package com.example.threeblindcubers.ui.timer.components

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the CubieMapping module.
 *
 * Verifies that the KOCIEMBA_TO_CUBIE mapping and buildCubieFaceColors
 * correctly translate between Kociemba facelet indices and 3D cubie positions.
 */
class CubieMappingTest {

    private val solvedState = List(54) { it / 9 }

    // =========================================================
    // Test 1: All 54 indices have valid cubie positions
    // =========================================================

    @Test
    fun `all 54 indices map to valid cubie positions`() {
        assertEquals("KOCIEMBA_TO_CUBIE should have 54 entries", 54, KOCIEMBA_TO_CUBIE.size)

        for (i in 0 until 54) {
            val m = KOCIEMBA_TO_CUBIE[i]
            assertTrue("idx $i: cubieX=${m.cubieX} out of range", m.cubieX in 0..2)
            assertTrue("idx $i: cubieY=${m.cubieY} out of range", m.cubieY in 0..2)
            assertTrue("idx $i: cubieZ=${m.cubieZ} out of range", m.cubieZ in 0..2)
            assertFalse(
                "idx $i: maps to hidden center (1,1,1)",
                m.cubieX == 1 && m.cubieY == 1 && m.cubieZ == 1
            )
            assertTrue(
                "idx $i: primitiveIndex=${m.primitiveIndex} out of range",
                m.primitiveIndex in 0..5
            )
        }
    }

    // =========================================================
    // Test 2: No duplicate sticker mappings
    // =========================================================

    @Test
    fun `no two Kociemba indices map to same cubie and primitive`() {
        val seen = mutableSetOf<String>()
        for (i in 0 until 54) {
            val m = KOCIEMBA_TO_CUBIE[i]
            val key = "${m.cubieX},${m.cubieY},${m.cubieZ},${m.primitiveIndex}"
            assertTrue(
                "Duplicate mapping at idx $i: $key already used",
                seen.add(key)
            )
        }
    }

    // =========================================================
    // Test 3: Correct sticker counts per cubie type
    // =========================================================

    @Test
    fun `corners have exactly 3 stickers`() {
        val cornerPositions = listOf(
            Triple(0, 0, 0), Triple(2, 0, 0), Triple(0, 2, 0), Triple(2, 2, 0),
            Triple(0, 0, 2), Triple(2, 0, 2), Triple(0, 2, 2), Triple(2, 2, 2)
        )
        for (pos in cornerPositions) {
            val count = KOCIEMBA_TO_CUBIE.count {
                it.cubieX == pos.first && it.cubieY == pos.second && it.cubieZ == pos.third
            }
            assertEquals("Corner $pos should have 3 stickers", 3, count)
        }
    }

    @Test
    fun `edges have exactly 2 stickers`() {
        val edgePositions = listOf(
            // U-layer edges
            Triple(1, 2, 0), Triple(2, 2, 1), Triple(1, 2, 2), Triple(0, 2, 1),
            // Middle-layer edges
            Triple(0, 1, 2), Triple(2, 1, 2), Triple(0, 1, 0), Triple(2, 1, 0),
            // D-layer edges
            Triple(1, 0, 2), Triple(2, 0, 1), Triple(1, 0, 0), Triple(0, 0, 1)
        )
        for (pos in edgePositions) {
            val count = KOCIEMBA_TO_CUBIE.count {
                it.cubieX == pos.first && it.cubieY == pos.second && it.cubieZ == pos.third
            }
            assertEquals("Edge $pos should have 2 stickers", 2, count)
        }
    }

    @Test
    fun `centers have exactly 1 sticker`() {
        val centerPositions = listOf(
            Triple(1, 2, 1), // U center
            Triple(2, 1, 1), // R center
            Triple(1, 1, 2), // F center
            Triple(1, 0, 1), // D center
            Triple(0, 1, 1), // L center
            Triple(1, 1, 0)  // B center
        )
        for (pos in centerPositions) {
            val count = KOCIEMBA_TO_CUBIE.count {
                it.cubieX == pos.first && it.cubieY == pos.second && it.cubieZ == pos.third
            }
            assertEquals("Center $pos should have 1 sticker", 1, count)
        }
    }

