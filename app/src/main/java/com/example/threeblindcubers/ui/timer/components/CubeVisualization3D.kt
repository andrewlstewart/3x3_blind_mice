package com.example.threeblindcubers.ui.timer.components

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.example.threeblindcubers.domain.models.Face
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.Rotation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 3D Rubik's cube visualization using OpenGL ES 2.0.
 *
 * Renders all 26 visible cubies as colored boxes with touch-to-orbit rotation.
 * Unlike SceneView, GLSurfaceView renders immediately on surface creation
 * without requiring user interaction to trigger the first frame.
 *
 * @param cubeState 54-element list: state[i] = color index (0-5)
 *   Colors: 0=White(U), 1=Red(R), 2=Green(F), 3=Yellow(D), 4=Orange(L), 5=Blue(B)
 * @param lastMove the most recent quarter-turn move (for turn animation)
 * @param lastMoveId monotonically increasing ID to trigger animation on new moves
 */
@Composable
fun CubeVisualization3D(
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
    val renderer = remember { CubeRenderer() }

    // Match the app's background color so the GL viewport blends seamlessly
    val bgColor = MaterialTheme.colorScheme.background.toArgb()
    renderer.setBackgroundColor(
        ((bgColor shr 16) and 0xFF) / 255f,
        ((bgColor shr 8) and 0xFF) / 255f,
        (bgColor and 0xFF) / 255f
    )

    // Update cube state whenever it changes
    renderer.updateCubeState(cubeState)

    // Update IMU orientation
    renderer.setOrientation(orientationW, orientationX, orientationY, orientationZ)

    // Trigger turn animation when a new move arrives
    renderer.onMoveUpdate(lastMove, lastMoveId)

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
                    // Handle touch for orbit rotation + long-press for calibration.
                    // Request that the parent (scrollable Column) does NOT intercept
                    // touch events while the user is dragging on the cube.
                    val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    var longPressDownX = 0f
                    var longPressDownY = 0f
                    var longPressPending = false
                    val longPressRunnable = Runnable {
                        if (longPressPending) {
                            longPressPending = false
                            renderer.resetOrbit()
                            onLongPress?.invoke()
                        }
                    }
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                // Tell parent to stop intercepting touches
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                renderer.onTouchEvent(event)
                                // Start long-press detection
                                if (onLongPress != null) {
                                    longPressDownX = event.x
                                    longPressDownY = event.y
                                    longPressPending = true
                                    longPressHandler.postDelayed(longPressRunnable, 500L)
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                renderer.onTouchEvent(event)
                                // Cancel long-press if moved too far
                                if (longPressPending) {
                                    val dx = event.x - longPressDownX
                                    val dy = event.y - longPressDownY
                                    if (dx * dx + dy * dy > 10f * 10f) {
                                        longPressPending = false
                                        longPressHandler.removeCallbacks(longPressRunnable)
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                // Allow parent to intercept again
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                                renderer.onTouchEvent(event)
                                // Cancel any pending long-press
                                longPressPending = false
                                longPressHandler.removeCallbacks(longPressRunnable)
                            }
                        }
                        requestRender()
                        true
                    }
                }
            },
            update = { view ->
                view.requestRender()
            },
            modifier = Modifier.matchParentSize()
        )
    }
}

// ─── Color definitions ────────────────────────────────────────────────────────

/** Maps color index to RGB float arrays. */
private val CUBE_COLORS: Map<Int, FloatArray> = mapOf(
    -1 to floatArrayOf(0.05f, 0.05f, 0.05f),  // Internal/black
    0 to floatArrayOf(1.0f, 1.0f, 1.0f),       // White (U)
    1 to floatArrayOf(0.8f, 0.0f, 0.0f),       // Red (R)
    2 to floatArrayOf(0.0f, 0.6f, 0.0f),       // Green (F)
    3 to floatArrayOf(1.0f, 0.87f, 0.0f),      // Yellow (D)
    4 to floatArrayOf(1.0f, 0.53f, 0.0f),      // Orange (L)
    5 to floatArrayOf(0.0f, 0.27f, 0.8f)       // Blue (B)
)

