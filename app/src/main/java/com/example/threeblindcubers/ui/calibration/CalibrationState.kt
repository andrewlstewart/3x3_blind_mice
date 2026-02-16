package com.example.threeblindcubers.ui.calibration

import com.example.threeblindcubers.domain.models.AxisCalibration

/**
 * Steps in the IMU axis calibration wizard.
 */
enum class CalibrationStep {
    /** Introduction screen explaining the process */
    INTRO,
    /** Collecting baseline quaternion before X-axis rotation */
    X_AXIS_HOLD,
    /** Waiting for user tap before X-axis rotation begins */
    X_AXIS_READY,
    /** User tilts cube forward/back to detect X axis (Orange→Red, L→R) */
    X_AXIS_ROTATE,
    /** X-axis rotation complete, waiting for user tap to continue */
    X_AXIS_DONE,
    /** Collecting baseline quaternion before Z-axis rotation */
    Z_AXIS_HOLD,
    /** Waiting for user tap before Z-axis rotation begins */
    Z_AXIS_READY,
    /** User turns cube left/right to detect Z axis (Yellow→White, D→U) */
    Z_AXIS_ROTATE,
    /** Computing axis mapping from collected data */
    COMPUTING,
    /** Calibration complete and saved */
    COMPLETE,
    /** Error occurred during calibration */
    ERROR
}

/**
 * State model for the calibration wizard UI.
 */
data class CalibrationState(
    val step: CalibrationStep = CalibrationStep.INTRO,
    /** Progress within current step, 0f to 1f (used for hold steps) */
    val progress: Float = 0f,
    /** User-facing status text for the current step */
    val statusText: String = "",
    /** Error message when step is ERROR */
    val errorMessage: String? = null,
    /** The resulting calibration, set when step is COMPLETE */
    val result: AxisCalibration? = null,
    /** Debug info showing the detected axis mapping */
    val debugInfo: String? = null
)
