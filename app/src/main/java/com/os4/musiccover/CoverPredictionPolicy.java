package com.os4.musiccover;

/** The player's confirmed track always gets its own artwork request. */
final class CoverPredictionPolicy {
    enum Action { PUSH_REAL, ENTER_COVER, STAY_CLOSED }
    private CoverPredictionPolicy() {}

    static Action afterTrack(boolean coverActive, boolean explicitEntry, boolean collapsedByDefault) {
        if (coverActive) return Action.PUSH_REAL;
        return explicitEntry || !collapsedByDefault ? Action.ENTER_COVER : Action.STAY_CLOSED;
    }
}