// ─── Shaders ──────────────────────────────────────────────────────────────────

private const val VERTEX_SHADER = """
    uniform mat4 uMVPMatrix;
    uniform mat4 uModelMatrix;
    attribute vec4 aPosition;
    attribute vec3 aNormal;
    varying vec3 vNormal;
    varying vec3 vWorldPos;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        vWorldPos = vec3(uModelMatrix * aPosition);
        vNormal = normalize(vec3(uModelMatrix * vec4(aNormal, 0.0)));
    }
"""

private const val FRAGMENT_SHADER = """
    precision mediump float;
    uniform vec3 uColor;
    uniform vec3 uCameraPos;

    // Key light (bright, directional)
    uniform vec3 uKeyLightDir;
    uniform vec3 uKeyLightColor;
    // Fill light (softer, opposite side)
    uniform vec3 uFillLightDir;
    uniform vec3 uFillLightColor;
    // Rim/back light (edge highlights)
    uniform vec3 uRimLightDir;
    uniform vec3 uRimLightColor;
    // Top light (overhead, brightens the cube)
    uniform vec3 uTopLightDir;
    uniform vec3 uTopLightColor;

    varying vec3 vNormal;
    varying vec3 vWorldPos;

    void main() {
        vec3 N = normalize(vNormal);
        vec3 V = normalize(uCameraPos - vWorldPos);

        // ── Hemisphere ambient (simulates IBL environment lighting) ──
        // Bright from above, dimmer from below — like a photo studio
        vec3 skyColor = vec3(0.55, 0.55, 0.6);    // upper hemisphere
        vec3 groundColor = vec3(0.20, 0.20, 0.22); // lower hemisphere
        float hemi = 0.5 + 0.5 * N.y;  // 1.0 for up-facing, 0.0 for down-facing
        vec3 ambient = uColor * mix(groundColor, skyColor, hemi);

        // ── Key light (main directional) ──
        float keyDiff = max(dot(N, uKeyLightDir), 0.0);
        vec3 keyHalf = normalize(uKeyLightDir + V);
        float keySpec = pow(max(dot(N, keyHalf), 0.0), 64.0);
        vec3 key = uKeyLightColor * (uColor * keyDiff * 0.7 + vec3(1.0) * keySpec * 0.3);

        // ── Fill light (softer, from opposite side) ──
        float fillDiff = max(dot(N, uFillLightDir), 0.0);
        vec3 fillHalf = normalize(uFillLightDir + V);
        float fillSpec = pow(max(dot(N, fillHalf), 0.0), 32.0);
        vec3 fill = uFillLightColor * (uColor * fillDiff * 0.45 + vec3(1.0) * fillSpec * 0.08);

        // ── Top light (overhead, broad illumination) ──
        float topDiff = max(dot(N, uTopLightDir), 0.0);
        vec3 topHalf = normalize(uTopLightDir + V);
        float topSpec = pow(max(dot(N, topHalf), 0.0), 48.0);
        vec3 top = uTopLightColor * (uColor * topDiff * 0.5 + vec3(1.0) * topSpec * 0.12);

        // ── Rim light (back-edge highlights for depth) ──
        float rimDiff = max(dot(N, uRimLightDir), 0.0);
        vec3 rimHalf = normalize(uRimLightDir + V);
        float rimSpec = pow(max(dot(N, rimHalf), 0.0), 32.0);
        vec3 rim = uRimLightColor * (uColor * rimDiff * 0.2 + vec3(1.0) * rimSpec * 0.1);

        // ── Fresnel rim glow (subtle edge brightening, like plastic sheen) ──
        float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
        vec3 fresnelGlow = vec3(0.12) * fresnel;

        vec3 finalColor = ambient + key + fill + top + rim + fresnelGlow;
        // Gentle tone-map: Reinhard with higher white point so colors stay vivid
        finalColor = finalColor / (finalColor + vec3(0.8));
        // Rescale so mid-tones aren't compressed too much
        finalColor *= 1.25;

        gl_FragColor = vec4(finalColor, 1.0);
    }
"""

