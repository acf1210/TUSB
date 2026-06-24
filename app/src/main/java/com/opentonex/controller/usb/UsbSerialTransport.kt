package com.opentonex.controller.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.opentonex.controller.connection.PedalTransport
import com.opentonex.controller.connection.PedalTransportTimeoutException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val ACTION_USB_PERMISSION = "com.opentonex.controller.USB_PERMISSION"
private const val TONEX_VENDOR_ID = 0x1963
private const val TONEX_PRODUCT_ID = 0x00D1
private const val BAUD_RATE = 115200
private const val IO_TIMEOUT_MS = 500
private const val READ_CHUNK_SIZE = 4096
private const val FLAG = 0x7E

/** [PedalTransport] real sobre um [UsbSerialPort] ja aberto (ver [connect]). */
class UsbSerialTransport(private val port: UsbSerialPort) : PedalTransport {

    override suspend fun open() {
        // Conexao/permissao/abertura ja feitas em connect(); nada a fazer aqui.
    }

    override suspend fun write(bytes: ByteArray) {
        port.write(bytes, IO_TIMEOUT_MS)
    }

    override suspend fun readFrame(timeoutMs: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(READ_CHUNK_SIZE)
        val frame = ArrayList<Byte>()
        var sawStartFlag = false
        while (System.currentTimeMillis() < deadline) {
            val read = port.read(buffer, IO_TIMEOUT_MS)
            for (i in 0 until read) {
                val b = buffer[i]
                val isFlag = (b.toInt() and 0xFF) == FLAG
                if (isFlag && !sawStartFlag) {
                    sawStartFlag = true
                    frame.add(b)
                } else if (isFlag && sawStartFlag) {
                    frame.add(b)
                    return frame.toByteArray()
                } else if (sawStartFlag) {
                    frame.add(b)
                }
            }
        }
        throw PedalTransportTimeoutException("sem resposta do pedal em ${timeoutMs}ms")
    }

    override suspend fun close() {
        port.close()
    }

    companion object {
        /** Localiza o ToneX One, pede permissao se preciso, abre a porta serial. Null se nao encontrado. */
        suspend fun connect(context: Context, manager: UsbManager): UsbSerialTransport? {
            val device = manager.deviceList.values.firstOrNull {
                it.vendorId == TONEX_VENDOR_ID && it.productId == TONEX_PRODUCT_ID
            } ?: return null

            if (!manager.hasPermission(device) && !requestPermission(context, manager, device)) {
                return null
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return null
            val connection = manager.openDevice(driver.device) ?: return null
            val port = driver.ports.firstOrNull() ?: return null
            port.open(connection)
            port.setParameters(BAUD_RATE, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            return UsbSerialTransport(port)
        }

        private suspend fun requestPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean =
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, intent: Intent) {
                        receiverContext.unregisterReceiver(this)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(granted)
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(ACTION_USB_PERMISSION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE
                )
                manager.requestPermission(device, pendingIntent)
            }
    }
}
