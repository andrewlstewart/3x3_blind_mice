package com.example.threeblindcubers.ui.timer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Isometric 3D Rubik's cube visualization showing U (top), F (front), R (right) faces.
 * This is the original 2D Canvas-based rendering, kept as a fallback.
 *
 * Viewpoint: looking from front-right-above.
 *
 * @param cubeState 54-element list: state[i] = color index (0-5)
 *   Colors: 0=White(U), 1=Red(R), 2=Green(F), 3=Yellow(D), 4=Orange(L), 5=Blue(B)
 */
@Composable
fun CubeVisualization2D(
    cubeState: List<Int>,
    modifier: Modifier = Modifier
) {
    // Get the visual grid mappings
    val uGrid = getTopFaceGrid(cubeState)
    val fGrid = getFrontFaceGrid(cubeState)
    val rGrid = getRightFaceGrid(cubeState)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .padding(8.dp)
    ) {
        val w = size.width
        val h = size.height

        // Isometric projection: 3D (x,y,z) -> 2D screen coords
        // X = right, Y = up, Z = toward viewer
        // screenX = (x - z) * cos30 * cellSize
        // screenY = (x + z) * sin30 * cellSize - y * cellSize
        val angle = Math.toRadians(30.0)
        val cos30 = cos(angle).toFloat()
        val sin30 = sin(angle).toFloat()

        // Size cell so the full hexagon fits within the canvas
        // Horizontal span: x ranges [0,3], z ranges [0,3] -> screenX spans [-3*cos30, 3*cos30] = 6*cos30
        // Vertical span: (x+z)*sin30 ranges [0, 3*sin30+3*sin30]=6*sin30, y*1 ranges [0,3] -> total = 3+6*sin30
        val cellSize = min(w / (6f * cos30), h / (3f + 6f * sin30)) * 0.85f

        // Project a 3D point to screen coordinates (before centering offset)
        fun projectX(x: Float, z: Float): Float = (x - z) * cos30 * cellSize
        fun projectY(x: Float, y: Float, z: Float): Float = (x + z) * sin30 * cellSize - y * cellSize

        // Compute bounding box of all 8 cube corners to center the drawing
        val cubeCorners = listOf(
            Triple(0f, 0f, 0f), Triple(3f, 0f, 0f), Triple(0f, 3f, 0f), Triple(0f, 0f, 3f),
            Triple(3f, 3f, 0f), Triple(3f, 0f, 3f), Triple(0f, 3f, 3f), Triple(3f, 3f, 3f)
        )
        val screenXs = cubeCorners.map { (cx, _, cz) -> projectX(cx, cz) }
        val screenYs = cubeCorners.map { (cx, cy, cz) -> projectY(cx, cy, cz) }
        val minSX = screenXs.min()
        val maxSX = screenXs.max()
        val minSY = screenYs.min()
        val maxSY = screenYs.max()

        // Offset to center the cube in the canvas
        val offsetX = (w - (maxSX - minSX)) / 2f - minSX
        val offsetY = (h - (maxSY - minSY)) / 2f - minSY

        // Final projection with centering
        fun sx(x: Float, z: Float): Float = projectX(x, z) + offsetX
        fun sy(x: Float, y: Float, z: Float): Float = projectY(x, y, z) + offsetY

        // Draw a single parallelogram cell given its 4 corners in 3D
        fun drawQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            color: Color
        ) {
            val path = Path().apply {
                moveTo(sx(x0, z0), sy(x0, y0, z0))
                lineTo(sx(x1, z1), sy(x1, y1, z1))
                lineTo(sx(x2, z2), sy(x2, y2, z2))
                lineTo(sx(x3, z3), sy(x3, y3, z3))
                close()
            }
            drawPath(path, color, style = Fill)
            drawPath(path, Color.Black, style = Stroke(width = 2f))
        }

        // --- 1. Top face (U): y=3 plane ---
        // Draw back rows first (visRow=2 -> 0) for painter's algorithm
        for (visRow in 2 downTo 0) {
            for (visCol in 0 until 3) {
                val colorIdx = uGrid[visRow][visCol]
                val color = shadeColor(faceColor(colorIdx), 1.0f)

                // visRow=0 = front edge (z=2->3), visRow=2 = back edge (z=0->1)
                val x0 = visCol.toFloat()
                val x1 = x0 + 1f
                val y = 3f
                val z0 = (2 - visRow).toFloat()     // back z
                val z1 = z0 + 1f                     // front z

                // Quad corners (clockwise from back-left):
                // back-left, back-right, front-right, front-left
                drawQuad(
                    x0, y, z0,   // back-left
                    x1, y, z0,   // back-right
                    x1, y, z1,   // front-right
                    x0, y, z1,   // front-left
                    color
                )
            }
        }

        // --- 2. Front face (F): z=3 plane ---
        // visRow=0 = bottom (y=0->1), visRow=2 = top (y=2->3)
        for (visRow in 0 until 3) {
            for (visCol in 0 until 3) {
                val colorIdx = fGrid[visRow][visCol]
                val color = shadeColor(faceColor(colorIdx), 0.85f)

                val x0 = visCol.toFloat()
                val x1 = x0 + 1f
                val yBot = visRow.toFloat()
                val yTop = yBot + 1f
                val z = 3f

                // Quad corners (clockwise from bottom-left):
                // bottom-left, bottom-right, top-right, top-left
                drawQuad(
                    x0, yBot, z,   // bottom-left
                    x1, yBot, z,   // bottom-right
                    x1, yTop, z,   // top-right
                    x0, yTop, z,   // top-left
                    color
                )
            }
        }

        // --- 3. Right face (R): x=3 plane ---
        // visRow=0 = bottom (y=0->1), visRow=2 = top (y=2->3)
        // visCol=0 = front (z=2->3), visCol=2 = back (z=0->1)
        for (visRow in 0 until 3) {
            for (visCol in 0 until 3) {
                val colorIdx = rGrid[visRow][visCol]
                val color = shadeColor(faceColor(colorIdx), 0.70f)

                val x = 3f
                val yBot = visRow.toFloat()
                val yTop = yBot + 1f
                val z0 = (2 - visCol).toFloat()   // back z (reversed: visCol=0 -> z=2)
                val z1 = z0 + 1f                   // front z

                // Quad corners (clockwise from front-bottom):
                // front-bottom, back-bottom, back-top, front-top
                drawQuad(
                    x, yBot, z1,   // front-bottom
                    x, yBot, z0,   // back-bottom
                    x, yTop, z0,   // back-top
                    x, yTop, z1,   // front-top
                    color
                )
            }
        }
    }
}

