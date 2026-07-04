package com.opentonex.controller.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
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

    /** SN real do pedal (descritor USB, string index 0x12) — o mesmo que o app oficial exibe. */
    override val deviceSerialNumber: String?
        get() = runCatching { driver.device.serialNumber }.getOrNull()
    // Endpoint MIDI OUT (USB Audio class, subclass MIDISTREAMING). Null se o device
    // nao expoe interface MIDI — nesse caso writeDirect lanca IOException.
    private val midiInterface: UsbInterface?
    private val midiOutEndpoint: UsbEndpoint?

    init {
        val result = findMidiOutEndpoint(driver.device)
        midiInterface = result?.first
        midiOutEndpoint = result?.second
    }

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
        // Reclama a interface MIDI na mesma UsbDeviceConnection (endpoints separados, mesmo device).
        midiInterface?.let { connection.claimInterface(it, true) }
        // Settle + flush espelham a ferramenta PC conhecida-boa (sleep apos abrir) antes do 1o Hello.
        delay(PORT_SETTLE_MS)
        runCatching { serialPort.purgeHwBuffers(true, true) }
        leftover.clear()
        usbConnection = connection
        port = serialPort
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        requirePort().write(bytes, IO_TIMEOUT_MS)
    }

    // Bytes lidos do USB alem do frame retornado (ex.: rajada de 0x0309 quando o knob
    // fisico gira chega em um unico chunk com VARIOS frames). Ficam guardados para a
    // proxima chamada de readFrame em vez de serem descartados.
    private val leftover = ArrayList<Byte>()

    // port.read() e bloqueante (I/O USB nativa) - roda em Dispatchers.IO para nao
    // congelar a main thread / disparar ANR quando o pedal demora ou nao responde.
    override suspend fun readFrame(timeoutMs: Long): ByteArray = withContext(Dispatchers.IO) {
        val activePort = requirePort()
        val deadline = System.currentTimeMillis() + timeoutMs
        val frame = ArrayList<Byte>()
        var sawStartFlag = false

        // Consome um byte; retorna o frame completo quando a flag de fechamento chega.
        fun consume(b: Byte): ByteArray? {
            val isFlag = (b.toInt() and 0xFF) == FLAG
            if (isFlag) {
                if (sawStartFlag && frame.size > 1) {
                    // flag de fechamento de um frame com payload real
                    frame.add(b)
                    return frame.toByteArray()
                }
                // flag de abertura, ou flag de preenchimento ocioso (0x7E repetido
                // entre frames, sem dados no meio) - reinicia a captura aqui
                frame.clear()
                frame.add(b)
                sawStartFlag = true
            } else if (sawStartFlag) {
                frame.add(b)
            }
            return null
        }

        // 1. Sobras da leitura anterior (frames que chegaram no mesmo chunk USB).
        while (leftover.isNotEmpty()) {
            val b = leftover.removeAt(0)
            consume(b)?.let { return@withContext it }
        }

        // 2. Leitura nova do USB.
        val buffer = ByteArray(READ_CHUNK_SIZE)
        while (System.currentTimeMillis() < deadline) {
            val read = activePort.read(buffer, IO_TIMEOUT_MS)
            for (i in 0 until read) {
                consume(buffer[i])?.let { result ->
                    // Guarda o resto do chunk para a proxima chamada.
                    for (j in i + 1 until read) leftover.add(buffer[j])
                    return@withContext result
                }
            }
        }
        throw PedalTransportTimeoutException("sem resposta do pedal em ${timeoutMs}ms")
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        closeBlocking()
    }

    override suspend fun writeDirect(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val ep = midiOutEndpoint ?: throw IOException("endpoint MIDI OUT nao encontrado no pedal")
        val conn = usbConnection ?: throw IOException("conexao USB nao esta aberta")
        conn.bulkTransfer(ep, bytes, bytes.size, IO_TIMEOUT_MS)
        Unit
    }

    private fun closeBlocking() {
        midiInterface?.let { runCatching { usbConnection?.releaseInterface(it) } }
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

        /** Localiza a interface MIDI Streaming (Audio class 0x01, subclass 0x03) e seu endpoint bulk OUT. */
        private fun findMidiOutEndpoint(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
            android.util.Log.d("ToneXUsb", "device interfaces: ${device.interfaceCount}")
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                android.util.Log.d("ToneXUsb", "  iface[$i] class=${iface.interfaceClass} sub=${iface.interfaceSubclass} endpoints=${iface.endpointCount}")
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    android.util.Log.d("ToneXUsb", "    ep[$j] addr=0x${ep.address.toString(16)} type=${ep.type} dir=${ep.direction}")
                }
                if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == 3) {
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                            return iface to ep
                        }
                    }
                }
            }
            return null
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
