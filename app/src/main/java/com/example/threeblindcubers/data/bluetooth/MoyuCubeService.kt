package com.example.threeblindcubers.data.bluetooth

import android.bluetooth.BluetoothDevice
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.domain.models.CubeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for communicating with the Moyu V10 smart cube via Bluetooth LE
 */
interface MoyuCubeService {

    /**
     * Current connection state
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Flow of cube events (moves, connection changes, errors)
     */
    val cubeEvents: SharedFlow<CubeEvent>

    /**
     * Scans for nearby Bluetooth LE devices
     * @return Flow of discovered devices
     */
    fun scan(): Flow<BluetoothDevice>

    /**
     * Connects to the specified Bluetooth device
     * @param device The Moyu cube device to connect to
     */
    suspend fun connect(device: BluetoothDevice)

    /**
     * Disconnects from the currently connected cube
     */
    suspend fun disconnect()

    /**
     * Stops scanning for devices
     */
    fun stopScan()

    /**
     * Checks if currently scanning
     */
    fun isScanning(): Boolean

    /**
     * Gets the currently connected device, if any
     */
    fun getConnectedDevice(): BluetoothDevice?
}
