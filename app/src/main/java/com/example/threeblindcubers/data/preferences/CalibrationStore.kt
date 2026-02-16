package com.example.threeblindcubers.data.preferences

import android.content.Context
import com.example.threeblindcubers.domain.models.AxisCalibration
import org.json.JSONObject

/**
 * SharedPreferences-backed persistence for axis calibrations, keyed by
 * Bluetooth device MAC address. Each device can have its own calibration
 * since different cube models may have different IMU orientations.
 */
class CalibrationStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves an axis calibration for the given device address.
     */
    fun save(deviceAddress: String, calibration: AxisCalibration) {
        val json = JSONObject().apply {
            put(KEY_X_SOURCE, calibration.xSourceAxis)
            put(KEY_X_SIGN, calibration.xSign.toDouble())
            put(KEY_Y_SOURCE, calibration.ySourceAxis)
            put(KEY_Y_SIGN, calibration.ySign.toDouble())
            put(KEY_Z_SOURCE, calibration.zSourceAxis)
            put(KEY_Z_SIGN, calibration.zSign.toDouble())
        }
        prefs.edit().putString(keyFor(deviceAddress), json.toString()).apply()
    }

    /**
     * Loads a previously saved axis calibration for the given device address.
     * Returns null if no calibration exists or if the saved data is corrupt.
     */
    fun load(deviceAddress: String): AxisCalibration? {
        val jsonStr = prefs.getString(keyFor(deviceAddress), null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            AxisCalibration(
                xSourceAxis = json.getInt(KEY_X_SOURCE),
                xSign = json.getDouble(KEY_X_SIGN).toFloat(),
                ySourceAxis = json.getInt(KEY_Y_SOURCE),
                ySign = json.getDouble(KEY_Y_SIGN).toFloat(),
                zSourceAxis = json.getInt(KEY_Z_SOURCE),
                zSign = json.getDouble(KEY_Z_SIGN).toFloat()
            )
        } catch (_: Exception) {
            // Corrupt data — remove it and return null
            prefs.edit().remove(keyFor(deviceAddress)).apply()
            null
        }
    }

    /**
     * Returns true if a calibration has been saved for the given device address.
     */
    fun hasCalibration(deviceAddress: String): Boolean {
        return prefs.contains(keyFor(deviceAddress))
    }

    private fun keyFor(deviceAddress: String): String = "$KEY_PREFIX$deviceAddress"

    companion object {
        private const val PREFS_NAME = "axis_calibration"
        private const val KEY_PREFIX = "cal_"
        private const val KEY_X_SOURCE = "xSrc"
        private const val KEY_X_SIGN = "xSign"
        private const val KEY_Y_SOURCE = "ySrc"
        private const val KEY_Y_SIGN = "ySign"
        private const val KEY_Z_SOURCE = "zSrc"
        private const val KEY_Z_SIGN = "zSign"
    }
}
