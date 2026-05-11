package com.example.appfightsmart

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

class BluetoothManager(
    private val context: Context,
    private var onConnectionStateChange: (Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "WitBLE"
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val DIRECT_CONNECT_DELAY_MS = 500L
        private const val POWER_REGISTER = 0x64

        private val UUID_SERVICE: UUID = UUID.fromString("0000ffe5-0000-1000-8000-00805f9a34fb")
        private val UUID_READ: UUID    = UUID.fromString("0000ffe4-0000-1000-8000-00805f9a34fb")
        private val UUID_WRITE: UUID   = UUID.fromString("0000ffe9-0000-1000-8000-00805f9a34fb")
        private val UUID_SERVICE_OLD: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9a34fb")
        private val UUID_NOTIFY_OLD:  UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9a34fb")
        private val UUID_WRITE_OLD:   UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9a34fb")
        private val UUID_CCCD: UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val UUID_BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val UUID_BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun sendWitCommand(cmd: ByteArray) {
        val ch = writeChar
            ?: notifyChar?.service?.getCharacteristic(UUID_WRITE)
            ?: gatt?.getService(UUID_SERVICE)?.getCharacteristic(UUID_WRITE)
            ?: gatt?.getService(UUID_SERVICE_OLD)?.getCharacteristic(UUID_WRITE_OLD)

        if (ch == null) { Log.e(TAG, "WRITE characteristic not found"); return }
        try {
            if (!hasConnectPerm()) { Log.e(TAG, "No BLUETOOTH_CONNECT"); return }
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = cmd
            val ok = gatt?.writeCharacteristic(ch) ?: false
            Log.d(TAG, "write ${cmd.joinToString(" ") { String.format("%02X", it) }} -> $ok")
            Log.d("WITCMD", "FF AA write -> ${cmd.joinToString(" ") { String.format("%02X", it) }} (ok=$ok)")
        } catch (e: Exception) {
            Log.e(TAG, "write err: ${e.message}")
        }
    }

    fun enableAnglesAndMagAt100Hz() {
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x69, 0x88.toByte(), 0xB5.toByte()))
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x03, 0x09, 0x00))
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x02, 0x3E, 0x00))
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x00, 0x00, 0x00))
    }

    fun requestWitPower() {
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x27, POWER_REGISTER.toByte(), 0x00))
    }

    fun setOnConnectionStateChange(cb: (Boolean) -> Unit) {
        onConnectionStateChange = cb
    }

    private val dataListeners = Collections.synchronizedList(mutableListOf<(ByteArray) -> Unit>())
    fun addDataListener(listener: (ByteArray) -> Unit) { dataListeners.add(listener) }
    fun removeDataListener(listener: (ByteArray) -> Unit) { dataListeners.remove(listener) }

    private val connListeners = Collections.synchronizedList(mutableListOf<(Boolean) -> Unit>())
    fun addConnectionListener(l: (Boolean) -> Unit) { connListeners.add(l) }
    fun removeConnectionListener(l: (Boolean) -> Unit) { connListeners.remove(l) }

    private val batteryListeners = Collections.synchronizedList(mutableListOf<(Int?) -> Unit>())
    fun addBatteryListener(l: (Int?) -> Unit) { batteryListeners.add(l) }
    fun removeBatteryListener(l: (Int?) -> Unit) { batteryListeners.remove(l) }

    private val rssiListeners = Collections.synchronizedList(mutableListOf<(Int?) -> Unit>())
    fun addRssiListener(l: (Int?) -> Unit) { rssiListeners.add(l) }
    fun removeRssiListener(l: (Int?) -> Unit) { rssiListeners.remove(l) }

    private val sysBtMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    private val btAdapter: BluetoothAdapter? = sysBtMgr?.adapter
    private val btScanner get() = btAdapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private var rssiJob: Job? = null
    private var powerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile private var configPushed = false
    @Volatile private var pendingDeviceAddress: String? = null
    @Volatile private var lastConnectUsedScan = false

    private val frameBuffer = ArrayList<Byte>(256)
    private fun emitFramesFromBuffer(emit: (ByteArray) -> Unit) {
        var i = 0
        while (frameBuffer.size - i >= 11) {
            while (i < frameBuffer.size && frameBuffer[i] != 0x55.toByte()) i++
            if (frameBuffer.size - i < 11) break
            val second = frameBuffer[i + 1]

            if (second == 0x71.toByte()) {
                if (frameBuffer.size - i < 20) break
                val packet = ByteArray(20) { idx -> frameBuffer[i + idx] }
                handleWitRegisterPacket(packet)
                emit(packet)
                i += 20
                continue
            }

            val known = second == 0x61.toByte() || second == 0x62.toByte() ||
                    second == 0x63.toByte() || second == 0x64.toByte() ||
                    second == 0x51.toByte() || second == 0x52.toByte() ||
                    second == 0x53.toByte() || second == 0x54.toByte()
            if (!known) { i++; continue }
            val frame = ByteArray(11) { idx -> frameBuffer[i + idx] }
            emit(frame)
            i += 11
        }
        if (i > 0) repeat(i) { frameBuffer.removeAt(0) }
    }

    private fun handleWitRegisterPacket(bytes: ByteArray): Boolean {
        if (bytes.size < 20 || bytes[0] != 0x55.toByte() || bytes[1] != 0x71.toByte()) return false
        val startRegister = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        if (startRegister == POWER_REGISTER) {
            val registerValue = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)
            val percent = voltageRegisterToBatteryPercent(registerValue)
            Log.d(TAG, "WIT power register=$registerValue voltage=${registerValue / 100.0}V -> battery=$percent%")
            batteryListeners.forEach { it(percent) }
            return true
        }
        return false
    }

    private fun voltageRegisterToBatteryPercent(value: Int): Int {
        // WIT returns an approximate Li-ion voltage register, usually voltage * 100.
        // The original WIT documentation table is very coarse and reports anything above 3.96 V as 100%,
        // but the official WIT app uses a smoother curve. This curve is calibrated for the replaced 3.7 V
        // Li-ion battery too, because capacity changed but voltage range remains the same.
        val curve = listOf(
            420 to 100,
            415 to 95,
            411 to 90,
            403 to 85,
            397 to 80,
            392 to 75,
            387 to 70,
            383 to 60,
            379 to 50,
            377 to 40,
            373 to 30,
            370 to 20,
            368 to 15,
            350 to 10,
            340 to 5,
            330 to 0
        )

        if (value >= curve.first().first) return curve.first().second
        for (i in 0 until curve.lastIndex) {
            val highVoltage = curve[i].first
            val highPercent = curve[i].second
            val lowVoltage = curve[i + 1].first
            val lowPercent = curve[i + 1].second
            if (value in lowVoltage..highVoltage) {
                val ratio = (value - lowVoltage).toFloat() / (highVoltage - lowVoltage).toFloat()
                return (lowPercent + ratio * (highPercent - lowPercent)).toInt().coerceIn(0, 100)
            }
        }
        return 0
    }

    fun isBluetoothEnabled(): Boolean = btAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        try {
            Log.d(TAG, "Connecting to $deviceAddress")
            if (btAdapter?.isEnabled != true) { Log.e(TAG, "Bluetooth is disabled"); return }
            if (!BluetoothAdapter.checkBluetoothAddress(deviceAddress)) { Log.e(TAG, "Invalid address $deviceAddress"); return }
            if (!hasConnectPerm()) { Log.e(TAG, "No BLUETOOTH_CONNECT"); return }

            stopScan()
            closeGatt()
            pendingDeviceAddress = deviceAddress
            configPushed = false
            frameBuffer.clear()
            btAdapter.cancelDiscovery()

            if (hasScanPerm() && btScanner != null) {
                startWarmupScanThenConnect(deviceAddress)
            } else {
                Log.w(TAG, "No scan permission/scanner; using direct BLE connect")
                directConnect(deviceAddress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "connect error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWarmupScanThenConnect(deviceAddress: String) {
        if (!hasScanPerm()) { directConnect(deviceAddress); return }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val foundAddress = result.device?.address ?: return
                if (foundAddress.equals(deviceAddress, ignoreCase = true)) {
                    Log.d(TAG, "Found WIT sensor in scan: $foundAddress rssi=${result.rssi}")
                    rssiListeners.forEach { it(result.rssi) }
                    stopScan()
                    connectToScannedDevice(result.device)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode; falling back to direct connect")
                stopScan()
                directConnect(deviceAddress)
            }
        }

        scanCallback = callback
        try {
            Log.d(TAG, "Starting BLE warmup scan for $deviceAddress")
            btScanner?.startScan(null, settings, callback)
            scanTimeoutJob = scope.launch {
                delay(SCAN_TIMEOUT_MS)
                if (pendingDeviceAddress == deviceAddress && gatt == null) {
                    Log.w(TAG, "BLE warmup scan timed out; falling back to direct connect")
                    stopScan()
                    delay(DIRECT_CONNECT_DELAY_MS)
                    directConnect(deviceAddress)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startScan error: ${e.message}; falling back to direct connect")
            stopScan()
            directConnect(deviceAddress)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToScannedDevice(device: BluetoothDevice) {
        if (!hasConnectPerm()) return
        lastConnectUsedScan = true
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun directConnect(deviceAddress: String) {
        if (!hasConnectPerm()) return
        val device = btAdapter?.getRemoteDevice(deviceAddress) ?: return
        lastConnectUsedScan = false
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback ?: return
        try {
            if (hasScanPerm()) btScanner?.stopScan(callback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan warning: ${e.message}")
        } finally {
            scanCallback = null
            scanTimeoutJob?.cancel()
            scanTimeoutJob = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "closeGatt warning: ${e.message}")
        } finally {
            rssiJob?.cancel()
            rssiJob = null
            powerJob?.cancel()
            powerJob = null
            gatt = null
            notifyChar = null
            writeChar = null
            batteryListeners.forEach { it(null) }
            rssiListeners.forEach { it(null) }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        if (!hasConnectPerm()) return
        stopScan()
        closeGatt()
        pendingDeviceAddress = null
        frameBuffer.clear()
        configPushed = false
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        ch.value = bytes
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return try { g.writeCharacteristic(ch) } catch (_: Exception) { false }
    }

    private fun hasConnectPerm(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPerm(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun propsToString(props: Int): String {
        val p = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) p += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) p += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) p += "WRITE_NR"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) p += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) p += "INDICATE"
        return p.joinToString("|")
    }

    @SuppressLint("MissingPermission")
    private fun dumpGatt(g: BluetoothGatt) {
        for (svc in g.services) {
            Log.i(TAG, "Svc ${svc.uuid}")
            for (ch in svc.characteristics) {
                Log.i(TAG, "  Char ${ch.uuid} props=${propsToString(ch.properties)}")
                for (d in ch.descriptors) {
                    Log.i(TAG, "    Desc ${d.uuid}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBatteryIfAvailable(g: BluetoothGatt) {
        if (!hasConnectPerm()) return
        val batteryChar = g.getService(UUID_BATTERY_SERVICE)?.getCharacteristic(UUID_BATTERY_LEVEL)
        if (batteryChar == null) {
            Log.w(TAG, "Standard Battery service not found; using WIT power register")
            return
        }
        try {
            val ok = g.readCharacteristic(batteryChar)
            Log.d(TAG, "read standard battery -> $ok")
        } catch (e: Exception) {
            Log.w(TAG, "read battery warning: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRssiUpdates(g: BluetoothGatt) {
        rssiJob?.cancel()
        rssiJob = scope.launch {
            while (isActive) {
                try {
                    if (hasConnectPerm()) g.readRemoteRssi()
                } catch (e: Exception) {
                    Log.w(TAG, "read RSSI warning: ${e.message}")
                }
                delay(5_000L)
            }
        }
    }

    private fun startWitPowerUpdates() {
        powerJob?.cancel()
        powerJob = scope.launch {
            delay(1_000L)
            while (isActive) {
                requestWitPower()
                delay(30_000L)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "state=$newState status=$status usedScan=$lastConnectUsedScan")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                pendingDeviceAddress = null
                scope.launch {
                    withContext(Dispatchers.Main) {
                        onConnectionStateChange(true)
                        connListeners.forEach { it(true) }
                    }
                }
                if (hasConnectPerm()) {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    g.requestMtu(185)
                    startRssiUpdates(g)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                rssiJob?.cancel()
                rssiJob = null
                powerJob?.cancel()
                powerJob = null
                scope.launch {
                    withContext(Dispatchers.Main) {
                        onConnectionStateChange(false)
                        connListeners.forEach { it(false) }
                        batteryListeners.forEach { it(null) }
                        rssiListeners.forEach { it(null) }
                    }
                }
                notifyChar = null
                writeChar = null
                frameBuffer.clear()
                configPushed = false
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU=$mtu status=$status")
            if (hasConnectPerm()) g.discoverServices() else Log.e(TAG, "No BLUETOOTH_CONNECT")
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: $status")
                return
            }
            Log.i(TAG, "Services discovered")
            dumpGatt(g)
            readBatteryIfAvailable(g)

            val newSvc = g.getService(UUID_SERVICE)
            val preferredNotify = newSvc?.getCharacteristic(UUID_READ)
                ?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
            val preferredWrite  = newSvc?.getCharacteristic(UUID_WRITE)

            val oldSvc = g.getService(UUID_SERVICE_OLD)
            val legacyNotify = oldSvc?.getCharacteristic(UUID_NOTIFY_OLD)
                ?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
            val legacyWrite = oldSvc?.getCharacteristic(UUID_WRITE_OLD)

            val scannedNotify = preferredNotify ?: legacyNotify ?: run {
                var found: BluetoothGattCharacteristic? = null
                for (svc in g.services) {
                    for (ch in svc.characteristics) {
                        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            found = ch; break
                        }
                    }
                    if (found != null) break
                }
                found
            }

            notifyChar = scannedNotify
            writeChar = preferredWrite ?: legacyWrite

            Log.d(TAG, "notifyChar=${notifyChar?.uuid} writeChar=${writeChar?.uuid}")
            if (scannedNotify == null) {
                Log.e(TAG, "No NOTIFY characteristic found")
                return
            }
            enableNotificationsOrFallback(g, scannedNotify)
        }

        @SuppressLint("MissingPermission")
        private fun enableNotificationsOrFallback(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (!hasConnectPerm()) { Log.e(TAG, "No BLUETOOTH_CONNECT"); return }
            val ok = g.setCharacteristicNotification(ch, true)
            Log.d(TAG, "setCharacteristicNotification=$ok for ${ch.uuid}")

            val cccd = ch.getDescriptor(UUID_CCCD)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val wrote = g.writeDescriptor(cccd)
                Log.d(TAG, "writeDescriptor(CCCD)=$wrote")
                if (!configPushed) {
                    scope.launch {
                        delay(250)
                        enableAnglesAndMagAt100Hz()
                        configPushed = true
                        startWitPowerUpdates()
                    }
                }
            } else {
                Log.e(TAG, "CCCD not found on ${ch.uuid}; using short polling fallback")
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    scope.launch {
                        repeat(40) {
                            try { g.readCharacteristic(ch) } catch (_: Exception) {}
                            delay(50)
                        }
                        if (!configPushed) {
                            enableAnglesAndMagAt100Hz()
                            configPushed = true
                            startWitPowerUpdates()
                        }
                    }
                } else {
                    if (!configPushed) {
                        enableAnglesAndMagAt100Hz()
                        configPushed = true
                        startWitPowerUpdates()
                    }
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                rssiListeners.forEach { it(rssi) }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite ${descriptor.uuid} status=$status")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val bytes = ch.value ?: return
            Log.d(TAG, "chunk (pre13) len=${bytes.size}")
            frameBuffer.addAll(bytes.toList())
            emitFramesFromBuffer { frame ->
                Log.d(TAG, "frame ${String.format("%02X %02X", frame[0], frame[1])}")
                dataListeners.forEach { it(frame) }
            }
            dataListeners.forEach { it(bytes) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            Log.d(TAG, "chunk len=${value.size}")
            frameBuffer.addAll(value.toList())
            emitFramesFromBuffer { frame ->
                Log.d(TAG, "frame ${String.format("%02X %02X", frame[0], frame[1])}")
                dataListeners.forEach { it(frame) }
            }
            dataListeners.forEach { it(value) }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val bytes = ch.value ?: return
                if (ch.uuid == UUID_BATTERY_LEVEL && bytes.isNotEmpty()) {
                    val percent = (bytes[0].toInt() and 0xFF).coerceIn(0, 100)
                    Log.d(TAG, "standard battery=$percent%")
                    batteryListeners.forEach { it(percent) }
                    return
                }
                Log.d(TAG, "read len=${bytes.size}")
                frameBuffer.addAll(bytes.toList())
                emitFramesFromBuffer { frame ->
                    Log.d(TAG, "frame ${String.format("%02X %02X", frame[0], frame[1])}")
                    dataListeners.forEach { it(frame) }
                }
                dataListeners.forEach { it(bytes) }
            }
        }
    }
}
