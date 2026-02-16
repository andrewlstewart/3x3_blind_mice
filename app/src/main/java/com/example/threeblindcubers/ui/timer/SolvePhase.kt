package com.example.threeblindcubers.ui.timer

/**
 * Phases of a blindfolded solve flow.
 */
enum class SolvePhase {
    /** No scramble generated yet */
    IDLE,
    /** User is applying the scramble to the physical cube */
    SCRAMBLING,
    /** All scramble moves matched; ready to start memorization */
    SCRAMBLE_COMPLETE,
    /** Memo timer running; waiting for first BT move to start solve */
    MEMORIZING,
    /** Solve timer running; waiting for solved state or user tap */
    SOLVING,
    /** Solve complete; results shown */
    COMPLETE
}