    @Test
    fun `total sticker count is 54`() {
        // 8 corners * 3 + 12 edges * 2 + 6 centers * 1 = 24 + 24 + 6 = 54
        val allMappings = KOCIEMBA_TO_CUBIE.size
        assertEquals("Total sticker count", 54, allMappings)
    }

    // =========================================================
    // Test 4: Solved state colors on correct primitives
    // =========================================================

    @Test
    fun `solved state - U face cubies have color 0 on primitive 5`() {
        val colors = buildCubieFaceColors(solvedState)
        // All cubies with y=2 should have color 0 (white) on primitive 5 (+Y/Top)
        for (x in 0..2) for (z in 0..2) {
            val faceColors = colors[Triple(x, 2, z)]!!
            assertEquals(
                "Cubie ($x,2,$z) prim 5 should be 0 (white)",
                0, faceColors[5]
            )
        }
    }

    @Test
    fun `solved state - R face cubies have color 1 on primitive 4`() {
        val colors = buildCubieFaceColors(solvedState)
        // All cubies with x=2 should have color 1 (red) on primitive 4 (+X/Right)
        for (y in 0..2) for (z in 0..2) {
            val faceColors = colors[Triple(2, y, z)]!!
            assertEquals(
                "Cubie (2,$y,$z) prim 4 should be 1 (red)",
                1, faceColors[4]
            )
        }
    }

    @Test
    fun `solved state - F face cubies have color 2 on primitive 2`() {
        val colors = buildCubieFaceColors(solvedState)
        // All cubies with z=2 should have color 2 (green) on primitive 2 (+Z)
        for (x in 0..2) for (y in 0..2) {
            val faceColors = colors[Triple(x, y, 2)]!!
            assertEquals(
                "Cubie ($x,$y,2) prim 2 should be 2 (green)",
                2, faceColors[2]
            )
        }
    }

    @Test
    fun `solved state - D face cubies have color 3 on primitive 0`() {
        val colors = buildCubieFaceColors(solvedState)
        // All cubies with y=0 should have color 3 (yellow) on primitive 0 (-Y/Bottom)
        for (x in 0..2) for (z in 0..2) {
            val faceColors = colors[Triple(x, 0, z)]!!
            assertEquals(
                "Cubie ($x,0,$z) prim 0 should be 3 (yellow)",
                3, faceColors[0]
            )
        }
    }

    @Test
    fun `solved state - L face cubies have color 4 on primitive 1`() {
        val colors = buildCubieFaceColors(solvedState)
        // All cubies with x=0 should have color 4 (orange) on primitive 1 (-X/Left)
        for (y in 0..2) for (z in 0..2) {
            val faceColors = colors[Triple(0, y, z)]!!
            assertEquals(
                "Cubie (0,$y,$z) prim 1 should be 4 (orange)",
                4, faceColors[1]
            )
        }
    }

    @Test
    fun `solved state - B face cubies have color 5 on primitive 3`() {
        val colors = buildCubieFaceColors(solvedState)
        // All cubies with z=0 should have color 5 (blue) on primitive 3 (-Z/Back)
        for (x in 0..2) for (y in 0..2) {
            val faceColors = colors[Triple(x, y, 0)]!!
            assertEquals(
                "Cubie ($x,$y,0) prim 3 should be 5 (blue)",
                5, faceColors[3]
            )
        }
    }

