package com.os4.musiccover.lyrics;

import java.util.concurrent.atomic.AtomicBoolean;

/** One blur job. A visible row can promote a queued prewarm, but it must rasterize only once. */
final class LyricBlurWork {
    private final int row;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean visible;

    LyricBlurWork(int row) { this.row = row; }

    /** True once for a not-yet-started prewarm, so it can be moved to the front of the queue. */
    boolean promote() {
        boolean first = !visible;
        visible = true;
        return first && !started.get();
    }

    boolean claim() { return started.compareAndSet(false, true); }

    /** Keep visible requests, and skip prewarms outside the renderer's retained row window. */
    boolean obsolete(int focus) {
        return !visible && focus >= 0 && (row < focus - 6 || row > focus + 12);
    }
}