// ─── Cube geometry ────────────────────────────────────────────────────────────

/**
 * A unit cube centered at origin. Each face has its own vertices so normals
 * can be per-face (flat shading). 6 faces x 4 vertices = 24 vertices.
 * Each vertex: x, y, z, nx, ny, nz
 */
private val CUBE_VERTEX_DATA = floatArrayOf(
    // Front face (z = +0.5), normal (0,0,1)
    -0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
     0.5f, -0.5f,  0.5f,  0f, 0f, 1f,
     0.5f,  0.5f,  0.5f,  0f, 0f, 1f,
    -0.5f,  0.5f,  0.5f,  0f, 0f, 1f,
    // Back face (z = -0.5), normal (0,0,-1)
     0.5f, -0.5f, -0.5f,  0f, 0f,-1f,
    -0.5f, -0.5f, -0.5f,  0f, 0f,-1f,
    -0.5f,  0.5f, -0.5f,  0f, 0f,-1f,
     0.5f,  0.5f, -0.5f,  0f, 0f,-1f,
    // Top face (y = +0.5), normal (0,1,0)
    -0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
     0.5f,  0.5f,  0.5f,  0f, 1f, 0f,
     0.5f,  0.5f, -0.5f,  0f, 1f, 0f,
    -0.5f,  0.5f, -0.5f,  0f, 1f, 0f,
    // Bottom face (y = -0.5), normal (0,-1,0)
    -0.5f, -0.5f, -0.5f,  0f,-1f, 0f,
     0.5f, -0.5f, -0.5f,  0f,-1f, 0f,
     0.5f, -0.5f,  0.5f,  0f,-1f, 0f,
    -0.5f, -0.5f,  0.5f,  0f,-1f, 0f,
    // Right face (x = +0.5), normal (1,0,0)
     0.5f, -0.5f,  0.5f,  1f, 0f, 0f,
     0.5f, -0.5f, -0.5f,  1f, 0f, 0f,
     0.5f,  0.5f, -0.5f,  1f, 0f, 0f,
     0.5f,  0.5f,  0.5f,  1f, 0f, 0f,
    // Left face (x = -0.5), normal (-1,0,0)
    -0.5f, -0.5f, -0.5f, -1f, 0f, 0f,
    -0.5f, -0.5f,  0.5f, -1f, 0f, 0f,
    -0.5f,  0.5f,  0.5f, -1f, 0f, 0f,
    -0.5f,  0.5f, -0.5f, -1f, 0f, 0f
)

/** Index buffer: 6 faces x 2 triangles x 3 indices = 36 indices. */
private val CUBE_INDICES = shortArrayOf(
    0,1,2,  0,2,3,      // front
    4,5,6,  4,6,7,      // back
    8,9,10, 8,10,11,     // top
    12,13,14, 12,14,15,  // bottom
    16,17,18, 16,18,19,  // right
    20,21,22, 20,22,23   // left
)

/**
 * Maps our OpenGL face order to CubeNode primitive indices used by CubieMapping.
 *
 * Our face order (from CUBE_VERTEX_DATA / CUBE_INDICES):
 *   0: Front  (+Z) -> Rubik's F (z=2) -> CubieMapping prim 2
 *   1: Back   (-Z) -> Rubik's B (z=0) -> CubieMapping prim 3
 *   2: Top    (+Y) -> Rubik's U (y=2) -> CubieMapping prim 5
 *   3: Bottom (-Y) -> Rubik's D (y=0) -> CubieMapping prim 0
 *   4: Right  (+X) -> Rubik's R (x=2) -> CubieMapping prim 4
 *   5: Left   (-X) -> Rubik's L (x=0) -> CubieMapping prim 1
 */
private val GL_FACE_TO_PRIMITIVE = intArrayOf(2, 3, 5, 0, 4, 1)

