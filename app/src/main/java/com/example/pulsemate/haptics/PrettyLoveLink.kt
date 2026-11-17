package com.example.pulsemate.haptics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
object PrettyLoveLink {
    private val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val TX: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    private const val SCAN_WINDOW_MS = 25_000L
    private const val PERIOD_MS = 360L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var appContext: Context? = null
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var connected = false
    private var connecting = false
    private var scanning = false
    private var scanStarted = 0L
    private var level = 0

    @Volatile
    var activePattern = 0
        private set
    private var loop: Job? = null
    private var lastError: String? = null
    private var matchedName: String? = null
    private var modeName = ""
    private var testLabel: String? = null
    private const val TEST_PATTERNS = false

    private val seenAddresses = HashSet<String>()

    private class Seen(val label: String, val rssi: Int, val time: Long)

    private val seenInfo = HashMap<String, Seen>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            seenAddresses.add(device.address)

            val record = result.scanRecord
            val name = record?.deviceName ?: device.name ?: ""
            val hasService = record?.serviceUuids?.contains(ParcelUuid(SERVICE)) == true

            seenInfo[device.address] = Seen(
                label = (if (name.isBlank()) "?" else name) + " " + device.address.takeLast(5),
                rssi = result.rssi,
                time = System.currentTimeMillis()
            )

            if (connecting || connected) return

            val lower = name.lowercase()
            val match = hasService ||
                    lower.startsWith("aogu") ||
                    lower.contains("ab shutter") ||
                    lower.contains("prettylove") ||
                    lower.contains("pretty love")

            if (match) {
                matchedName = if (name.isBlank()) device.address else name
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                lastError = null
                g.discoverServices()
            } else {
                lastError = "Connection to ${matchedName ?: "toy"} dropped (status $status)"
                close()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val documented = g.getService(SERVICE)?.getCharacteristic(TX)

            if (documented != null) {
                tx = documented
                modeName = "burst mode"
                connected = true
                connecting = false
                startLoop(false)
                return
            }

            val writable = ArrayList<BluetoothGattCharacteristic>()

            for (s in g.services) {
                val id = s.uuid.toString().substring(4, 8).lowercase()

                if (id == "1800" || id == "1801" || id == "180a") continue

                for (c in s.characteristics) {
                    if (c.properties and 0x0C != 0) writable.add(c)
                }
            }

            if (writable.isEmpty()) {
                lastError = "${matchedName ?: "Toy"} has nothing to write to"
                close()
                return
            }

            tx = if (writable.size >= 3) writable[1] else writable[0]
            modeName = "steady mode"
            connected = true
            connecting = false
            startLoop(true)
        }
    }

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        ensure()
    }

    private fun adapter(): BluetoothAdapter? {
        val manager = appContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun hasPermission(): Boolean {
        val c = appContext ?: return false

        return if (Build.VERSION.SDK_INT >= 31) {
            c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun ensure() {
        if (connected || connecting) return

        val a = adapter() ?: return

        if (!a.isEnabled || !hasPermission()) return

        val now = System.currentTimeMillis()

        if (scanning && now - scanStarted < SCAN_WINDOW_MS) return

        try {
            if (scanning) a.bluetoothLeScanner?.stopScan(scanCallback)

            scanning = true
            scanStarted = now

            a.bluetoothLeScanner?.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
            )
        } catch (e: Exception) {
            scanning = false
        }
    }

    private fun connect(device: BluetoothDevice) {
        connecting = true

        try {
            adapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
        }

        scanning = false

        try {
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            connecting = false
        }
    }

    private fun close() {
        loop?.cancel()
        loop = null
        connected = false
        connecting = false
        tx = null

        try {
            gatt?.close()
        } catch (e: Exception) {
        }

        gatt = null
    }

    private fun write(bytes: ByteArray) {
        val g = gatt ?: return
        val c = tx ?: return
        val type = if (c.properties and 0x04 != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, type)
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = type
                    c.value = bytes
                    g.writeCharacteristic(c)
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun burstFor(l: Int): Int {
        val f = (l.coerceIn(1, 9) - 1) / 8f

        return 0xC9 + Math.round(f * (0xDC - 0xC9))
    }

    private fun startLoop(steady: Boolean) {
        loop?.cancel()

        if (TEST_PATTERNS && steady) {
            loop = scope.launch {
                while (isActive) {
                    for (p in 1..12) {
                        testLabel = "Pattern $p of 12  [00 %02X]".format(p)
                        write(byteArrayOf(0x00, p.toByte()))
                        delay(4000L)

                        testLabel = "Pattern $p done. Quiet pause"
                        write(byteArrayOf(0x00, 0x00))
                        delay(2500L)
                    }

                    testLabel = "Restarting in 4s"
                    delay(4000L)
                }
            }

            return
        }

        loop = scope.launch {
            var stopped = false

            write(byteArrayOf(0x00, 0x00))

            while (isActive) {
                val l = level

                if (l <= 0) {
                    if (!stopped) {
                        write(byteArrayOf(0x00, 0x00))
                        stopped = true
                    }

                    delay(120L)
                    continue
                }

                stopped = false

                if (steady) {
                    val duty = 0.18f + 0.82f * (l - 1) / 8f

                    if (duty >= 0.99f) {
                        write(byteArrayOf(0x00, 0x01))
                        delay(PERIOD_MS)
                    } else {
                        val on = (PERIOD_MS * duty).toLong().coerceAtLeast(40L)

                        write(byteArrayOf(0x00, 0x01))
                        delay(on)
                        write(byteArrayOf(0x00, 0x00))
                        delay((PERIOD_MS - on).coerceAtLeast(40L))
                    }
                } else {
                    write(byteArrayOf(0x00, burstFor(l).toByte()))
                    delay(220L)
                }
            }
        }
    }

    fun setLevel(value: Int) {
        level = value.coerceIn(0, 9)

        if (level > 0) activePattern = 0
    }

    fun pattern(p: Int) {
        level = 0
        activePattern = p.coerceIn(0, 12)
        write(byteArrayOf(0x00, p.coerceIn(0, 12).toByte()))
    }

    fun stopNow() {
        level = 0
        activePattern = 0
        write(byteArrayOf(0x00, 0x00))
    }

    fun isConnected(): Boolean = connected

    fun problem(): String? {
        ensure()

        val a = adapter()

        return when {
            connected && testLabel != null -> "Prettylove pattern test. Tell me what each number feels like:\n$testLabel"
            connected -> "Prettylove: connected to ${matchedName ?: "toy"} ($modeName)"
            a == null || !a.isEnabled -> null
            !hasPermission() -> "Prettylove: Nearby devices permission missing"
            connecting -> "Prettylove: connecting to ${matchedName ?: "toy"}…"
            lastError != null -> "Prettylove: $lastError"
            else -> null
        }
    }
}