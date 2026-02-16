package com.example.threeblindcubers.ui.timer.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.threeblindcubers.domain.models.Move

/**
 * Rubik's cube visualization composable.
 *
 * Delegates to [CubeVisualization3D] for true 3D rendering with OpenGL ES 2.0.
 * The original 2D isometric rendering is available as [CubeVisualization2D].
 *
 * @param cubeState 54-element list: state[i] = color index (0-5)
 *   Colors: 0=White(U), 1=Red(R), 2=Green(F), 3=Yellow(D), 4=Orange(L), 5=Blue(B)
 * @param orientationW quaternion W component for IMU orientation (default: identity)
 * @param orientationX quaternion X component
 * @param orientationY quaternion Y component
 * @param orientationZ quaternion Z component
 * @param lastMove the most recent quarter-turn move (for turn animation)
 * @param lastMoveId monotonically increasing ID to trigger animation on new moves
 * @param onLongPress callback invoked on long-press (e.g., to calibrate orientation)
 */
@Composable
fun CubeVisualization(
    cubeState: List<Int>,
    modifier: Modifier = Modifier,
    orientationW: Float = 1f,
    orientationX: Float = 0f,
    orientationY: Float = 0f,
    orientationZ: Float = 0f,
    lastMove: Move? = null,
    lastMoveId: Int = 0,
    onLongPress: (() -> Unit)? = null
) {
    CubeVisualization3D(
        cubeState = cubeState,
        modifier = modifier,
        orientationW = orientationW,
        orientationX = orientationX,
        orientationY = orientationY,
        orientationZ = orientationZ,
        lastMove = lastMove,
        lastMoveId = lastMoveId,
        onLongPress = onLongPress
    )
}
