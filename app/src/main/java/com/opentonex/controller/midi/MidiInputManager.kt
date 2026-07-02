package com.opentonex.controller.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado da conexao MIDI exibido na aba MIDI. */
sealed interface MidiConnectionState {
    data object Idle : MidiConnectionState
    data object Scanning : MidiConnectionState
    data class Connecting(val deviceName: String) : MidiConnectionState
    data class Connected(val deviceName: String) : MidiConnectionState
    data class Error(val message: String) : MidiConnectionState
}

/** Dispositivo listado na UI: USB MIDI ja plugado ou BLE encontrado no scan. */
data class MidiDeviceUi(
    val id: String,
    val name: String,
    val isBluetooth: Boolean,
    val usbInfo: MidiDeviceInfo? = null,
    val bleDevice: BluetoothDevice? = null
)

/**
 * Unica camada que toca as APIs Android de MIDI/BLE. Lista dispositivos USB MIDI,
 * escaneia BLE MIDI (service UUID padrao) e conecta um dispositivo por vez, entregando
 * as mensagens parseadas em [onMessages] SEMPRE na main thread.
 *
 * Sem logica de dominio aqui: parse fica no MidiParser, mapeamento no dispatcher.
 * As permissoes de Bluetooth devem ser pedidas pela UI ANTES de chamar startBleScan.
 */
class MidiInputManager(
    private val context: Context,
    private val onMessages: (List<MidiMessage>) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val midiManager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else null

    private val parser = MidiParser()
    private var openDevice: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null
    private var scanCallback: ScanCallback? = null

    private val _devices = MutableStateFlow<List<MidiDeviceUi>>(emptyList())
    val devices: StateFlow<List<MidiDeviceUi>> = _devices.asStateFlow()

    private val _state = MutableStateFlow<MidiConnectionState>(MidiConnectionState.Idle)
    val state: StateFlow<MidiConnectionState> = _state.asStateFlow()

    val isMidiSupported: Boolean get() = midiManager != null

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val messages = parser.parse(msg, offset, count)
            if (messages.isNotEmpty()) mainHandler.post { onMessages(messages) }
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = refreshUsbDevices()
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            refreshUsbDevices()
            val current = openDevice?.info
            if (current != null && current.id == device.id) handleDisconnected()
        }
    }

    init {
        @Suppress("DEPRECATION")
        midiManager?.registerDeviceCallback(deviceCallback, mainHandler)
        refreshUsbDevices()
    }

    /** Recarrega a lista de dispositivos USB MIDI (BLE encontrados no scan sao mantidos). */
    fun refreshUsbDevices() {
        val manager = midiManager ?: return
        @Suppress("DEPRECATION")
        val usb = manager.devices
            .filter { it.outputPortCount > 0 && it.type != MidiDeviceInfo.TYPE_BLUETOOTH }
            .map { info ->
                MidiDeviceUi(
                    id = "usb-${info.id}",
                    name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                        ?: "USB MIDI ${info.id}",
                    isBluetooth = false,
                    usbInfo = info
                )
            }
        val ble = _devices.value.filter { it.isBluetooth }
        _devices.value = usb + ble
    }

    /** Requer BLUETOOTH_SCAN (S+) ou ACCESS_FINE_LOCATION (<=R) ja concedidas pela UI. */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (midiManager == null) return
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = MidiConnectionState.Error("Bluetooth indisponível")
            return
        }
        stopBleScan()
        _state.value = MidiConnectionState.Scanning
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: device.name ?: return
                val ui = MidiDeviceUi(
                    id = "ble-${device.address}",
                    name = name,
                    isBluetooth = true,
                    bleDevice = device
                )
                if (_devices.value.none { it.id == ui.id }) {
                    _devices.value = _devices.value + ui
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = MidiConnectionState.Error("Scan BLE falhou ($errorCode)")
            }
        }
        scanCallback = callback
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MIDI_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        mainHandler.postDelayed({ stopBleScan() }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        runCatching { bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback) }
        if (_state.value is MidiConnectionState.Scanning) {
            _state.value = MidiConnectionState.Idle
        }
    }

    fun connect(device: MidiDeviceUi) {
        val manager = midiManager ?: return
        stopBleScan()
        disconnect()
        _state.value = MidiConnectionState.Connecting(device.name)
        val listener = MidiManager.OnDeviceOpenedListener { opened ->
            if (opened == null) {
                _state.value = MidiConnectionState.Error("Falha ao abrir ${device.name}")
                return@OnDeviceOpenedListener
            }
            val port = opened.openOutputPort(0)
            if (port == null) {
                runCatching { opened.close() }
                _state.value = MidiConnectionState.Error("Sem porta MIDI em ${device.name}")
                return@OnDeviceOpenedListener
            }
            port.connect(receiver)
            openDevice = opened
            outputPort = port
            _state.value = MidiConnectionState.Connected(device.name)
        }
        when {
            device.usbInfo != null -> manager.openDevice(device.usbInfo, listener, mainHandler)
            device.bleDevice != null ->
                manager.openBluetoothDevice(device.bleDevice, listener, mainHandler)
            else -> _state.value = MidiConnectionState.Idle
        }
    }

    fun disconnect() {
        runCatching { outputPort?.disconnect(receiver) }
        runCatching { outputPort?.close() }
        runCatching { openDevice?.close() }
        outputPort = null
        openDevice = null
        if (_state.value !is MidiConnectionState.Scanning) {
            _state.value = MidiConnectionState.Idle
        }
    }

    private fun handleDisconnected() {
        disconnect()
        _state.value = MidiConnectionState.Error("Dispositivo MIDI desconectado")
    }

    fun release() {
        stopBleScan()
        disconnect()
        midiManager?.unregisterDeviceCallback(deviceCallback)
    }

    private companion object {
        /** Service UUID padrao do BLE MIDI (spec MMA). */
        val MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}
