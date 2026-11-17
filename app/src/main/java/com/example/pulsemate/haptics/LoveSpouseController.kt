package com.example.pulsemate.haptics

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log

class LoveSpouseController(context: Context) : HapticController {

    companion object {
        private const val TAG = "PulseMateBluetooth"
        private const val MANUFACTURER_ID = 0x00FF
        private const val STOP_TEARDOWN_DELAY_MS = 1500L

        private val SERVICE_UUID =
            ParcelUuid.fromString("0000AE8F-0000-1000-8000-00805F9B34FB")

        private val COMMAND_PREFIX = byteArrayOf(
            0x6D, 0xB6.toByte(), 0x43, 0xCE.toByte(),
            0x97.toByte(), 0xFE.toByte(), 0x42, 0x7C
        )

        private val levelCommands = mapOf(
            1 to byteArrayOf(0xD4.toByte(), 0x1F, 0x5D),
            2 to byteArrayOf(0xD7.toByte(), 0x84.toByte(), 0x6F),
            3 to byteArrayOf(0xD6.toByte(), 0x0D, 0x7E),
            4 to byteArrayOf(0xD1.toByte(), 0xB2.toByte(), 0x0A),
            5 to byteArrayOf(0xD0.toByte(), 0x3B, 0x1B),
            6 to byteArrayOf(0xD3.toByte(), 0xA0.toByte(), 0x29),
            7 to byteArrayOf(0xD2.toByte(), 0x29, 0x38),
            8 to byteArrayOf(0xDD.toByte(), 0xDE.toByte(), 0xC0.toByte()),
            9 to byteArrayOf(0xDC.toByte(), 0x57, 0xD1.toByte())
        )

        private val stopCommand =
            byteArrayOf(0xE5.toByte(), 0x15, 0x7D)

        private val handler = Handler(Looper.getMainLooper())
        private val lock = Any()

        private var advertiser: BluetoothLeAdvertiser? = null
        private var advertisingSet: AdvertisingSet? = null
        private var activeCallback: AdvertisingSetCallback? = null
        private var starting = false
        private var latestData: AdvertiseData? = null
        private var latestHex = ""

        @Volatile
        private var lastFailure: String? = null

        private val teardown = Runnable { teardownAdvertising() }

        @SuppressLint("MissingPermission")
        private fun teardownAdvertising() {
            synchronized(lock) {
                val currentAdvertiser = advertiser
                val callback = activeCallback

                if (currentAdvertiser != null && callback != null) {
                    try {
                        currentAdvertiser.stopAdvertisingSet(callback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not stop advertising set", e)
                    }
                }

                advertisingSet = null
                activeCallback = null
                advertiser = null
                latestData = null
                latestHex = ""

                Log.d(TAG, "Advertising stopped after STOP command")
            }
        }
    }

    private val appContext = context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private var currentLevel = 1

    init {
        PrettyLoveLink.init(appContext)
    }

    fun deviceProblem(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return "Bluetooth permission is off. Allow Nearby devices for PulseMate in Settings."
        }

        val adapter = bluetoothAdapter
            ?: return "This phone doesn't support Bluetooth."

        if (!adapter.isEnabled) {
            return "Bluetooth is off. Turn it on to feel the game."
        }

        if (adapter.bluetoothLeAdvertiser == null) {
            return "This phone can't send Bluetooth commands."
        }

        PrettyLoveLink.ensure()

        return lastFailure
    }

    @SuppressLint("MissingPermission")
    override fun setLevel(level: Int) {
        currentLevel = level.coerceIn(1, 9)

        val command = levelCommands[currentLevel]
        if (command == null) {
            Log.e(TAG, "No command for level $currentLevel")
            return
        }

        Log.d(TAG, "Quiz requested level: $currentLevel")
        PrettyLoveLink.setLevel(currentLevel)
        sendCommand(command, isStop = false)
    }

    @SuppressLint("MissingPermission")
    fun pressRemoteButton(button: Int) {
        if (button !in 1..9) {
            Log.w(TAG, "Ignoring invalid remote button: $button")
            return
        }

        currentLevel = button
        Log.d(TAG, "Remote button pressed: $button")
        PrettyLoveLink.setLevel(button)
        sendCommand(levelCommands.getValue(button), isStop = false)
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        Log.d(TAG, "STOP requested")
        PrettyLoveLink.setLevel(0)
        sendCommand(stopCommand, isStop = true)
    }

