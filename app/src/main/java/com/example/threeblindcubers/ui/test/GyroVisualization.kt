package com.example.threeblindcubers.ui.test

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 composable that renders 3 colored axis lines (X=red, Y=green, Z=blue)
 * rotated by the provided unit quaternion, with a small white cube at the origin as a centroid.
 *
 * Used in the debug screen to visualize gyro/IMU orientation data from the smart cube.
 *
 * @param quatW quaternion W component (real part, normalized -1 to +1)
 * @param quatX quaternion X component (normalized)
 * @param quatY quaternion Y component (normalized)
 * @param quatZ quaternion Z component (normalized)
 */
@Composable
fun GyroAxesView(
    quatW: Float,
    quatX: Float,
    quatY: Float,
    quatZ: Float,
    modifier: Modifier = Modifier
) {
    val renderer = remember { GyroAxesRenderer() }

    // Match the app's background color
    val bgColor = MaterialTheme.colorScheme.background.toArgb()
    renderer.setBackgroundColor(
        ((bgColor shr 16) and 0xFF) / 255f,
        ((bgColor shr 8) and 0xFF) / 255f,
        (bgColor and 0xFF) / 255f
    )

    // Update quaternion whenever it changes
    renderer.setQuaternion(quatW, quatX, quatY, quatZ)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        AndroidView(
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            update = { view ->
                view.requestRender()
            },
            modifier = Modifier.matchParentSize()
        )
    }
}

// ---- Shaders ----

private const val AXES_VERTEX_SHADER = """
    uniform mat4 uMVPMatrix;
    attribute vec4 aPosition;
    attribute vec4 aColor;
    varying vec4 vColor;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        gl_PointSize = 8.0;
        vColor = aColor;
    }
"""

private const val AXES_FRAGMENT_SHADER = """
    precision mediump float;
    varying vec4 vColor;
    void main() {
        gl_FragColor = vColor;
    }
"""

// ---- Renderer ----

/**
 * OpenGL ES 2.0 renderer that draws 3 colored axis lines from the origin
 * and a small white cube at the centroid. The axes are rotated by a unit quaternion.
 */
internal class GyroAxesRenderer : GLSurfaceView.Renderer {

    // Quaternion (unit quaternion, normalized)
    @Volatile private var qW = 1f
    @Volatile private var qX = 0f
    @Volatile private var qY = 0f
    @Volatile private var qZ = 0f

    // Background color
    @Volatile private var bgR = 0f
    @Volatile private var bgG = 0f
    @Volatile private var bgB = 0f
    @Volatile private var bgDirty = true

    // GL handles
    private var program = 0
    private var uMVPMatrixHandle = 0
    private var aPositionHandle = 0
    private var aColorHandle = 0

    // Buffers
    private var axisVertexBuffer: FloatBuffer? = null
    private var axisColorBuffer: FloatBuffer? = null
    private var cubeVertexBuffer: FloatBuffer? = null
    private var cubeColorBuffer: FloatBuffer? = null
    private var cubeIndexBuffer: java.nio.ShortBuffer? = null

    // Matrices
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    fun setBackgroundColor(r: Float, g: Float, b: Float) {
        bgR = r; bgG = g; bgB = b; bgDirty = true
    }

    fun setQuaternion(w: Float, x: Float, y: Float, z: Float) {
        qW = w; qX = x; qY = y; qZ = z
    }

    /**
     * Build a 4x4 rotation matrix from a unit quaternion.
     *
     * Standard quaternion-to-rotation-matrix formula:
     *   [ 1-2(y²+z²)   2(xy-wz)    2(xz+wy)   0 ]
     *   [ 2(xy+wz)     1-2(x²+z²)  2(yz-wx)   0 ]
     *   [ 2(xz-wy)     2(yz+wx)    1-2(x²+y²) 0 ]
     *   [ 0            0           0           1 ]
     *
     * OpenGL uses column-major order, so m[col*4 + row].
     */
    private fun quaternionToMatrix(w: Float, x: Float, y: Float, z: Float, m: FloatArray) {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z

        // Column 0
        m[0]  = 1f - 2f * (yy + zz)
        m[1]  = 2f * (xy + wz)
        m[2]  = 2f * (xz - wy)
        m[3]  = 0f

        // Column 1
        m[4]  = 2f * (xy - wz)
        m[5]  = 1f - 2f * (xx + zz)
        m[6]  = 2f * (yz + wx)
        m[7]  = 0f

        // Column 2
        m[8]  = 2f * (xz + wy)
        m[9]  = 2f * (yz - wx)
        m[10] = 1f - 2f * (xx + yy)
        m[11] = 0f

        // Column 3
        m[12] = 0f
        m[13] = 0f
        m[14] = 0f
        m[15] = 1f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glLineWidth(4.0f)

        // Compile shaders
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, AXES_VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, AXES_FRAGMENT_SHADER)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aColorHandle = GLES20.glGetAttribLocation(program, "aColor")

