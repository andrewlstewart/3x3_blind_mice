package com.example.threeblindcubers.domain.models

/**
 * Connection state for the Bluetooth cube
 */
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}