    @SuppressLint("MissingPermission")
    override fun reset() {
        currentLevel = 1
        Log.d(TAG, "Controller reset")
        PrettyLoveLink.setLevel(0)
        sendCommand(stopCommand, isStop = true)
    }

    private fun sendCommand(command: ByteArray, isStop: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission is missing")
            return
        }

        val adapter = bluetoothAdapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter is unavailable or disabled")
            return
        }

        val currentAdvertiser = adapter.bluetoothLeAdvertiser
        if (currentAdvertiser == null) {
            Log.e(TAG, "This phone does not provide a BLE advertiser")
            return
        }

        val payload = COMMAND_PREFIX + command
        val hexCommand = command.joinToString(" ") {
            "%02X".format(it.toInt() and 0xFF)
        }

        Log.d(TAG, "Sending command: $hexCommand")
        Log.d(
            TAG,
            "Manufacturer payload: " +
                    payload.joinToString(" ") {
                        "%02X".format(it.toInt() and 0xFF)
                    }
        )

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(MANUFACTURER_ID, payload)
            .addServiceUuid(SERVICE_UUID)
            .build()

        synchronized(lock) {
            handler.removeCallbacks(teardown)

            latestData = data
            latestHex = hexCommand

            val set = advertisingSet

            when {
                set != null -> updateSet(currentAdvertiser, set, data, hexCommand)
                !starting -> startSet(currentAdvertiser, data, hexCommand)
                else -> Log.d(TAG, "Advertising is starting, command queued: $hexCommand")
            }

            if (isStop) {
                handler.postDelayed(teardown, STOP_TEARDOWN_DELAY_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateSet(
        currentAdvertiser: BluetoothLeAdvertiser,
        set: AdvertisingSet,
        data: AdvertiseData,
        hexCommand: String
    ) {
        try {
            set.setAdvertisingData(data)
            Log.d(TAG, "Updated advertising data to $hexCommand")
        } catch (e: Exception) {
            Log.w(TAG, "Could not update advertising set, restarting", e)
            advertisingSet = null
            startSet(currentAdvertiser, data, hexCommand)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSet(
        currentAdvertiser: BluetoothLeAdvertiser,
        data: AdvertiseData,
        hexCommand: String
    ) {
        try {
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setConnectable(true)
                .setScannable(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
                .setTxPowerLevel(
                    AdvertisingSetParameters.TX_POWER_HIGH
                )
                .build()

            val callback = object : AdvertisingSetCallback() {

                override fun onAdvertisingSetStarted(
                    set: AdvertisingSet?,
                    txPower: Int,
                    status: Int
                ) {
                    synchronized(lock) {
                        starting = false

                        if (status == ADVERTISE_SUCCESS && set != null) {
                            advertisingSet = set
                            lastFailure = null
                            Log.i(
                                TAG,
                                "Advertising active for command $hexCommand"
                            )

                            val pending = latestData
                            if (pending != null && pending !== data) {
                                try {
                                    set.setAdvertisingData(pending)
                                    Log.d(
                                        TAG,
                                        "Applied queued command $latestHex"
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not apply queued command", e)
                                }
                            }
                        } else {
                            advertisingSet = null
                            activeCallback = null
                            lastFailure =
                                "Bluetooth command failed (code $status). Try turning Bluetooth off and on."
                            Log.e(
                                TAG,
                                "Advertising failed. Status: $status"
                            )
                        }
                    }
                }

                override fun onAdvertisingSetStopped(
                    set: AdvertisingSet?
                ) {
                    synchronized(lock) {
                        if (advertisingSet == set) {
                            advertisingSet = null
                        }
                    }
                    Log.d(TAG, "Advertising set stopped")
                }
            }

            starting = true
            advertiser = currentAdvertiser
            activeCallback = callback

            currentAdvertiser.startAdvertisingSet(
                parameters,
                data,
                null,
                null,
                null,
                callback
            )

            Log.d(TAG, "Started BLE advertising request for $hexCommand")

        } catch (e: SecurityException) {
            starting = false
            activeCallback = null
            Log.e(TAG, "Bluetooth permission error", e)
        } catch (e: IllegalArgumentException) {
            starting = false
            activeCallback = null
            Log.e(TAG, "Invalid BLE advertising configuration", e)
        } catch (e: Exception) {
            starting = false
            activeCallback = null
            Log.e(TAG, "Could not start BLE advertising", e)
        }
    }

    override fun getLevel(): Int {
        return currentLevel
    }
}