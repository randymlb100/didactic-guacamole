package com.lotterynet.pro.core.printing

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.perf.PosPerformanceBudget
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

object BluetoothThermalPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val escPosInit = byteArrayOf(0x1B, 0x40)
    private val escPosAlignLeft = byteArrayOf(0x1B, 0x61, 0x00)
    private val escPosAlignCenter = byteArrayOf(0x1B, 0x61, 0x01)
    private val escPosBoldOn = byteArrayOf(0x1B, 0x45, 0x01)
    private val escPosBoldOff = byteArrayOf(0x1B, 0x45, 0x00)
    private val escPosNormalSize = byteArrayOf(0x1D, 0x21, 0x00)
    private val escPosTallSize = byteArrayOf(0x1D, 0x21, 0x10)
    private val escPosDoubleSize = byteArrayOf(0x1D, 0x21, 0x11)

    data class PrintResult(
        val success: Boolean,
        val message: String,
    )

    data class TimeoutPolicy(
        val connectTimeoutMs: Long,
        val writeTimeoutMs: Long,
        val retryBackoffMs: Long,
    )

    fun timeoutPolicy(): TimeoutPolicy {
        return TimeoutPolicy(
            connectTimeoutMs = PosPerformanceBudget.BLUETOOTH_CONNECT_TIMEOUT_MS,
            writeTimeoutMs = PosPerformanceBudget.BLUETOOTH_WRITE_TIMEOUT_MS,
            retryBackoffMs = 1_500L,
        )
    }

    internal fun timeoutPolicy(content: String): TimeoutPolicy {
        val lineCount = content.lineSequence().count().coerceAtLeast(1)
        val qrCount = content.lineSequence().count { it.startsWith("[[QR]]") }
        val dynamicWriteTimeout = (
            PosPerformanceBudget.BLUETOOTH_WRITE_TIMEOUT_MS +
                (lineCount * 60L) +
                (qrCount * 1_500L)
            ).coerceIn(PosPerformanceBudget.BLUETOOTH_WRITE_TIMEOUT_MS, 18_000L)
        return timeoutPolicy().copy(writeTimeoutMs = dynamicWriteTimeout)
    }

    fun printText(
        context: Context,
        content: String,
        prefs: ThermalPrinterPrefs,
    ): PrintResult {
        val printerAddress = prefs.selectedPrinterAddress.trim()
        if (printerAddress.isBlank()) {
            return PrintResult(false, "No hay impresora conectada")
        }
        val hasConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val hasScanPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        if (!hasConnectPermission) {
            return PrintResult(false, "Falta permiso Bluetooth para imprimir")
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return PrintResult(false, "Bluetooth no está disponible")
        if (!adapter.isEnabled) {
            return PrintResult(false, "Bluetooth está apagado")
        }
        val device = runCatching { adapter.getRemoteDevice(printerAddress) }.getOrNull()
            ?: return PrintResult(false, "No se encontró la impresora configurada")
        if (adapter.bondedDevices?.none { it.address == printerAddress } != false) {
            return PrintResult(false, "La impresora no está enlazada")
        }

        val activeSocket = AtomicReference<BluetoothSocket?>()
        return runPrintOperationWithTimeout(
            policy = timeoutPolicy(content),
            onTimeout = {
                activeSocket.getAndSet(null)?.let { socket ->
                    runCatching { socket.close() }
                }
            },
        ) {
            if (hasScanPermission) runCatching { adapter.cancelDiscovery() }
            connectAndWrite(device, content, activeSocket)
            PrintResult(true, "Enviado a impresora Bluetooth")
        }
    }

    internal fun resolvePrinterDrainDelayMs(content: String): Long {
        val lineCount = content.lineSequence().count().coerceAtLeast(1)
        val qrCount = content.lineSequence().count { it.startsWith("[[QR]]") }
        return (700L + (lineCount * 12L) + (qrCount * 800L)).coerceIn(900L, 2_500L)
    }

    fun testConnection(
        context: Context,
        prefs: ThermalPrinterPrefs,
        bancaName: String,
    ): PrintResult {
        return printText(
            context = context,
            content = buildConnectionTestText(bancaName, prefs),
            prefs = prefs,
        )
    }

    internal fun buildConnectionTestText(
        bancaName: String,
        prefs: ThermalPrinterPrefs,
    ): String {
        val width = if (prefs.paperWidth == "80") "80mm" else "58mm"
        return listOf(
            "[[TITLE|normal]]LOTTERYNET PRO",
            "[[CENTER|normal]]Prueba de impresora",
            "[[CENTER|normal]]$bancaName",
            "------------------------------",
            "[[BOLD|normal]]Conexion Bluetooth OK",
            "Papel: $width",
            "Hora: ${System.currentTimeMillis()}",
            "------------------------------",
            "[[FOOTER|normal]]Si lee esto, la impresora responde.",
        ).joinToString("\n")
    }

    internal fun encodeStyledLine(rawLine: String): ByteArray {
        return ByteArrayOutputStream().also { output ->
            writeStyledLine(output, rawLine)
        }.toByteArray()
    }

    internal fun runPrintOperationWithTimeout(
        policy: TimeoutPolicy,
        onTimeout: () -> Unit = {},
        operation: () -> PrintResult,
    ): PrintResult {
        val result = AtomicReference<PrintResult?>()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            runCatching(operation)
                .onSuccess { result.set(it) }
                .onFailure { failure.set(it) }
        }.apply {
            name = "bluetooth-thermal-print"
            isDaemon = true
        }
        worker.start()
        val timeoutMs = (policy.connectTimeoutMs + policy.writeTimeoutMs).coerceAtLeast(1L)
        worker.join(timeoutMs)
        if (worker.isAlive) {
            runCatching(onTimeout)
            worker.interrupt()
            return PrintResult(false, "La impresora no respondió a tiempo")
        }
        return result.get()
            ?: PrintResult(
                success = false,
                message = failure.get()?.let(::friendlyBluetoothFailure)?.takeIf { it.isNotBlank() }
                    ?: "No se pudo imprimir en Bluetooth",
            )
    }

    private fun connectAndWrite(
        device: BluetoothDevice,
        content: String,
        activeSocket: AtomicReference<BluetoothSocket?>,
    ) {
        var lastError: Throwable? = null
        bluetoothSocketCandidates(device).forEach { socketFactory ->
            val socket = runCatching(socketFactory).getOrElse { error ->
                lastError = error
                return@forEach
            }
            activeSocket.set(socket)
            try {
                socket.use { connectedSocket ->
                    connectedSocket.connect()
                    connectedSocket.outputStream.use { output ->
                        output.write(escPosInit)
                        content.lineSequence().forEach { rawLine ->
                            writeStyledLine(output, rawLine)
                        }
                        output.write("\n\n\n\n\n\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                        Thread.sleep(resolvePrinterDrainDelayMs(content))
                    }
                }
                activeSocket.compareAndSet(socket, null)
                return
            } catch (error: Throwable) {
                lastError = error
                activeSocket.compareAndSet(socket, null)
                runCatching { socket.close() }
                Thread.sleep(250L)
            }
        }
        throw lastError ?: IllegalStateException("No se pudo abrir la conexion Bluetooth")
    }

    private fun bluetoothSocketCandidates(device: BluetoothDevice): List<() -> BluetoothSocket> {
        return listOf(
            { device.createRfcommSocketToServiceRecord(sppUuid) },
            { device.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            {
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
    }

    private fun friendlyBluetoothFailure(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            "BLUETOOTH_SCAN" in message -> "Falta permiso Bluetooth para detectar la impresora"
            "BLUETOOTH_CONNECT" in message -> "Falta permiso Bluetooth para conectar la impresora"
            "read failed" in message.lowercase() -> "La impresora no aceptó la conexión Bluetooth"
            "socket" in message.lowercase() -> "No se pudo abrir la conexión Bluetooth"
            else -> message
        }
    }

    private fun writeStyledLine(
        output: java.io.OutputStream,
        rawLine: String,
    ) {
        if (rawLine.startsWith("[[QR]]")) {
            writeQr(output, rawLine.removePrefix("[[QR]]"))
            output.write(escPosAlignLeft)
            output.write(escPosBoldOff)
            output.write(escPosNormalSize)
            return
        }
        val styled = ThermalLineStyling.parse(rawLine)
        ThermalLineStyling.positionedMoneyRow(styled)?.let { row ->
            writePositionedMoneyRow(output, styled, row)
            return
        }
        when (styled.style) {
            ThermalLineStyle.TITLE -> {
                output.write(escPosAlignCenter)
                output.write(escPosBoldOn)
                output.write(sizeCommand(styled.scale))
            }
            ThermalLineStyle.CENTER -> {
                output.write(escPosAlignCenter)
                output.write(escPosBoldOff)
                output.write(escPosNormalSize)
            }
            ThermalLineStyle.LOTTERY -> {
                output.write(escPosAlignLeft)
                output.write(escPosBoldOn)
                output.write(sizeCommand(styled.scale))
            }
            ThermalLineStyle.BOLD -> {
                output.write(escPosAlignLeft)
                output.write(escPosBoldOn)
                output.write(sizeCommand(styled.scale))
            }
            ThermalLineStyle.PLAY_TYPE,
            ThermalLineStyle.PLAY_NUMBER,
            ThermalLineStyle.PLAY_AMOUNT,
            ThermalLineStyle.SECURITY -> {
                output.write(escPosAlignLeft)
                output.write(escPosBoldOn)
                output.write(sizeCommand(styled.scale))
            }
            ThermalLineStyle.QR -> {
                writeQr(output, styled.text)
                output.write(escPosAlignLeft)
                output.write(escPosBoldOff)
                output.write(escPosNormalSize)
                return
            }
            ThermalLineStyle.TOTAL -> {
                output.write(escPosAlignLeft)
                output.write(escPosBoldOn)
                output.write(sizeCommand(styled.scale))
            }
            ThermalLineStyle.FOOTER -> {
                output.write(escPosAlignCenter)
                output.write(escPosBoldOff)
                output.write(escPosNormalSize)
            }
            ThermalLineStyle.NORMAL -> {
                output.write(escPosAlignLeft)
                output.write(escPosBoldOff)
                output.write(escPosNormalSize)
            }
        }
        output.write(styled.text.toByteArray(Charsets.UTF_8))
        output.write("\n".toByteArray(Charsets.UTF_8))
        output.write(escPosAlignLeft)
        output.write(escPosBoldOff)
        output.write(escPosNormalSize)
    }

    private fun writePositionedMoneyRow(
        output: java.io.OutputStream,
        styled: ThermalStyledLine,
        row: ThermalLineStyling.PositionedMoneyRow,
    ) {
        output.write(escPosAlignLeft)
        output.write(escPosBoldOn)
        output.write(sizeCommand(styled.scale))
        output.write(row.label.toByteArray(Charsets.UTF_8))
        output.write(escPosAbsolutePosition(amountColumnDots(row)))
        output.write(sizeCommand(styled.scale))
        output.write(row.amount.toByteArray(Charsets.UTF_8))
        output.write("\n".toByteArray(Charsets.UTF_8))
        output.write(escPosAlignLeft)
        output.write(escPosBoldOff)
        output.write(escPosNormalSize)
    }

    private fun amountColumnDots(row: ThermalLineStyling.PositionedMoneyRow): Int {
        val fontADotWidth = 12
        val amountColumn = (row.width - row.amount.length).coerceAtLeast(row.label.length + 1)
        val maxDots = if (row.width >= 40) 560 else 360
        return (amountColumn * fontADotWidth).coerceIn(0, maxDots)
    }

    private fun escPosAbsolutePosition(dots: Int): ByteArray {
        return byteArrayOf(
            0x1B,
            0x24,
            (dots and 0xFF).toByte(),
            ((dots shr 8) and 0xFF).toByte(),
        )
    }

    private fun sizeCommand(scale: String): ByteArray {
        return when (scale) {
            "large" -> escPosDoubleSize
            "tall" -> escPosTallSize
            else -> escPosNormalSize
        }
    }

    private fun writeQr(
        output: java.io.OutputStream,
        payload: String,
    ) {
        val data = payload.toByteArray(Charsets.UTF_8)
        val storeLength = data.size + 3
        val pL = (storeLength and 0xFF).toByte()
        val pH = ((storeLength shr 8) and 0xFF).toByte()
        output.write(escPosAlignCenter)
        output.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        output.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05))
        output.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
        output.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        output.write(data)
        output.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        output.write("\n\n".toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
