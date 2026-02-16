package com.example.threeblindcubers.domain.models

/**
 * Represents the six faces of a Rubik's cube
 */
enum class Face {
    U, // Up (White)
    D, // Down (Yellow)
    L, // Left (Orange)
    R, // Right (Red)
    F, // Front (Green)
    B; // Back (Blue)

    companion object {
        fun fromString(s: String): Face? {
            return entries.find { it.name.equals(s, ignoreCase = true) }
        }
    }
}