// ─── Renderer ─────────────────────────────────────────────────────────────────

/**
 * OpenGL ES 2.0 renderer for a Rubik's cube.
 * Renders 26 cubies with per-face coloring based on cube state.
 * Features a 3-point lighting model (key, fill, rim) with specular highlights
 * and Fresnel rim glow for a realistic plastic look.
 * Supports animated layer rotations (~100ms) when moves are applied.
 */
internal class CubeRenderer : GLSurfaceView.Renderer {

    // Orbit angles (degrees)
    @Volatile private var rotationX = 25f   // pitch
    @Volatile private var rotationY = 35f   // yaw (positive = view from right, showing R/red face)

    // Touch tracking
    private var previousX = 0f
    private var previousY = 0f
    private var isDragging = false

    // Background color (set from Compose theme)
    @Volatile private var bgR = 0f
    @Volatile private var bgG = 0f
    @Volatile private var bgB = 0f
    @Volatile private var bgDirty = true

    // IMU orientation quaternion
    @Volatile private var oW = 1f
    @Volatile private var oX = 0f
    @Volatile private var oY = 0f
    @Volatile private var oZ = 0f

    // Cube state: map from (x,y,z) -> 6-element color index array
    @Volatile
    private var cubieFaceColors: Map<Triple<Int, Int, Int>, IntArray> = emptyMap()

    // ── Turn animation state ──
    private companion object {
        const val ANIMATION_DURATION_NS = 100_000_000L  // 100ms in nanoseconds
    }

    // Pre-move cubie colors for the animated layer (snapshot before state update)
    @Volatile private var animPreMoveColors: Map<Triple<Int, Int, Int>, IntArray>? = null
    // Which cubies are in the animated layer (filter by coordinate)
    @Volatile private var animLayerFilter: ((Triple<Int, Int, Int>) -> Boolean)? = null
    // Rotation axis for the animation (0=X, 1=Y, 2=Z)
    @Volatile private var animAxis: Int = 0
    // Target rotation angle in degrees (±90)
    @Volatile private var animTargetDegrees: Float = 0f
    // Animation start time (System.nanoTime)
    @Volatile private var animStartNanos: Long = 0L
    // Whether animation is currently active
    @Volatile private var animActive: Boolean = false
    // Last move ID we've seen (to detect new moves)
    @Volatile private var lastSeenMoveId: Int = 0

    // OpenGL handles
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: java.nio.ShortBuffer? = null
    private var uMVPMatrixHandle = 0
    private var uModelMatrixHandle = 0
    private var uColorHandle = 0
    private var uCameraPosHandle = 0
    private var uKeyLightDirHandle = 0
    private var uKeyLightColorHandle = 0
    private var uFillLightDirHandle = 0
    private var uFillLightColorHandle = 0
    private var uRimLightDirHandle = 0
    private var uRimLightColorHandle = 0
    private var uTopLightDirHandle = 0
    private var uTopLightColorHandle = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0

    // Matrices
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val orientationMatrix = FloatArray(16)
    private val vpOrientedMatrix = FloatArray(16)
    private val animRotMatrix = FloatArray(16)  // layer rotation during animation
    private val tempMatrix = FloatArray(16)     // scratch matrix

    fun setBackgroundColor(r: Float, g: Float, b: Float) {
        bgR = r
        bgG = g
        bgB = b
        bgDirty = true
    }

    fun updateCubeState(cubeState: List<Int>) {
        cubieFaceColors = buildCubieFaceColors(cubeState)
    }

    fun setOrientation(w: Float, x: Float, y: Float, z: Float) {
        oW = w; oX = x; oY = y; oZ = z
    }

    /** Resets the touch-drag orbit angles back to the default iso view. */
    fun resetOrbit() {
        rotationX = 25f
        rotationY = 35f
    }

