package com.os4.musiccover;

/** Session tokens, rather than controller instances, identify one capsule owner. */
final class CapsuleSessionPolicy {
    private CapsuleSessionPolicy() {}
    static boolean blocksReload(boolean capsuleRequest, boolean navigationOverlay) {
        return capsuleRequest || navigationOverlay;
    }
    /** The visible media notification chooses the source; keep the existing owner only
     * during a temporary gap when no card or active candidate is available. */
    static <T> T preferred(T card, boolean cardPlaying, T active, boolean activePlaying, T current) {
        if (activePlaying && !cardPlaying) return active;
        return card != null ? card : active != null ? active : current;
    }
    static boolean same(Object owner, Object candidate) {
        return owner != null && owner.equals(candidate);
    }
}
