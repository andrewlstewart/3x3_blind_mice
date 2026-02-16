package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Face
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.Rotation
import com.example.threeblindcubers.ui.timer.components.getFrontFaceGrid
import com.example.threeblindcubers.ui.timer.components.getRightFaceGrid
import com.example.threeblindcubers.ui.timer.components.getTopFaceGrid
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests that the visualization grid mapping functions correctly translate
 * Kociemba facelet state into visual grid positions.
 *
 * We test by applying known moves and checking that the visible face grids
 * show the expected colors at the expected positions.
 *
 * Visual grid conventions:
 *   - Top face (U): visRow=0 = FRONT edge (touching F), visRow=2 = BACK edge
 *                    visCol=0 = LEFT, visCol=2 = RIGHT
 *   - Front face (F): visRow=0 = BOTTOM, visRow=2 = TOP
 *                      visCol=0 = LEFT, visCol=2 = RIGHT
 *   - Right face (R): visRow=0 = BOTTOM, visRow=2 = TOP
 *                      visCol=0 = FRONT edge, visCol=2 = BACK edge
 *
 * Color indices: 0=White(U), 1=Red(R), 2=Green(F), 3=Yellow(D), 4=Orange(L), 5=Blue(B)
 */
class CubeVisualizationMappingTest {

    private lateinit var tracker: CubeStateTracker

    @Before
    fun setUp() {
        tracker = CubeStateTracker()
    }

    // =========================================================
    // Solved state: all faces show their own color
    // =========================================================

    @Test
    fun `solved state - top face is all white`() {
        val grid = getTopFaceGrid(tracker.getState())
        for (r in 0..2) for (c in 0..2) {
            assertEquals("U visRow=$r visCol=$c should be white(0)", 0, grid[r][c])
        }
    }

    @Test
    fun `solved state - front face is all green`() {
        val grid = getFrontFaceGrid(tracker.getState())
        for (r in 0..2) for (c in 0..2) {
            assertEquals("F visRow=$r visCol=$c should be green(2)", 2, grid[r][c])
        }
    }

    @Test
    fun `solved state - right face is all red`() {
        val grid = getRightFaceGrid(tracker.getState())
        for (r in 0..2) for (c in 0..2) {
            assertEquals("R visRow=$r visCol=$c should be red(1)", 1, grid[r][c])
        }
    }

    // =========================================================
    // U CW: looking down, top layer rotates clockwise.
    //
    // Effect on visible faces:
    //   U face: corners/edges rotate CW (looking from above).
    //           All still white — face color unchanged.
    //   U CW (looking down at top, F=south): S→W, W→N, N→E, E→S
    //     i.e. R→F, F→L, L→B, B→R
    //   F face top row: gets R's old top row = red (1)
    //   R face top row: gets B's old top row = blue (5)
    // =========================================================

    @Test
    fun `U CW - top face still all white`() {
        tracker.applyMove(Move(Face.U, Rotation.CLOCKWISE))
        val grid = getTopFaceGrid(tracker.getState())
        for (r in 0..2) for (c in 0..2) {
            assertEquals("U[$r][$c] should be white after U CW", 0, grid[r][c])
        }
    }

    @Test
    fun `U CW - front face top row becomes red (from R)`() {
        tracker.applyMove(Move(Face.U, Rotation.CLOCKWISE))
        val grid = getFrontFaceGrid(tracker.getState())

        // F top row = visRow=2 (top of front face)
        // After U CW: F top row gets R's old top row = red (1)
        assertEquals("F top-left after U CW", 1, grid[2][0])
        assertEquals("F top-center after U CW", 1, grid[2][1])
        assertEquals("F top-right after U CW", 1, grid[2][2])

        // F bottom rows unchanged (still green)
        assertEquals("F bottom-left after U CW", 2, grid[0][0])
        assertEquals("F mid-left after U CW", 2, grid[1][0])
    }

    @Test
    fun `U CW - right face top row becomes blue (from B)`() {
        tracker.applyMove(Move(Face.U, Rotation.CLOCKWISE))
        val grid = getRightFaceGrid(tracker.getState())

        // R top row = visRow=2
        // After U CW: R top row gets B's old top row = blue (5)
        assertEquals("R top-front after U CW", 5, grid[2][0])
        assertEquals("R top-mid after U CW", 5, grid[2][1])
        assertEquals("R top-back after U CW", 5, grid[2][2])

        // R bottom rows unchanged (still red)
        assertEquals("R bottom-front after U CW", 1, grid[0][0])
    }

    // =========================================================
    // U CCW: opposite of U CW.
    //   F top row gets L's old top row = orange (4).
    //   R top row gets F's old top row = green (2).
    // =========================================================

    @Test
    fun `U CCW - front face top row becomes orange (from L)`() {
        tracker.applyMove(Move(Face.U, Rotation.COUNTER_CLOCKWISE))
        val grid = getFrontFaceGrid(tracker.getState())

        assertEquals("F top-left after U'", 4, grid[2][0])
        assertEquals("F top-center after U'", 4, grid[2][1])
        assertEquals("F top-right after U'", 4, grid[2][2])
    }

