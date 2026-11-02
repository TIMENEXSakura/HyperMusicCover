package com.os4.musiccover.lyrics;

import java.util.List;

/** The last drawn pose of a genuinely finished lyric group, independent of the next row's scroll. */
final class LyricLineExit {
    static final long DURATION_MS = 300L;
    /** The last line has nothing after it to push it to the top. */
    static final long LAST_LINE_WAIT_MS = 1200L;
    static final float END_SCALE = 0.85f;
    // Line-timed lyrics wake at most every 500ms. Returning from sleep must not replay old exits.
    private static final int MAX_END_LAG_MS = 600;

    static final class Row {
        float x, y, scale, alpha, emphasis, lit, blur;
        float relativeY = Float.NaN;
        boolean captured;
        long startedAt = -1L;
        long readyAt = -1L;
        boolean finished;

        boolean departing() { return startedAt >= 0L; }
        boolean waitingForTop() { return readyAt >= 0L && !departing(); }
        float amount(long now) {
            float t = Math.max(0f, Math.min(1f, (now - startedAt) / (float) DURATION_MS));
            return t * t * (3f - 2f * t);
        }
        float alpha(long now) { return departing() ? 1f - amount(now) : 1f; }
        float scale(long now) { return departing() ? 1f - (1f - END_SCALE) * amount(now) : 1f; }
    }

    private Row[] rows = new Row[0];
    private int previousPosition = -1;
    private boolean previouslyPlaying;
    private boolean seeking;

    void reset(int count) {
        rows = new Row[Math.max(0, count)];
        for (int i = 0; i < rows.length; i++) rows[i] = new Row();
        previousPosition = -1;
        previouslyPlaying = false;
        seeking = false;
    }

    Row row(int index) { return index >= 0 && index < rows.length ? rows[index] : null; }

    /**
     * Keep the last live pose's opacity as the exit begins. A playing row is clipped at the
     * top without an edge fade; applying the old top-edge formula at its end made a partially
     * clipped long line disappear instantly instead of beginning its own fade.
     */
    static float edgeAlpha(float captured, float top, float bottom,
                           float clipTop, float clipBottom, float fade) {
        if (bottom <= clipTop || top >= clipBottom || clipBottom <= clipTop) return 0f;
        if (fade <= 0f) return captured;
        float edge = (clipBottom - top) / fade;
        return Math.min(captured, Math.max(0f, Math.min(1f, edge)));
    }

    void capture(int index, float x, float y, float scale, float alpha,
                 float emphasis, float lit, float blur) {
        Row row = row(index);
        if (row == null || row.departing()) return;
        row.x = x; row.y = y; row.scale = scale; row.alpha = alpha;
        row.emphasis = emphasis; row.lit = lit; row.blur = blur;
        row.captured = true;
    }

    /** Called before focus/layout rebasing, which must never be mistaken for a singer ending. */
    boolean step(List<LyricLine> lines, int position, long now, boolean playing,
                 boolean seek, boolean animate) {
        if (rows.length != lines.size()) reset(lines.size());
        boolean changed = false;
        if (seek && !seeking || position < previousPosition) {
            for (Row row : rows) changed |= row.departing();
            reset(lines.size());
        }
        for (int i = 0; i < rows.length; i++) {
            Row row = rows[i];
            if (row.departing()) {
                if (!row.finished) {
                    row.finished = !animate || now - row.startedAt >= DURATION_MS;
                    changed = true;
                }
                continue;
            }
            if (row.waitingForTop()) {
                // Only the final line has no next line to move it through the upper edge.
                // Wait a bounded time after it has actually ended; a seek resets this state.
                if (i == rows.length - 1 && row.captured && animate && !seek
                        && now - row.readyAt >= LAST_LINE_WAIT_MS) {
                    row.startedAt = now;
                    changed = true;
                }
                continue;
            }
            LyricLine line = lines.get(i);
            int start = LyricFocus.startOf(line), end = LyricFocus.endOf(line);
            if ((!playing && !previouslyPlaying) || !animate || seek || !row.captured || end <= start
                    || previousPosition < start || previousPosition >= end || position < end
                    || (long) position - end > MAX_END_LAG_MS) continue;
            // Being finished is permission to exit, not a command to freeze midway down the
            // screen. Keep following the list until the last drawn pose reaches its upper edge.
            row.readyAt = now;
            changed = true;
        }
        previousPosition = position;
        previouslyPlaying = playing;
        seeking = seek;
        return changed;
    }

    /** An offscreen waiting line must never reuse an older visible pose for its fallback. */
    void forgetWaitingPose(int index) {
        Row row = row(index);
        if (row != null && row.waitingForTop()) {
            row.captured = false;
            row.relativeY = Float.NaN;
        }
    }

    /** Called after capturing a visible row's current pose, never for an unfinished lyric. */
    boolean startAtTop(int index, float top, float clipTop, float fade, long now) {
        return startAtTop(index, top, clipTop, fade, now, false, Float.NaN);
    }

    /** A moving card must not make an otherwise stationary finished row start exiting. */
    boolean startAtTop(int index, float top, float clipTop, float fade, long now,
                       boolean morphing, float relativeY) {
        Row row = row(index);
        if (row == null) return false;
        float previousRelativeY = row.relativeY;
        row.relativeY = relativeY;
        if (!row.waitingForTop() || !row.captured
                || top > clipTop + Math.max(0f, fade)) return false;
        if (morphing && (!Float.isFinite(previousRelativeY) || !Float.isFinite(relativeY)
                || relativeY >= previousRelativeY)) return false;
        row.startedAt = now;
        return true;
    }

    /** A single delayed wake, not a continuous frame loop, for the final-line fallback. */
    long finalWakeAt() {
        if (rows.length == 0) return -1L;
        Row last = rows[rows.length - 1];
        return last.waitingForTop() && last.captured ? last.readyAt + LAST_LINE_WAIT_MS : -1L;
    }

    boolean moving() {
        for (Row row : rows) if (row.departing() && !row.finished) return true;
        return false;
    }

    /** A new layout cannot reuse pixel poses; preserve completed exits without replaying them. */
    void finishForLayout() {
        for (Row row : rows) {
            if (row.departing()) row.finished = true;
            row.captured = false;
            row.relativeY = Float.NaN;
        }
    }
}