/**
 * A 3x3 grid of color indices for one face of the cube, in visual order.
 * [visRow][visCol] where visRow=0 is the row nearest the origin (bottom/front),
 * visRow=2 is farthest from origin (top/back).
 */
typealias FaceGrid = Array<IntArray>

/**
 * Maps Kociemba U face (indices 0-8) to visual grid.
 *
 * Kociemba U layout (looking down, F toward you):
 *   0 1 2   <- row 0 = BACK (farthest from viewer)
 *   3 4 5
 *   6 7 8   <- row 2 = FRONT (closest to viewer, touching F face top edge)
 *
 * Visual grid on isometric top face:
 *   Origin = front-top-left corner of cube (where U meets F's top-left)
 *   col direction = xVec (right along front-top edge)
 *   row direction = zVec (into depth, away from viewer)
 *   visRow=0 = at origin (FRONT edge)
 *   visRow=2 = farthest back
 *
 * So: visRow=0 = Kociemba row 2 (front), visRow=2 = Kociemba row 0 (back)
 *     visCol=0 = Kociemba col 0 (left),  visCol=2 = Kociemba col 2 (right)
 */
fun getTopFaceGrid(cubeState: List<Int>): FaceGrid {
    return Array(3) { visRow ->
        IntArray(3) { visCol ->
            val kRow = 2 - visRow  // front=visRow0=kRow2, back=visRow2=kRow0
            val kCol = visCol
            cubeState.getOrElse(kRow * 3 + kCol) { 0 }
        }
    }
}

/**
 * Maps Kociemba F face (indices 18-26) to visual grid.
 *
 * Kociemba F layout (looking at front face):
 *   18 19 20   <- row 0 = TOP
 *   21 22 23
 *   24 25 26   <- row 2 = BOTTOM
 *
 * Visual grid on isometric front face:
 *   Origin = front-bottom-left corner
 *   col direction = xVec (right)
 *   row direction = yVec (up)
 *   visRow=0 = at origin (BOTTOM), visRow=2 = top
 *
 * So: visRow=0 = Kociemba row 2 (bottom), visRow=2 = Kociemba row 0 (top)
 *     visCol=0 = Kociemba col 0 (left),   visCol=2 = Kociemba col 2 (right)
 */
fun getFrontFaceGrid(cubeState: List<Int>): FaceGrid {
    return Array(3) { visRow ->
        IntArray(3) { visCol ->
            val kRow = 2 - visRow
            val kCol = visCol
            cubeState.getOrElse(18 + kRow * 3 + kCol) { 0 }
        }
    }
}

/**
 * Maps Kociemba R face (indices 9-17) to visual grid.
 *
 * Kociemba R layout (looking at right face from outside):
 *    9 10 11   <- row 0 = TOP
 *   12 13 14
 *   15 16 17   <- row 2 = BOTTOM
 *
 * When looking at R from the right side:
 *   col 0 (9,12,15) = LEFT edge of the R face = FRONT edge of cube
 *   col 2 (11,14,17) = RIGHT edge of the R face = BACK edge of cube
 *
 * Visual grid on isometric right face:
 *   Origin = front-bottom-right corner
 *   col direction = zVec (into depth, away from viewer)
 *   row direction = yVec (up)
 *   visRow=0 = at origin (BOTTOM), visRow=2 = top
 *   visCol=0 = at origin (FRONT edge), visCol=2 = back edge
 *
 * So: visRow=0 = Kociemba row 2 (bottom), visRow=2 = Kociemba row 0 (top)
 *     visCol=0 = Kociemba col 0 (front),  visCol=2 = Kociemba col 2 (back)
 */
fun getRightFaceGrid(cubeState: List<Int>): FaceGrid {
    return Array(3) { visRow ->
        IntArray(3) { visCol ->
            val kRow = 2 - visRow
            val kCol = visCol
            cubeState.getOrElse(9 + kRow * 3 + kCol) { 0 }
        }
    }
}

internal fun shadeColor(base: Color, factor: Float) = Color(
    red = (base.red * factor).coerceIn(0f, 1f),
    green = (base.green * factor).coerceIn(0f, 1f),
    blue = (base.blue * factor).coerceIn(0f, 1f),
    alpha = 1f
)

/** 0=White(U), 1=Red(R), 2=Green(F), 3=Yellow(D), 4=Orange(L), 5=Blue(B) */
internal fun faceColor(colorIndex: Int): Color = when (colorIndex) {
    0 -> Color.White
    1 -> Color(0xFFCC0000)
    2 -> Color(0xFF009900)
    3 -> Color(0xFFFFDD00)
    4 -> Color(0xFFFF8800)
    5 -> Color(0xFF0044CC)
    else -> Color.Gray
}