    @Test
    fun `U CCW - right face top row becomes green (from F)`() {
        tracker.applyMove(Move(Face.U, Rotation.COUNTER_CLOCKWISE))
        val grid = getRightFaceGrid(tracker.getState())

        assertEquals("R top-front after U'", 2, grid[2][0])
        assertEquals("R top-mid after U'", 2, grid[2][1])
        assertEquals("R top-back after U'", 2, grid[2][2])
    }

    // =========================================================
    // R CW: right face rotates CW (looking from right side).
    //   R face: all still red (own face rotation).
    //   F face right column: was green, now gets D's right column = yellow.
    //     R CW adjacent cycles: [2,51,29,20],[5,48,32,23],[8,45,35,26]
    //     state[20] = old[26] — but wait, both are F face.
    //     Actually: state[20]=old[2] NO...
    //     Cycle [2,51,29,20] with CW: state[2]=old[20], state[51]=old[2], state[29]=old[51], state[20]=old[29]
    //     Wait, CW cycle applies as: temp=state[d]; [d]=[c]; [c]=[b]; [b]=[a]; [a]=temp
    //     So for [a,b,c,d]=[2,51,29,20]:
    //       temp=state[20]=green(2); state[20]=state[29]=yellow(3); state[29]=state[51]=blue(5);
    //       state[51]=state[2]=white(0); state[2]=temp=green(2)
    //     So after R CW:
    //       F[20] (top-right of F, visRow=2 visCol=2) = yellow(3)  (was D's)
    //       U[2] (top-right of U, back-right visually) = green(2) (was F's)
    //
    //   F right column = indices 20,23,26 → get D's right column colors
    //     state[20]=old_D[29]=3(yellow), state[23]=old_D[32]=3, state[26]=old_D[35]=3
    //     Wait, D's right column in solved = all yellow(3).
    //     No: cycle [8,45,35,26]: temp=state[26]=green; [26]=[35]=yellow; [35]=[45]=blue; [45]=[8]=white; [8]=temp=green
    //     state[26]=3(yellow), state[23]=3(yellow), state[20]=3(yellow) ← F right col all become yellow
    // =========================================================

    @Test
    fun `R CW - front face right column becomes yellow (from D)`() {
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        val grid = getFrontFaceGrid(tracker.getState())

        // F right column = visCol=2
        // visRow=0 (bottom) = Kociemba idx 26, visRow=1 (mid) = idx 23, visRow=2 (top) = idx 20
        assertEquals("F bottom-right after R CW", 3, grid[0][2])  // yellow
        assertEquals("F mid-right after R CW", 3, grid[1][2])     // yellow
        assertEquals("F top-right after R CW", 3, grid[2][2])     // yellow

        // F left column unchanged (still green)
        assertEquals("F bottom-left after R CW", 2, grid[0][0])
        assertEquals("F mid-left after R CW", 2, grid[1][0])
        assertEquals("F top-left after R CW", 2, grid[2][0])
    }

    @Test
    fun `R CW - top face right column becomes green (from F)`() {
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        val grid = getTopFaceGrid(tracker.getState())

        // U right column = visCol=2
        // Kociemba indices for U right col: 2,5,8
        // After R CW: state[2]=old_F[20]=green(2), state[5]=old_F[23]=green(2), state[8]=old_F[26]=green(2)
        assertEquals("U front-right after R CW", 2, grid[0][2])
        assertEquals("U mid-right after R CW", 2, grid[1][2])
        assertEquals("U back-right after R CW", 2, grid[2][2])

        // U left column unchanged (still white)
        assertEquals("U front-left after R CW", 0, grid[0][0])
    }

    @Test
    fun `R CW - right face still all red`() {
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        val grid = getRightFaceGrid(tracker.getState())
        for (r in 0..2) for (c in 0..2) {
            assertEquals("R[$r][$c] should be red after R CW", 1, grid[r][c])
        }
    }

    // =========================================================
    // F CW: front face rotates CW (looking at it).
    //   F face: all still green (own face rotation).
    //   U face bottom row (visRow=0 = front edge of U):
    //     gets L's right column colors = orange(4)
    //     F CW cycles: [6,9,29,44],[7,12,28,41],[8,15,27,38]
    //     For [6,9,29,44]: temp=[44]=orange; [44]=[29]=yellow; [29]=[9]=red; [9]=[6]=white; [6]=temp=orange
    //     U bottom row = Kociemba 6,7,8 → state[6]=orange(4), state[7]=orange(4), state[8]=orange(4)
    //
    //   R face left column (visCol=0 = front edge of R):
    //     gets U's bottom row = white(0)
    //     state[9]=old[6]=white(0), state[12]=old[7]=white(0), state[15]=old[8]=white(0)
    // =========================================================

    @Test
    fun `F CW - top face front row becomes orange (from L)`() {
        tracker.applyMove(Move(Face.F, Rotation.CLOCKWISE))
        val grid = getTopFaceGrid(tracker.getState())

        // U front row = visRow=0 (Kociemba row 2 = indices 6,7,8)
        assertEquals("U front-left after F CW", 4, grid[0][0])   // orange
        assertEquals("U front-center after F CW", 4, grid[0][1]) // orange
        assertEquals("U front-right after F CW", 4, grid[0][2])  // orange

        // U back row unchanged
        assertEquals("U back-left after F CW", 0, grid[2][0])
    }