        // ---- Axis lines: 3 lines from origin along +X, +Y, +Z (length 1.5) ----
        val axisLen = 1.5f
        val axisVertices = floatArrayOf(
            // X axis: origin -> +X
            0f, 0f, 0f,   axisLen, 0f, 0f,
            // Y axis: origin -> +Y
            0f, 0f, 0f,   0f, axisLen, 0f,
            // Z axis: origin -> +Z
            0f, 0f, 0f,   0f, 0f, axisLen
        )
        val axisColors = floatArrayOf(
            // X axis: red
            1f, 0.2f, 0.2f, 1f,   1f, 0.2f, 0.2f, 1f,
            // Y axis: green
            0.2f, 1f, 0.2f, 1f,   0.2f, 1f, 0.2f, 1f,
            // Z axis: blue
            0.3f, 0.5f, 1f, 1f,   0.3f, 0.5f, 1f, 1f
        )

        axisVertexBuffer = allocateFloatBuffer(axisVertices)
        axisColorBuffer = allocateFloatBuffer(axisColors)

        // ---- Centroid cube: small cube at origin ----
        val s = 0.08f // half-size
        val cubeVerts = floatArrayOf(
            -s, -s,  s,   s, -s,  s,   s,  s,  s,  -s,  s,  s,  // front
            -s, -s, -s,   s, -s, -s,   s,  s, -s,  -s,  s, -s   // back
        )
        val cubeColors = FloatArray(8 * 4) { 1f } // all white, RGBA=1

        cubeVertexBuffer = allocateFloatBuffer(cubeVerts)
        cubeColorBuffer = allocateFloatBuffer(cubeColors)