    /**
     * Called from Compose recomposition when lastMove/lastMoveId changes.
     * Snapshots the CURRENT cubie colors (which are the POST-move state)
     * and starts an animation that rotates the affected layer from the
     * pre-move position to the post-move position.
     *
     * The trick: cubeState is already updated by the ViewModel, so all cubies
     * show the final colors. During animation, we render the affected layer's
     * cubies with an INVERSE rotation that starts at -targetDegrees (pre-move
     * position) and animates to 0 (final position). This avoids needing to
     * store pre-move state separately.
     */
    fun onMoveUpdate(move: Move?, moveId: Int) {
        if (move == null || moveId == lastSeenMoveId) return
        lastSeenMoveId = moveId

        // Determine which layer and rotation from the move
        val layerInfo = getMoveLayerInfo(move) ?: return

        animLayerFilter = layerInfo.filter
        animAxis = layerInfo.axis
        animTargetDegrees = layerInfo.degrees
        animStartNanos = System.nanoTime()
        animActive = true
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - previousX
                    val dy = event.y - previousY
                    rotationY -= dx * 0.4f  // negate so drag-right orbits right
                    rotationX += dy * 0.4f
                    // Clamp pitch to avoid flipping
                    rotationX = rotationX.coerceIn(-89f, 89f)
                    previousX = event.x
                    previousY = event.y
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // Compile shaders and link program
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Get attribute/uniform locations
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uColorHandle = GLES20.glGetUniformLocation(program, "uColor")
        uCameraPosHandle = GLES20.glGetUniformLocation(program, "uCameraPos")
        uKeyLightDirHandle = GLES20.glGetUniformLocation(program, "uKeyLightDir")
        uKeyLightColorHandle = GLES20.glGetUniformLocation(program, "uKeyLightColor")
        uFillLightDirHandle = GLES20.glGetUniformLocation(program, "uFillLightDir")
        uFillLightColorHandle = GLES20.glGetUniformLocation(program, "uFillLightColor")
        uRimLightDirHandle = GLES20.glGetUniformLocation(program, "uRimLightDir")
        uRimLightColorHandle = GLES20.glGetUniformLocation(program, "uRimLightColor")
        uTopLightDirHandle = GLES20.glGetUniformLocation(program, "uTopLightDir")
        uTopLightColorHandle = GLES20.glGetUniformLocation(program, "uTopLightColor")

        // Create vertex buffer
        vertexBuffer = ByteBuffer
            .allocateDirect(CUBE_VERTEX_DATA.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(CUBE_VERTEX_DATA)
                position(0)
            }

        // Create index buffer
        indexBuffer = ByteBuffer
            .allocateDirect(CUBE_INDICES.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(CUBE_INDICES)
                position(0)
            }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 35f, ratio, 1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Update background color if theme changed
        if (bgDirty) {
            GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
            bgDirty = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Camera: orbit around origin using spherical coordinates
        val camDist = 8.5f
        val radX = Math.toRadians(rotationX.toDouble())
        val radY = Math.toRadians(rotationY.toDouble())
        val camX = (camDist * Math.cos(radX) * Math.sin(radY)).toFloat()
        val camY = (camDist * Math.sin(radX)).toFloat()
        val camZ = (camDist * Math.cos(radX) * Math.cos(radY)).toFloat()

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,   // eye
            0f, 0f, 0f,         // center
            0f, 1f, 0f          // up
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Build orientation matrix from IMU quaternion and compose with VP.
        // The whole cube rotates by the IMU orientation, then the user can still orbit.
        // VP_oriented = VP * orientation
        quaternionToMatrix(oW, oX, oY, oZ, orientationMatrix)
        Matrix.multiplyMM(vpOrientedMatrix, 0, vpMatrix, 0, orientationMatrix, 0)

        GLES20.glUseProgram(program)

        // Pass camera position for specular calculations
        GLES20.glUniform3f(uCameraPosHandle, camX, camY, camZ)

        // ── 4-point lighting setup ──

        // Key light: bright, from upper-right-front
        val keyDir = normalize(0.5f, 0.9f, 0.7f)
        GLES20.glUniform3f(uKeyLightDirHandle, keyDir[0], keyDir[1], keyDir[2])
        GLES20.glUniform3f(uKeyLightColorHandle, 1.0f, 0.98f, 0.95f) // warm white

        // Fill light: from lower-left-front (fills shadows on opposite side)
        val fillDir = normalize(-0.6f, 0.3f, 0.5f)
        GLES20.glUniform3f(uFillLightDirHandle, fillDir[0], fillDir[1], fillDir[2])
        GLES20.glUniform3f(uFillLightColorHandle, 0.85f, 0.88f, 1.0f) // cool blue-white

        // Top light: nearly straight down, broad overhead illumination
        val topDir = normalize(0.05f, 1.0f, 0.1f)
        GLES20.glUniform3f(uTopLightDirHandle, topDir[0], topDir[1], topDir[2])
        GLES20.glUniform3f(uTopLightColorHandle, 1.0f, 1.0f, 1.0f) // pure white

        // Rim light: from behind-below to create edge definition
        val rimDir = normalize(-0.3f, -0.5f, -0.8f)
        GLES20.glUniform3f(uRimLightDirHandle, rimDir[0], rimDir[1], rimDir[2])
        GLES20.glUniform3f(uRimLightColorHandle, 0.6f, 0.6f, 0.7f) // subtle cool

        // Bind vertex data
        val stride = 6 * 4 // 6 floats per vertex, 4 bytes per float
        vertexBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer?.position(3)
        GLES20.glEnableVertexAttribArray(aNormalHandle)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        // ── Compute animation progress ──
        val layerFilter = animLayerFilter
        var animAngle = 0f
        val isAnimating: Boolean
        if (animActive && layerFilter != null) {
            val elapsed = System.nanoTime() - animStartNanos
            if (elapsed >= ANIMATION_DURATION_NS) {
                // Animation complete
                animActive = false
                isAnimating = false
            } else {
                // Smooth ease-out interpolation: 1 - (1-t)^2
                val t = elapsed.toFloat() / ANIMATION_DURATION_NS.toFloat()
                val eased = 1f - (1f - t) * (1f - t)
                // Animate from -target to 0 (i.e., the layer starts rotated back
                // and arrives at the final position)
                animAngle = animTargetDegrees * (eased - 1f)
                isAnimating = true
            }
        } else {
            isAnimating = false
        }

        // Build the layer rotation matrix if animating
        if (isAnimating) {
            Matrix.setIdentityM(animRotMatrix, 0)
            when (animAxis) {
                0 -> Matrix.rotateM(animRotMatrix, 0, animAngle, 1f, 0f, 0f)
                1 -> Matrix.rotateM(animRotMatrix, 0, animAngle, 0f, 1f, 0f)
                2 -> Matrix.rotateM(animRotMatrix, 0, animAngle, 0f, 0f, 1f)
            }
        }

        // Draw each cubie
        val cubies = cubieFaceColors
        val cubieSize = 0.9f
        val gap = 1.05f  // slight gap between cubies

        for ((pos, faceColors) in cubies) {
            val (cx, cy, cz) = pos
            // Center the cube: positions 0,1,2 -> -1.05, 0, 1.05
            val worldX = (cx - 1) * gap
            val worldY = (cy - 1) * gap
            val worldZ = (cz - 1) * gap

            // Check if this cubie is in the animated layer
            val inAnimLayer = isAnimating && layerFilter != null && layerFilter(pos)

            // Draw each face with its own color
            for (faceIdx in 0 until 6) {
                val primIdx = GL_FACE_TO_PRIMITIVE[faceIdx]
                val colorIdx = faceColors[primIdx]
                val rgb = CUBE_COLORS[colorIdx] ?: CUBE_COLORS[-1]!!

                // Model matrix: translate and scale
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, worldX, worldY, worldZ)
                Matrix.scaleM(modelMatrix, 0, cubieSize, cubieSize, cubieSize)

                if (inAnimLayer) {
                    // For animated cubies: VP_oriented * animRot * model
                    Matrix.multiplyMM(tempMatrix, 0, animRotMatrix, 0, modelMatrix, 0)
                    Matrix.multiplyMM(mvpMatrix, 0, vpOrientedMatrix, 0, tempMatrix, 0)
                    // Model matrix for lighting also needs the rotation
                    Matrix.multiplyMM(tempMatrix, 0, animRotMatrix, 0, modelMatrix, 0)
                    GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, tempMatrix, 0)
                } else {
                    // Normal: VP_oriented * model
                    Matrix.multiplyMM(mvpMatrix, 0, vpOrientedMatrix, 0, modelMatrix, 0)
                    GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
                }

                GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
                GLES20.glUniform3fv(uColorHandle, 1, rgb, 0)

                // Draw just this face (2 triangles = 6 indices starting at faceIdx*6)
                indexBuffer?.position(faceIdx * 6)
                GLES20.glDrawElements(
                    GLES20.GL_TRIANGLES, 6,
                    GLES20.GL_UNSIGNED_SHORT, indexBuffer
                )
            }
        }

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }

    /** Normalize a direction vector in-place and return as FloatArray. */
    private fun normalize(x: Float, y: Float, z: Float): FloatArray {
        val len = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        return floatArrayOf(x / len, y / len, z / len)
    }

    /**
     * Build a 4x4 rotation matrix from a unit quaternion.
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
}

// ─── Move → Layer mapping ────────────────────────────────────────────────────

/**
 * Describes which layer of cubies to animate and how.
 * @param filter predicate: true if a cubie at (x,y,z) is in the layer
 * @param axis rotation axis index: 0=X, 1=Y, 2=Z
 * @param degrees rotation amount in degrees (positive = CW when looking
 *   from the positive end of the axis toward origin, per right-hand rule)
 */