    @Test
    fun `solved state - internal faces are -1`() {
        val colors = buildCubieFaceColors(solvedState)
        // Check a few internal faces that shouldn't have stickers
        // Center cubie (1,2,1) only has U face (prim 5), all others should be -1
        val uCenter = colors[Triple(1, 2, 1)]!!
        assertEquals("U center prim 5 should be 0", 0, uCenter[5])
        assertEquals("U center prim 0 should be -1", -1, uCenter[0])
        assertEquals("U center prim 1 should be -1", -1, uCenter[1])
        assertEquals("U center prim 2 should be -1", -1, uCenter[2])
        assertEquals("U center prim 3 should be -1", -1, uCenter[3])
        assertEquals("U center prim 4 should be -1", -1, uCenter[4])

        // Edge cubie (1,2,0) has U face (prim 5) and B face (prim 3), rest -1
        val ubEdge = colors[Triple(1, 2, 0)]!!
        assertEquals("UB edge prim 5 should be 0", 0, ubEdge[5])
        assertEquals("UB edge prim 3 should be 5", 5, ubEdge[3])
        assertEquals("UB edge prim 0 should be -1", -1, ubEdge[0])
        assertEquals("UB edge prim 1 should be -1", -1, ubEdge[1])
        assertEquals("UB edge prim 2 should be -1", -1, ubEdge[2])
        assertEquals("UB edge prim 4 should be -1", -1, ubEdge[4])
    }

    // =========================================================
    // Test 5: Cross-validation with 2D grid mapping
    // =========================================================

    @Test
    fun `3D mapping matches 2D getTopFaceGrid for y=2 cubies`() {
        val colors3D = buildCubieFaceColors(solvedState)
        val grid2D = getTopFaceGrid(solvedState)

        // 2D grid: visRow=0 = front (z=2), visRow=2 = back (z=0)
        //          visCol=0 = left (x=0),  visCol=2 = right (x=2)
        for (visRow in 0..2) {
            for (visCol in 0..2) {
                val x = visCol
                val z = 2 - visRow
                val color2D = grid2D[visRow][visCol]
                val color3D = colors3D[Triple(x, 2, z)]!![5]  // prim 5 = +Y/Top = U face
                assertEquals(
                    "Top face visRow=$visRow visCol=$visCol: 2D=$color2D vs 3D=$color3D",
                    color2D, color3D
                )
            }
        }
    }

    @Test
    fun `3D mapping matches 2D getFrontFaceGrid for z=2 cubies`() {
        val colors3D = buildCubieFaceColors(solvedState)
        val grid2D = getFrontFaceGrid(solvedState)

        // 2D grid: visRow=0 = bottom (y=0), visRow=2 = top (y=2)
        //          visCol=0 = left (x=0),   visCol=2 = right (x=2)
        for (visRow in 0..2) {
            for (visCol in 0..2) {
                val x = visCol
                val y = visRow
                val color2D = grid2D[visRow][visCol]
                val color3D = colors3D[Triple(x, y, 2)]!![2]  // prim 2 = +Z = F face
                assertEquals(
                    "Front face visRow=$visRow visCol=$visCol: 2D=$color2D vs 3D=$color3D",
                    color2D, color3D
                )
            }
        }
    }

    @Test
    fun `3D mapping matches 2D getRightFaceGrid for x=2 cubies`() {
        val colors3D = buildCubieFaceColors(solvedState)
        val grid2D = getRightFaceGrid(solvedState)

        // 2D grid: visRow=0 = bottom (y=0), visRow=2 = top (y=2)
        //          visCol=0 = front (z=2),  visCol=2 = back (z=0)
        for (visRow in 0..2) {
            for (visCol in 0..2) {
                val y = visRow
                val z = 2 - visCol
                val color2D = grid2D[visRow][visCol]
                val color3D = colors3D[Triple(2, y, z)]!![4]  // prim 4 = +X = R face
                assertEquals(
                    "Right face visRow=$visRow visCol=$visCol: 2D=$color2D vs 3D=$color3D",
                    color2D, color3D
                )
            }
        }
    }

    // =========================================================
    // Test 6: Corner adjacency verification
    // =========================================================

