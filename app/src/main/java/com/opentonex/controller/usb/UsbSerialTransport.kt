package com.opentonex.controller.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.opentonex.controller.connection.PedalTransport
import com.opentonex.controller.connection.PedalTransportTimeoutException
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val ACTION_USB_PERMISSION = "com.opentonex.controller.USB_PERMISSION"
private const val TONEX_VENDOR_ID = 0x1963
private const val TONEX_PRODUCT_ID = 0x00D1
private const val BAUD_RATE = 115200
private const val IO_TIMEOUT_MS = 500
private const val READ_CHUNK_SIZE = 4096
private const val FLAG = 0x7E
private const val PORT_SETTLE_MS = 200L

/**
 * [PedalTransport] real sobre o ToneX One (CDC ACM). A permissao/device sao resolvidos uma vez
 * em [connect]; a porta serial em si e aberta/fechada por [open]/[close] e pode ser REABERTA
 * varias vezes. Isso e essencial: numa porta CDC recem-aberta o pedal costuma ignorar o Hello
 * (~10% de chance de responder por abertura); a recuperacao real e reabrir a porta - foi o que
 * as reconexoes manuais faziam. Ver docs/protocol-notes.md (Bug 3 - conexao lenta).
 */
class UsbSerialTransport private constructor(
    private val manager: UsbManager,
    private val driver: UsbSerialDriver
) : PedalTransport {

    private var port: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null

    /** (Re)abre a porta serial do zero: nova UsbDeviceConnection + port.open + DTR/RTS + settle. */
    override suspend fun open() = withContext(Dispatchers.IO) {
        closeBlocking()
        val connection = manager.openDevice(driver.device)
            ?: throw IOException("falha ao abrir a conexao USB do pedal")
        val serialPort = driver.ports.firstOrNull()
            ?: throw IOException("driver USB do pedal sem porta serial")
        serialPort.open(connection)
        serialPort.setParameters(BAUD_RATE, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        // Muitos dispositivos CDC ACM ficam mudos até o host afirmar DTR/RTS.
        serialPort.dtr = true
        serialPort.rts = true
        // Settle + flush espelham a ferramenta PC conhecida-boa (sleep apos abrir) antes do 1o Hello.
        delay(PORT_SETTLE_MS)
        runCatching { serialPort.purgeHwBuffers(true, true) }
        usbConnection = connection
        port = serialPort
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        requirePort().write(bytes, IO_TIMEOUT_MS)
    }

    // port.read() e bloqueante (I/O USB nativa) - roda em Dispatchers.IO para nao
    // congelar a main thread / disparar ANR quando o pedal demora ou nao responde.
    override suspend fun readFrame(timeoutMs: Long): ByteArray = withContext(Dispatchers.IO) {
        val activePort = requirePort()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(READ_CHUNK_SIZE)
        val frame = ArrayList<Byte>()
        var sawStartFlag = false
        while (System.currentTimeMillis() < deadline) {
            val read = activePort.read(buffer, IO_TIMEOUT_MS)
            for (i in 0 until read) {
                val b = buffer[i]
                val isFlag = (b.toInt() and 0xFF) == FLAG
                if (isFlag) {
                    if (sawStartFlag && frame.size > 1) {
                        // flag de fechamento de um frame com payload real
                        frame.add(b)
                        return@withContext frame.toByteArray()
                    }
                    // flag de abertura, ou flag de preenchimento ocioso (0x7E repetido
                    // entre frames, sem dados no meio) - reinicia a captura aqui
                    frame.clear()
                    frame.add(b)
                    sawStartFlag = true
                } else if (sawStartFlag) {
                    frame.add(b)
                }
            }
        }
        throw PedalTransportTimeoutException("sem resposta do pedal em ${timeoutMs}ms")
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        closeBlocking()
    }

    private fun closeBlocking() {
        runCatching { port?.close() }
        runCatching { usbConnection?.close() }
        port = null
        usbConnection = null
    }

    private fun requirePort(): UsbSerialPort =
        port ?: throw IOException("porta serial do pedal nao esta aberta")

    companion object {
        /**
         * Localiza o ToneX One e pede permissao se preciso. A porta serial NAO e aberta aqui -
         * isso fica para [open], que pode ser chamado repetidamente (reabertura no handshake).
         * Retorna null se o pedal nao for encontrado ou a permissao for negada.
         */
        suspend fun connect(context: Context, manager: UsbManager): UsbSerialTransport? {
            val device = manager.deviceList.values.firstOrNull {
                it.vendorId == TONEX_VENDOR_ID && it.productId == TONEX_PRODUCT_ID
            } ?: return null

            if (!manager.hasPermission(device) && !requestPermission(context, manager, device)) {
                return null
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return null
            return UsbSerialTransport(manager, driver)
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
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                val permissionIntent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    permissionIntent,
                    PendingIntent.FLAG_MUTABLE
                )
                manager.requestPermission(device, pendingIntent)
            }
    }
}
