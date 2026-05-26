package com.lotterynet.pro.core.printing

import com.lotterynet.pro.core.perf.PosPerformanceBudget
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothThermalPrinterPolicyTest {
    @Test
    fun `printer timeout policy is bounded for old pos devices`() {
        val policy = BluetoothThermalPrinter.timeoutPolicy()

        assertEquals(PosPerformanceBudget.BLUETOOTH_CONNECT_TIMEOUT_MS, policy.connectTimeoutMs)
        assertEquals(PosPerformanceBudget.BLUETOOTH_WRITE_TIMEOUT_MS, policy.writeTimeoutMs)
        assertTrue(policy.retryBackoffMs > 0L)
    }

    @Test
    fun `long ticket with qr receives larger bluetooth write window and drain delay`() {
        val content = (1..120).joinToString("\n") { "[[PLAY_NUMBER|tall]]Q ${it.toString().padStart(2, '0')}        100" } +
            "\n[[QR]]LN|NAT-LONG|SEC|12000|1777072400000"

        val policy = BluetoothThermalPrinter.timeoutPolicy(content)

        assertEquals(PosPerformanceBudget.BLUETOOTH_CONNECT_TIMEOUT_MS, policy.connectTimeoutMs)
        assertTrue(policy.writeTimeoutMs > PosPerformanceBudget.BLUETOOTH_WRITE_TIMEOUT_MS)
        assertTrue(policy.writeTimeoutMs <= 18_000L)
        assertTrue(BluetoothThermalPrinter.resolvePrinterDrainDelayMs(content) >= 1_200L)
        assertTrue(BluetoothThermalPrinter.resolvePrinterDrainDelayMs(content) <= 2_500L)
    }

    @Test
    fun `print operation returns recoverable timeout instead of waiting forever`() {
        var timeoutClosedSocket = false
        val result = BluetoothThermalPrinter.runPrintOperationWithTimeout(
            policy = BluetoothThermalPrinter.TimeoutPolicy(
                connectTimeoutMs = 10L,
                writeTimeoutMs = 10L,
                retryBackoffMs = 1L,
            ),
            onTimeout = { timeoutClosedSocket = true },
        ) {
            Thread.sleep(250L)
            BluetoothThermalPrinter.PrintResult(true, "late")
        }

        assertFalse(result.success)
        assertTrue(result.message.contains("tiempo", ignoreCase = true))
        assertTrue(timeoutClosedSocket)
    }

    @Test
    fun `print operation returns success before timeout`() {
        val result = BluetoothThermalPrinter.runPrintOperationWithTimeout(
            policy = BluetoothThermalPrinter.TimeoutPolicy(
                connectTimeoutMs = 200L,
                writeTimeoutMs = 200L,
                retryBackoffMs = 1L,
            ),
        ) {
            BluetoothThermalPrinter.PrintResult(true, "ok")
        }

        assertTrue(result.success)
        assertEquals("ok", result.message)
    }

    @Test
    fun `connection test text identifies bluetooth printer proof`() {
        val text = BluetoothThermalPrinter.buildConnectionTestText(
            bancaName = "Banca Norte",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )

        assertTrue(text.contains("Prueba de impresora"))
        assertTrue(text.contains("Conexion Bluetooth OK"))
        assertTrue(text.contains("Banca Norte"))
    }

    @Test
    fun `positioned money row prints amount with absolute cursor instead of padding spaces`() {
        val row = ThermalLineStyling.playMoneyRow(
            label = "P 14/15",
            amount = "3",
            width = 26,
            scale = "tall",
        )

        val bytes = BluetoothThermalPrinter.encodeStyledLine(row)
        val textPayload = bytes.toString(Charsets.UTF_8)

        assertTrue(bytes.hasSequence(byteArrayOf(0x1B, 0x24)))
        assertTrue(textPayload.contains("P 14/15"))
        assertTrue(textPayload.contains("3"))
        assertFalse(textPayload.contains("                 3"))
    }

    private fun ByteArray.hasSequence(sequence: ByteArray): Boolean {
        return asList().windowed(sequence.size).any { it == sequence.asList() }
    }
}