        val cubeIdx = shortArrayOf(
            // front
            0, 1, 2,  0, 2, 3,
            // back
            5, 4, 7,  5, 7, 6,
            // top
            3, 2, 6,  3, 6, 7,
            // bottom
            4, 5, 1,  4, 1, 0,
            // right
            1, 5, 6,  1, 6, 2,
            // left
            4, 0, 3,  4, 3, 7
        )
        cubeIndexBuffer = ByteBuffer
            .allocateDirect(cubeIdx.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(cubeIdx); position(0) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 40f, ratio, 0.5f, 50f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (bgDirty) {
            GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
            bgDirty = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Fixed camera: looking at origin from slightly above and to the right
        val camDist = 4.5f
        val camX = camDist * 0.5f
        val camY = camDist * 0.4f
        val camZ = camDist * 0.7f

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,  // eye
            0f, 0f, 0f,       // center
            0f, 1f, 0f        // up
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glUseProgram(program)

        // Build model matrix from quaternion
        quaternionToMatrix(qW, qX, qY, qZ, modelMatrix)

        // MVP = VP * Model
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        // Draw axis lines
        GLES20.glLineWidth(4.0f)
        axisVertexBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, axisVertexBuffer)

        axisColorBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(aColorHandle)
        GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, 0, axisColorBuffer)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 6) // 3 lines x 2 vertices

        // Draw negative axis lines (dimmer, to show orientation better)
        drawNegativeAxes()

        // Draw centroid cube
        cubeVertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, cubeVertexBuffer)

        cubeColorBuffer?.position(0)
        GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, 0, cubeColorBuffer)

        cubeIndexBuffer?.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, cubeIndexBuffer)

        // Draw axis labels as small endpoint cubes
        drawAxisEndpoints()

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aColorHandle)

        // Draw reference grid (unrotated) for orientation context
        drawReferenceGrid()
    }

    /**
     * Draw dimmer negative axis lines so we can see the full orientation.
     */
    private fun drawNegativeAxes() {
        val axisLen = 1.0f
        val negVertices = floatArrayOf(
            0f, 0f, 0f,  -axisLen, 0f, 0f,
            0f, 0f, 0f,   0f, -axisLen, 0f,
            0f, 0f, 0f,   0f, 0f, -axisLen
        )
        val negColors = floatArrayOf(
            0.4f, 0.15f, 0.15f, 1f,  0.4f, 0.15f, 0.15f, 1f,
            0.15f, 0.4f, 0.15f, 1f,  0.15f, 0.4f, 0.15f, 1f,
            0.15f, 0.2f, 0.4f, 1f,   0.15f, 0.2f, 0.4f, 1f
        )

        val vb = allocateFloatBuffer(negVertices)
        val cb = allocateFloatBuffer(negColors)

        GLES20.glLineWidth(2.0f)
        vb.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
        cb.position(0)
        GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 6)
    }

    /**
     * Draw small cubes at the endpoints of each axis to make them more visible.
     */
    private fun drawAxisEndpoints() {
        val axisLen = 1.5f
        val s = 0.06f
        val endpoints = arrayOf(
            floatArrayOf(axisLen, 0f, 0f) to floatArrayOf(1f, 0.2f, 0.2f, 1f), // X red
            floatArrayOf(0f, axisLen, 0f) to floatArrayOf(0.2f, 1f, 0.2f, 1f), // Y green
            floatArrayOf(0f, 0f, axisLen) to floatArrayOf(0.3f, 0.5f, 1f, 1f)  // Z blue
        )

        for ((pos, color) in endpoints) {
            val cx = pos[0]; val cy = pos[1]; val cz = pos[2]
            val verts = floatArrayOf(
                cx - s, cy - s, cz + s,  cx + s, cy - s, cz + s,  cx + s, cy + s, cz + s,  cx - s, cy + s, cz + s,
                cx - s, cy - s, cz - s,  cx + s, cy - s, cz - s,  cx + s, cy + s, cz - s,  cx - s, cy + s, cz - s
            )
            val colors = FloatArray(8 * 4).also { arr ->
                for (i in 0 until 8) {
                    arr[i * 4] = color[0]; arr[i * 4 + 1] = color[1]
                    arr[i * 4 + 2] = color[2]; arr[i * 4 + 3] = color[3]
                }
            }
            val idx = shortArrayOf(
                0, 1, 2, 0, 2, 3,  5, 4, 7, 5, 7, 6,  3, 2, 6, 3, 6, 7,
                4, 5, 1, 4, 1, 0,  1, 5, 6, 1, 6, 2,  4, 0, 3, 4, 3, 7
            )

            val vb = allocateFloatBuffer(verts)
            val cb = allocateFloatBuffer(colors)
            val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder())
                .asShortBuffer().apply { put(idx); position(0) }

            vb.position(0)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
            cb.position(0)
            GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)
            ib.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, ib)
        }
    }

    /**
     * Draw a faint reference grid on the XZ plane (unrotated) so the user can
     * see orientation relative to "world" space.
     */
    private fun drawReferenceGrid() {
        // Use identity model matrix (no rotation)
        Matrix.multiplyMM(tempMatrix, 0, vpMatrix, 0, FloatArray(16).also { Matrix.setIdentityM(it, 0) }, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, tempMatrix, 0)

        val gridSize = 2.0f
        val gridStep = 0.5f
        val lines = mutableListOf<Float>()
        val colors = mutableListOf<Float>()
        val gridColor = floatArrayOf(0.3f, 0.3f, 0.3f, 0.3f)

        var v = -gridSize
        while (v <= gridSize) {
            // Lines along X
            lines.addAll(listOf(-gridSize, 0f, v, gridSize, 0f, v))
            colors.addAll(gridColor.toList()); colors.addAll(gridColor.toList())
            // Lines along Z
            lines.addAll(listOf(v, 0f, -gridSize, v, 0f, gridSize))
            colors.addAll(gridColor.toList()); colors.addAll(gridColor.toList())
            v += gridStep
        }

        val vb = allocateFloatBuffer(lines.toFloatArray())
        val cb = allocateFloatBuffer(colors.toFloatArray())

        GLES20.glLineWidth(1.0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        vb.position(0)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
        cb.position(0)
        GLES20.glEnableVertexAttribArray(aColorHandle)
        GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, lines.size / 3)

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }

    private fun allocateFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer
            .allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }
}
