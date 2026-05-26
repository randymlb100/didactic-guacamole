package com.lotterynet.pro.core.printing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object IntegratedThermalPrinter {
    private const val SUNMI_SERVICE_ACTION = "woyou.aidlservice.jiuiv5.IWoyouService"
    private const val SUNMI_SERVICE_PACKAGE = "woyou.aidlservice.jiuiv5"
    private const val CONNECT_TIMEOUT_MS = 2_000L

    fun isAvailable(context: Context): Boolean {
        val intent = sunmiIntent()
        return context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
    }

    fun printText(context: Context, content: String): BluetoothThermalPrinter.PrintResult {
        val intent = sunmiIntent()
        val latch = CountDownLatch(1)
        var serviceBinder: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceBinder = service
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
            }
        }
        val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) return BluetoothThermalPrinter.PrintResult(false, "Impresora interna no disponible")
        return try {
            if (!latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                BluetoothThermalPrinter.PrintResult(false, "Impresora interna no respondió")
            } else {
                val binder = serviceBinder ?: return BluetoothThermalPrinter.PrintResult(false, "Impresora interna no conectó")
                val service = Class.forName("$SUNMI_SERVICE_PACKAGE.IWoyouService\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder) ?: return BluetoothThermalPrinter.PrintResult(false, "Impresora interna no conectó")
                service.javaClass.getMethod("printerInit", callbackClass()).invoke(service, null)
                content.lineSequence().forEach { rawLine ->
                    if (rawLine.startsWith("[[QR]]")) {
                        val payload = rawLine.removePrefix("[[QR]]").ifBlank { "LN" }
                        val printedQr = runCatching {
                            service.javaClass
                                .getMethod("printQRCode", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, callbackClass())
                                .invoke(service, payload, 6, 2, null)
                        }.isSuccess
                        if (!printedQr) {
                            service.javaClass.getMethod("printTextWithFont", String::class.java, String::class.java, Float::class.javaPrimitiveType, callbackClass())
                                .invoke(service, "QR: $payload\n", "", 22f, null)
                        }
                    } else {
                        val styled = ThermalLineStyling.parse(rawLine)
                        val fontSize = when (styled.style) {
                            ThermalLineStyle.TITLE,
                            ThermalLineStyle.LOTTERY,
                            ThermalLineStyle.TOTAL -> 30f
                            ThermalLineStyle.PLAY_NUMBER -> 27f
                            else -> 23f
                        }
                        val positionedRow = ThermalLineStyling.positionedMoneyRow(styled)
                        if (positionedRow != null && printPositionedMoneyRow(service, positionedRow, fontSize)) {
                            return@forEach
                        }
                        printTextWithFont(service, "${styled.text}\n", fontSize)
                    }
                }
                runCatching {
                    service.javaClass.getMethod("lineWrap", Int::class.javaPrimitiveType, callbackClass()).invoke(service, 5, null)
                }
                BluetoothThermalPrinter.PrintResult(true, "Enviado a impresora interna")
            }
        } catch (error: Throwable) {
            BluetoothThermalPrinter.PrintResult(false, "No se pudo imprimir en impresora interna")
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private fun callbackClass(): Class<*> {
        return Class.forName("$SUNMI_SERVICE_PACKAGE.ICallback")
    }

    private fun printPositionedMoneyRow(
        service: Any,
        row: ThermalLineStyling.PositionedMoneyRow,
        fontSize: Float,
    ): Boolean {
        return runCatching {
            runCatching {
                service.javaClass
                    .getMethod("setFontSize", Float::class.javaPrimitiveType, callbackClass())
                    .invoke(service, fontSize, null)
            }
            val labelColumns = (row.width - row.amount.length - 1).coerceAtLeast(1)
            service.javaClass
                .getMethod(
                    "printColumnsString",
                    Array<String>::class.java,
                    IntArray::class.java,
                    IntArray::class.java,
                    callbackClass(),
                )
                .invoke(
                    service,
                    arrayOf(row.label, row.amount),
                    intArrayOf(labelColumns, row.amount.length + 1),
                    intArrayOf(0, 2),
                    null,
                )
            true
        }.getOrDefault(false)
    }

    private fun printTextWithFont(
        service: Any,
        text: String,
        fontSize: Float,
    ) {
        service.javaClass.getMethod("printTextWithFont", String::class.java, String::class.java, Float::class.javaPrimitiveType, callbackClass())
            .invoke(service, text, "", fontSize, null)
    }

    private fun sunmiIntent(): Intent {
        return Intent(SUNMI_SERVICE_ACTION).setPackage(SUNMI_SERVICE_PACKAGE)
    }
}