    @Test
    fun `corner URF (2,2,2) has stickers from U idx 8, R idx 9, F idx 20`() {
        // Cubie (2,2,2) = URF corner
        // Should have:
        //   idx 8  -> prim 5 (U face, +Y)
        //   idx 9  -> prim 4 (R face, +X)
        //   idx 20 -> prim 2 (F face, +Z)
        val m8 = KOCIEMBA_TO_CUBIE[8]
        assertEquals("idx 8 cubieX", 2, m8.cubieX)
        assertEquals("idx 8 cubieY", 2, m8.cubieY)
        assertEquals("idx 8 cubieZ", 2, m8.cubieZ)
        assertEquals("idx 8 prim", 5, m8.primitiveIndex)

        val m9 = KOCIEMBA_TO_CUBIE[9]
        assertEquals("idx 9 cubieX", 2, m9.cubieX)
        assertEquals("idx 9 cubieY", 2, m9.cubieY)
        assertEquals("idx 9 cubieZ", 2, m9.cubieZ)
        assertEquals("idx 9 prim", 4, m9.primitiveIndex)

        val m20 = KOCIEMBA_TO_CUBIE[20]
        assertEquals("idx 20 cubieX", 2, m20.cubieX)
        assertEquals("idx 20 cubieY", 2, m20.cubieY)
        assertEquals("idx 20 cubieZ", 2, m20.cubieZ)
        assertEquals("idx 20 prim", 2, m20.primitiveIndex)
    }

    @Test
    fun `corner ULB (0,2,0) has stickers from U idx 0, L idx 36, B idx 47`() {
        // Cubie (0,2,0) = ULB corner (the OP corner buffer)
        val m0 = KOCIEMBA_TO_CUBIE[0]
        assertEquals("idx 0 cubieX", 0, m0.cubieX)
        assertEquals("idx 0 cubieY", 2, m0.cubieY)
        assertEquals("idx 0 cubieZ", 0, m0.cubieZ)
        assertEquals("idx 0 prim", 5, m0.primitiveIndex)

        val m36 = KOCIEMBA_TO_CUBIE[36]
        assertEquals("idx 36 cubieX", 0, m36.cubieX)
        assertEquals("idx 36 cubieY", 2, m36.cubieY)
        assertEquals("idx 36 cubieZ", 0, m36.cubieZ)
        assertEquals("idx 36 prim", 1, m36.primitiveIndex)

        val m47 = KOCIEMBA_TO_CUBIE[47]
        assertEquals("idx 47 cubieX", 0, m47.cubieX)
        assertEquals("idx 47 cubieY", 2, m47.cubieY)
        assertEquals("idx 47 cubieZ", 0, m47.cubieZ)
        assertEquals("idx 47 prim", 3, m47.primitiveIndex)
    }

    @Test
    fun `corner DRF (2,0,2) has stickers from D idx 29, R idx 15, F idx 26`() {
        // Cubie (2,0,2) = DRF corner (the OP corner swap target)
        val m29 = KOCIEMBA_TO_CUBIE[29]
        assertEquals("idx 29 cubieX", 2, m29.cubieX)
        assertEquals("idx 29 cubieY", 0, m29.cubieY)
        assertEquals("idx 29 cubieZ", 2, m29.cubieZ)
        assertEquals("idx 29 prim", 0, m29.primitiveIndex)

        val m15 = KOCIEMBA_TO_CUBIE[15]
        assertEquals("idx 15 cubieX", 2, m15.cubieX)
        assertEquals("idx 15 cubieY", 0, m15.cubieY)
        assertEquals("idx 15 cubieZ", 2, m15.cubieZ)
        assertEquals("idx 15 prim", 4, m15.primitiveIndex)

        val m26 = KOCIEMBA_TO_CUBIE[26]
        assertEquals("idx 26 cubieX", 2, m26.cubieX)
        assertEquals("idx 26 cubieY", 0, m26.cubieY)
        assertEquals("idx 26 cubieZ", 2, m26.cubieZ)
        assertEquals("idx 26 prim", 2, m26.primitiveIndex)
    }

