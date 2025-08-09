package com.example.appfightsmart

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        // Newer WIT layout (from your email)
        private val UUID_SERVICE: UUID = UUID.fromString("0000ffe5-0000-1000-8000-00805f9a34fb")
        private val UUID_READ: UUID    = UUID.fromString("0000ffe4-0000-1000-8000-00805f9a34fb") // NOTIFY
        private val UUID_WRITE: UUID   = UUID.fromString("0000ffe9-0000-1000-8000-00805f9a34fb")
        // Older WIT layout (fallback)
        private val UUID_SERVICE_OLD: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9a34fb")
        private val UUID_NOTIFY_OLD:  UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9a34fb")
        private val UUID_WRITE_OLD:   UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9a34fb")
        // Correct CCCD base UUID ends with ...9b34fb (NOT ...9a34fb)
        private val UUID_CCCD: UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // ---------- WRITE helper ----------
    @Suppress("MemberVisibilityCanBePrivate")
    fun sendWitCommand(cmd: ByteArray) {
        // Prefer the cached writeChar; fall back to service lookups (new and old)
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

    /** One-tap: unlock → rate 100Hz → output mask (acc/gyro/angle/mag/port) → save */
    fun enableAnglesAndMagAt100Hz() {
        // 1) Unlock
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x69, 0x88.toByte(), 0xB5.toByte()))
        // 2) Return rate = 100 Hz  (FF AA 03 09 00)
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x03, 0x09, 0x00))
        // 3) Output content mask (acc+gyro+angle+mag+port). Adjust later if needed.
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x02, 0x3E, 0x00))
        // 4) Save config
        sendWitCommand(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x00, 0x00, 0x00))
    }

    fun setOnConnectionStateChange(cb: (Boolean) -> Unit) {
        onConnectionStateChange = cb
    }

    // === Multi-listener support ===
    private val dataListeners = Collections.synchronizedList(mutableListOf<(ByteArray) -> Unit>())
    fun addDataListener(listener: (ByteArray) -> Unit) { dataListeners.add(listener) }
    fun removeDataListener(listener: (ByteArray) -> Unit) { dataListeners.remove(listener) }

    private val connListeners = Collections.synchronizedList(mutableListOf<(Boolean) -> Unit>())
    fun addConnectionListener(l: (Boolean) -> Unit) { connListeners.add(l) }
    fun removeConnectionListener(l: (Boolean) -> Unit) { connListeners.remove(l) }

    // === System BT ===
    private val sysBtMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    private val btAdapter: BluetoothAdapter? = sysBtMgr?.adapter
    private var gatt: BluetoothGatt? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // SEARCH ANCHOR 1: CONFIG PUSH FLAG
    @Volatile private var configPushed = false

    // --- Reassembler buffer for 11-byte WIT frames ---
    private val frameBuffer = ArrayList<Byte>(256)
    private fun emitFramesFromBuffer(emit: (ByteArray) -> Unit) {
        var i = 0
        while (frameBuffer.size - i >= 11) {
            while (i < frameBuffer.size && frameBuffer[i] != 0x55.toByte()) i++
            if (frameBuffer.size - i < 11) break
            val second = frameBuffer[i + 1]
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

    fun isBluetoothEnabled(): Boolean = btAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        try {
            Log.d(TAG, "Connecting to $deviceAddress")
            if (btAdapter?.isEnabled != true) return
            if (!BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                Log.e(TAG, "Invalid address $deviceAddress")
                return
            }
            gatt?.close()
            val device = btAdapter.getRemoteDevice(deviceAddress)
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            Log.e(TAG, "connect error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        if (!hasConnectPerm()) return
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        notifyChar = null
        writeChar = null
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

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        onConnectionStateChange(true)
                        connListeners.forEach { it(true) }
                    }
                }
                if (hasConnectPerm()) {
                    g.requestMtu(185)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        onConnectionStateChange(false)
                        connListeners.forEach { it(false) }
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
            if (!hasConnectPerm()) {
                Log.e(TAG, "No BLUETOOTH_CONNECT")
                return
            }
            val ok = g.setCharacteristicNotification(ch, true)
            Log.d(TAG, "setCharacteristicNotification=$ok for ${ch.uuid}")

            val cccd = ch.getDescriptor(UUID_CCCD)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val wrote = g.writeDescriptor(cccd)
                Log.d(TAG, "writeDescriptor(CCCD)=$wrote")

                // SEARCH ANCHOR 2: CALL CONFIG AFTER NOTIFY
                if (!configPushed) {
                    scope.launch {
                        delay(250) // let notifications settle
                        enableAnglesAndMagAt100Hz()
                        configPushed = true
                    }
                }
            } else {
                Log.e(TAG, "CCCD not found on ${ch.uuid}; using short polling fallback")
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    scope.launch {
                        repeat(40) { // ~2s at 50ms
                            try { g.readCharacteristic(ch) } catch (_: Exception) {}
                            delay(50)
                        }
                        // After a brief polling warmup, push config too
                        if (!configPushed) {
                            enableAnglesAndMagAt100Hz()
                            configPushed = true
                        }
                    }
                } else {
                    // No READ either; still try to push config once.
                    if (!configPushed) {
                        enableAnglesAndMagAt100Hz()
                        configPushed = true
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite ${descriptor.uuid} status=$status")
        }

        // pre-Android 13
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

        // Android 13+
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
