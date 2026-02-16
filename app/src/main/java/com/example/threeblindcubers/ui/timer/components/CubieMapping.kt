package com.example.threeblindcubers.ui.timer.components

/**
 * Maps each Kociemba facelet index (0-53) to its 3D cubie position and
 * the SceneView CubeNode face primitive that should receive its color.
 *
 * ## Coordinate System
 * - **X**: L(0) -> R(2)
 * - **Y**: D(0) -> U(2)
 * - **Z**: B(0) -> F(2)
 * - 26 visible cubies: all (x,y,z) in {0,1,2}^3 except (1,1,1)
 *
 * ## SceneView CubeNode Primitive Ordering (verified from source)
 * | Prim | Direction | Rubik's Face        |
 * |:----:|:---------:|:-------------------:|
 * |  0   | -Y (Bot)  | D (when y=0)        |
 * |  1   | -X (Left) | L (when x=0)        |
 * |  2   | +Z (Back*)| F (when z=2)        |
 * |  3   | -Z (Frt*) | B (when z=0)        |
 * |  4   | +X (Right)| R (when x=2)        |
 * |  5   | +Y (Top)  | U (when y=2)        |
 *
 * *Filament calls +Z "back" and -Z "front" — opposite of standard cube convention.
 */

/**
 * Specifies which cubie position and which CubeNode face primitive a
 * single Kociemba facelet index maps to.
 */
data class StickerMapping(
    val cubieX: Int,
    val cubieY: Int,
    val cubieZ: Int,
    val primitiveIndex: Int  // 0-5, which CubeNode face gets this color
)

/**
 * 54-element array: `KOCIEMBA_TO_CUBIE[kociembaIndex]` gives the cubie
 * position (x, y, z) and the CubeNode primitive index for that sticker.
 *
 * Derived from CubeStateTracker.kt Kociemba numbering and the coordinate
 * system / primitive mapping documented above.
 */