    @Test
    fun `F CW - right face front column becomes white (from U)`() {
        tracker.applyMove(Move(Face.F, Rotation.CLOCKWISE))
        val grid = getRightFaceGrid(tracker.getState())

        // R front column = visCol=0 (Kociemba col 0 = indices 9,12,15)
        assertEquals("R bottom-front after F CW", 0, grid[0][0])  // white
        assertEquals("R mid-front after F CW", 0, grid[1][0])     // white
        assertEquals("R top-front after F CW", 0, grid[2][0])     // white

        // R back column unchanged
        assertEquals("R bottom-back after F CW", 1, grid[0][2])
    }

    // =========================================================
    // D CW (looking from below): opposite of U.
    //   D CW sends: F bottom → L bottom, R bottom → F bottom, B bottom → R bottom, L bottom → B bottom
    //   Wait, need to check cycles: [15,24,42,51],[16,25,43,52],[17,26,44,53]
    //   [15,24,42,51] CW: temp=[51]; [51]=[42]; [42]=[24]; [24]=[15]; [15]=temp
    //     state[15]=old[51]=blue(5); state[24]=old[15]=red(1);
    //     state[42]=old[24]=green(2); state[51]=old[42]=orange(4)
    //
    //   F bottom row (24,25,26): state[24]=old[15]=red(1), state[25]=old[16]=red, state[26]=old[17]=red
    //   R bottom row (15,16,17): state[15]=old[51]=blue(5), state[16]=old[52]=blue, state[17]=old[53]=blue
    // =========================================================

    @Test
    fun `D CW - front face bottom row becomes orange (from L)`() {
        tracker.applyMove(Move(Face.D, Rotation.CLOCKWISE))
        val grid = getFrontFaceGrid(tracker.getState())

        // F bottom row = visRow=0 (Kociemba row 2 = indices 24,25,26)
        // D CW: L→F, so F gets orange (4)
        assertEquals("F bottom-left after D CW", 4, grid[0][0])  // orange
        assertEquals("F bottom-center after D CW", 4, grid[0][1])
        assertEquals("F bottom-right after D CW", 4, grid[0][2])
    }

    @Test
    fun `D CW - right face bottom row becomes green (from F)`() {
        tracker.applyMove(Move(Face.D, Rotation.CLOCKWISE))
        val grid = getRightFaceGrid(tracker.getState())

        // R bottom row = visRow=0 (Kociemba row 2 = indices 15,16,17)
        // D CW: F→R, so R gets green (2)
        assertEquals("R bottom-front after D CW", 2, grid[0][0])  // green
        assertEquals("R bottom-mid after D CW", 2, grid[0][1])
        assertEquals("R bottom-back after D CW", 2, grid[0][2])
    }

    // =========================================================
    // B CW: back face rotates CW looking from back.
    //   Cycles: [2,36,33,17],[1,39,34,14],[0,42,35,11]
    //   U back row gets R's color = red (1)
    //   R back column gets D's color = yellow (3)
    // =========================================================

    @Test
    fun `B CW - top face back row becomes red (from R)`() {
        tracker.applyMove(Move(Face.B, Rotation.CLOCKWISE))
        val grid = getTopFaceGrid(tracker.getState())

        // U back row = visRow=2 (Kociemba row 0 = indices 0,1,2)
        assertEquals("U back-left after B CW", 1, grid[2][0])   // red
        assertEquals("U back-center after B CW", 1, grid[2][1])
        assertEquals("U back-right after B CW", 1, grid[2][2])
    }

    @Test
    fun `B CW - right face back column becomes yellow (from D)`() {
        tracker.applyMove(Move(Face.B, Rotation.CLOCKWISE))
        val grid = getRightFaceGrid(tracker.getState())

        // R back column = visCol=2 (Kociemba col 2 = indices 11,14,17)
        assertEquals("R bottom-back after B CW", 3, grid[0][2])  // yellow
        assertEquals("R mid-back after B CW", 3, grid[1][2])
        assertEquals("R top-back after B CW", 3, grid[2][2])
    }

    // =========================================================
    // Verify that applying U CW then U CCW returns grids to solved
    // =========================================================

    @Test
    fun `U CW then U CCW returns all grids to solved`() {
        tracker.applyMove(Move(Face.U, Rotation.CLOCKWISE))
        tracker.applyMove(Move(Face.U, Rotation.COUNTER_CLOCKWISE))
        val state = tracker.getState()

        val uGrid = getTopFaceGrid(state)
        val fGrid = getFrontFaceGrid(state)
        val rGrid = getRightFaceGrid(state)

        for (r in 0..2) for (c in 0..2) {
            assertEquals("U[$r][$c]", 0, uGrid[r][c])
            assertEquals("F[$r][$c]", 2, fGrid[r][c])
            assertEquals("R[$r][$c]", 1, rGrid[r][c])
        }
    }
}