    @Test
    fun `corner DLB (0,0,0) has stickers from D idx 33, L idx 42, B idx 53`() {
        // Cubie (0,0,0) = DLB corner
        val m33 = KOCIEMBA_TO_CUBIE[33]
        assertEquals("idx 33 cubieX", 0, m33.cubieX)
        assertEquals("idx 33 cubieY", 0, m33.cubieY)
        assertEquals("idx 33 cubieZ", 0, m33.cubieZ)
        assertEquals("idx 33 prim", 0, m33.primitiveIndex)

        val m42 = KOCIEMBA_TO_CUBIE[42]
        assertEquals("idx 42 cubieX", 0, m42.cubieX)
        assertEquals("idx 42 cubieY", 0, m42.cubieY)
        assertEquals("idx 42 cubieZ", 0, m42.cubieZ)
        assertEquals("idx 42 prim", 1, m42.primitiveIndex)

        val m53 = KOCIEMBA_TO_CUBIE[53]
        assertEquals("idx 53 cubieX", 0, m53.cubieX)
        assertEquals("idx 53 cubieY", 0, m53.cubieY)
        assertEquals("idx 53 cubieZ", 0, m53.cubieZ)
        assertEquals("idx 53 prim", 3, m53.primitiveIndex)
    }

    // =========================================================
    // Test: Edge adjacency verification
    // =========================================================

    @Test
    fun `edge UR (2,2,1) has stickers from U idx 5, R idx 10`() {
        // Cubie (2,2,1) = UR edge (the OP edge buffer)
        val m5 = KOCIEMBA_TO_CUBIE[5]
        assertEquals("idx 5 cubieX", 2, m5.cubieX)
        assertEquals("idx 5 cubieY", 2, m5.cubieY)
        assertEquals("idx 5 cubieZ", 1, m5.cubieZ)
        assertEquals("idx 5 prim", 5, m5.primitiveIndex)

        val m10 = KOCIEMBA_TO_CUBIE[10]
        assertEquals("idx 10 cubieX", 2, m10.cubieX)
        assertEquals("idx 10 cubieY", 2, m10.cubieY)
        assertEquals("idx 10 cubieZ", 1, m10.cubieZ)
        assertEquals("idx 10 prim", 4, m10.primitiveIndex)
    }

    // =========================================================
    // Test: buildCubieFaceColors returns exactly 26 cubies
    // =========================================================

    @Test
    fun `buildCubieFaceColors returns exactly 26 cubies`() {
        val colors = buildCubieFaceColors(solvedState)
        assertEquals("Should have 26 cubies", 26, colors.size)
        assertNull(
            "Center (1,1,1) should not be in map",
            colors[Triple(1, 1, 1)]
        )
    }

    // =========================================================
    // Test: Primitive indices match face positions
    // =========================================================

    @Test
    fun `U face stickers all use primitive 5`() {
        for (i in 0..8) {
            assertEquals("idx $i should use prim 5", 5, KOCIEMBA_TO_CUBIE[i].primitiveIndex)
        }
    }

    @Test
    fun `R face stickers all use primitive 4`() {
        for (i in 9..17) {
            assertEquals("idx $i should use prim 4", 4, KOCIEMBA_TO_CUBIE[i].primitiveIndex)
        }
    }

    @Test
    fun `F face stickers all use primitive 2`() {
        for (i in 18..26) {
            assertEquals("idx $i should use prim 2", 2, KOCIEMBA_TO_CUBIE[i].primitiveIndex)
        }
    }

    @Test
    fun `D face stickers all use primitive 0`() {
        for (i in 27..35) {
            assertEquals("idx $i should use prim 0", 0, KOCIEMBA_TO_CUBIE[i].primitiveIndex)
        }
    }

    @Test
    fun `L face stickers all use primitive 1`() {
        for (i in 36..44) {
            assertEquals("idx $i should use prim 1", 1, KOCIEMBA_TO_CUBIE[i].primitiveIndex)
        }
    }

    @Test
    fun `B face stickers all use primitive 3`() {
        for (i in 45..53) {
            assertEquals("idx $i should use prim 3", 3, KOCIEMBA_TO_CUBIE[i].primitiveIndex)
        }
    }
}