val KOCIEMBA_TO_CUBIE: Array<StickerMapping> = arrayOf(
    // ── U face (indices 0-8): y=2, primitive 5 (+Y/Top) ──
    // Kociemba U layout (looking down, F toward you):
    //   0 1 2   ← row 0 = back (z=0)
    //   3 4 5
    //   6 7 8   ← row 2 = front (z=2)
    // Columns: left(x=0) to right(x=2)
    /* 0 */ StickerMapping(0, 2, 0, 5),  // U top-left-back
    /* 1 */ StickerMapping(1, 2, 0, 5),  // U top-back
    /* 2 */ StickerMapping(2, 2, 0, 5),  // U top-right-back
    /* 3 */ StickerMapping(0, 2, 1, 5),  // U mid-left
    /* 4 */ StickerMapping(1, 2, 1, 5),  // U center
    /* 5 */ StickerMapping(2, 2, 1, 5),  // U mid-right
    /* 6 */ StickerMapping(0, 2, 2, 5),  // U front-left
    /* 7 */ StickerMapping(1, 2, 2, 5),  // U front-center
    /* 8 */ StickerMapping(2, 2, 2, 5),  // U front-right

    // ── R face (indices 9-17): x=2, primitive 4 (+X/Right) ──
    // Kociemba R layout (looking at right face from outside):
    //    9 10 11   ← row 0 = top (y=2)
    //   12 13 14
    //   15 16 17   ← row 2 = bottom (y=0)
    // Col 0 = front (z=2), Col 2 = back (z=0)
    /* 9  */ StickerMapping(2, 2, 2, 4),  // R top-front (URF)
    /* 10 */ StickerMapping(2, 2, 1, 4),  // R top-mid
    /* 11 */ StickerMapping(2, 2, 0, 4),  // R top-back (UBR)
    /* 12 */ StickerMapping(2, 1, 2, 4),  // R mid-front
    /* 13 */ StickerMapping(2, 1, 1, 4),  // R center
    /* 14 */ StickerMapping(2, 1, 0, 4),  // R mid-back
    /* 15 */ StickerMapping(2, 0, 2, 4),  // R bottom-front (DRF)
    /* 16 */ StickerMapping(2, 0, 1, 4),  // R bottom-mid
    /* 17 */ StickerMapping(2, 0, 0, 4),  // R bottom-back (DBR)

    // ── F face (indices 18-26): z=2, primitive 2 (+Z) ──
    // Kociemba F layout (looking at front face):
    //   18 19 20   ← row 0 = top (y=2)
    //   21 22 23
    //   24 25 26   ← row 2 = bottom (y=0)
    // Col 0 = left (x=0), Col 2 = right (x=2)
    /* 18 */ StickerMapping(0, 2, 2, 2),  // F top-left (UFL)
    /* 19 */ StickerMapping(1, 2, 2, 2),  // F top-center
    /* 20 */ StickerMapping(2, 2, 2, 2),  // F top-right (URF)
    /* 21 */ StickerMapping(0, 1, 2, 2),  // F mid-left
    /* 22 */ StickerMapping(1, 1, 2, 2),  // F center
    /* 23 */ StickerMapping(2, 1, 2, 2),  // F mid-right
    /* 24 */ StickerMapping(0, 0, 2, 2),  // F bottom-left (DFL)
    /* 25 */ StickerMapping(1, 0, 2, 2),  // F bottom-center
    /* 26 */ StickerMapping(2, 0, 2, 2),  // F bottom-right (DRF)

    // ── D face (indices 27-35): y=0, primitive 0 (-Y/Bottom) ──
    // Kociemba D layout (looking from below, F toward you):
    //   27 28 29   ← row 0 = front (z=2)
    //   30 31 32
    //   33 34 35   ← row 2 = back (z=0)
    // Col 0 = left (x=0), Col 2 = right (x=2)
    /* 27 */ StickerMapping(0, 0, 2, 0),  // D front-left (DFL)
    /* 28 */ StickerMapping(1, 0, 2, 0),  // D front-center
    /* 29 */ StickerMapping(2, 0, 2, 0),  // D front-right (DRF)
    /* 30 */ StickerMapping(0, 0, 1, 0),  // D mid-left
    /* 31 */ StickerMapping(1, 0, 1, 0),  // D center
    /* 32 */ StickerMapping(2, 0, 1, 0),  // D mid-right
    /* 33 */ StickerMapping(0, 0, 0, 0),  // D back-left (DLB)
    /* 34 */ StickerMapping(1, 0, 0, 0),  // D back-center
    /* 35 */ StickerMapping(2, 0, 0, 0),  // D back-right (DBR)

    // ── L face (indices 36-44): x=0, primitive 1 (-X/Left) ──
    // Kociemba L layout (looking at left face from outside):
    //   36 37 38   ← row 0 = top (y=2)
    //   39 40 41
    //   42 43 44   ← row 2 = bottom (y=0)
    // Col 0 = back (z=0), Col 2 = front (z=2)
    /* 36 */ StickerMapping(0, 2, 0, 1),  // L top-back (ULB)
    /* 37 */ StickerMapping(0, 2, 1, 1),  // L top-mid
    /* 38 */ StickerMapping(0, 2, 2, 1),  // L top-front (UFL)
    /* 39 */ StickerMapping(0, 1, 0, 1),  // L mid-back
    /* 40 */ StickerMapping(0, 1, 1, 1),  // L center
    /* 41 */ StickerMapping(0, 1, 2, 1),  // L mid-front
    /* 42 */ StickerMapping(0, 0, 0, 1),  // L bottom-back (DLB)
    /* 43 */ StickerMapping(0, 0, 1, 1),  // L bottom-mid
    /* 44 */ StickerMapping(0, 0, 2, 1),  // L bottom-front (DFL)

    // ── B face (indices 45-53): z=0, primitive 3 (-Z/Back) ──
    // Kociemba B layout (looking at back face from outside/behind):
    //   45 46 47   ← row 0 = top (y=2)
    //   48 49 50
    //   51 52 53   ← row 2 = bottom (y=0)
    // Col 0 = right (x=2), Col 2 = left (x=0)  [mirrored because looking from behind]
    /* 45 */ StickerMapping(2, 2, 0, 3),  // B top-right (UBR)
    /* 46 */ StickerMapping(1, 2, 0, 3),  // B top-center
    /* 47 */ StickerMapping(0, 2, 0, 3),  // B top-left (ULB)
    /* 48 */ StickerMapping(2, 1, 0, 3),  // B mid-right
    /* 49 */ StickerMapping(1, 1, 0, 3),  // B center
    /* 50 */ StickerMapping(0, 1, 0, 3),  // B mid-left
    /* 51 */ StickerMapping(2, 0, 0, 3),  // B bottom-right (DBR)
    /* 52 */ StickerMapping(1, 0, 0, 3),  // B bottom-center
    /* 53 */ StickerMapping(0, 0, 0, 3),  // B bottom-left (DLB)
)

/**
 * Builds a map from cubie position to a 6-element IntArray of color indices.
 *
 * Each cubie has 6 potential face slots (one per CubeNode primitive 0-5).
 * - External faces get the color from the cube state (0-5).
 * - Internal/non-visible faces get -1 (rendered as near-black).
 *
 * @param cubeState 54-element list: state[i] = color index (0-5)
 * @return Map from (x,y,z) -> IntArray(6) where [primIdx] = color index (-1 for internal)
 */
fun buildCubieFaceColors(cubeState: List<Int>): Map<Triple<Int, Int, Int>, IntArray> {
    // Initialize all 26 cubies with -1 (internal/black) on all faces
    val result = mutableMapOf<Triple<Int, Int, Int>, IntArray>()
    for (x in 0..2) {
        for (y in 0..2) {
            for (z in 0..2) {
                if (x == 1 && y == 1 && z == 1) continue  // skip hidden center
                result[Triple(x, y, z)] = IntArray(6) { -1 }
            }
        }
    }

    // Place each sticker's color into the correct cubie face slot
    for (kIdx in 0 until 54) {
        val mapping = KOCIEMBA_TO_CUBIE[kIdx]
        val key = Triple(mapping.cubieX, mapping.cubieY, mapping.cubieZ)
        val faceColors = result[key]
            ?: throw IllegalStateException("Cubie ($key) not found for Kociemba index $kIdx")
        faceColors[mapping.primitiveIndex] = cubeState[kIdx]
    }

    return result
}