private data class MoveLayerInfo(
    val filter: (Triple<Int, Int, Int>) -> Boolean,
    val axis: Int,
    val degrees: Float
)

/**
 * Maps a cube Move to the layer filter, rotation axis, and angle.
 *
 * Coordinate system (from CubieMapping):
 *   X: L(0) → R(2)
 *   Y: D(0) → U(2)
 *   Z: B(0) → F(2)
 *
 * Standard cube rotation conventions (looking from positive axis toward origin):
 *   R (x=2, around +X): CW = -90° in OpenGL  (top goes to front)
 *   L (x=0, around +X): CW = +90° in OpenGL  (opposite sense)
 *   U (y=2, around +Y): CW = -90° in OpenGL  (front goes to right)
 *   D (y=0, around +Y): CW = +90° in OpenGL
 *   F (z=2, around +Z): CW = -90° in OpenGL  (top goes to right)
 *   B (z=0, around +Z): CW = +90° in OpenGL
 */
private fun getMoveLayerInfo(move: Move): MoveLayerInfo? {
    val dir = when (move.rotation) {
        Rotation.CLOCKWISE -> 1f
        Rotation.COUNTER_CLOCKWISE -> -1f
        Rotation.DOUBLE -> return null  // doubles are expanded to quarter turns by ViewModel
    }

    return when (move.face) {
        Face.R -> MoveLayerInfo(
            filter = { it.first == 2 },
            axis = 0,
            degrees = -90f * dir
        )
        Face.L -> MoveLayerInfo(
            filter = { it.first == 0 },
            axis = 0,
            degrees = 90f * dir
        )
        Face.U -> MoveLayerInfo(
            filter = { it.second == 2 },
            axis = 1,
            degrees = -90f * dir
        )
        Face.D -> MoveLayerInfo(
            filter = { it.second == 0 },
            axis = 1,
            degrees = 90f * dir
        )
        Face.F -> MoveLayerInfo(
            filter = { it.third == 2 },
            axis = 2,
            degrees = -90f * dir
        )
        Face.B -> MoveLayerInfo(
            filter = { it.third == 0 },
            axis = 2,
            degrees = 90f * dir
        )
    }
}
