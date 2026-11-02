package com.os4.musiccover.lyrics;

import java.util.Arrays;
import java.util.List;

final class LyricFocus {
    static final int DEFAULT_LIMIT = 3;
    private int[] selected = new int[0];
    private int[] next = new int[0];
    private boolean[] selectedIndex = new boolean[0];
    int count;
    int first;
    int last;
    private float[] positions = new float[0];

    static int clampLimit(int value) {
        return value == 0 ? 0 : Math.max(1, Math.min(4, value));
    }

    static int startOf(LyricLine line) {
        int start = line.start;
        for (LyricLine bg : line.backgrounds()) start = Math.min(start, bg.start);
        return start;
    }

    static int endOf(LyricLine line) {
        int end = line.end;
        for (LyricLine bg : line.backgrounds()) end = Math.max(end, bg.end);
        return end;
    }

    boolean update(List<LyricLine> lines, int position, int limit, int fallback, int latestStarted) {
        int previousCount = count;
        int found = 0;
        boolean changed = false;
        limit = clampLimit(limit);
        limit = limit == 0 ? lines.size() : Math.min(limit, lines.size());
        if (selected.length < limit) {
            selected = Arrays.copyOf(selected, limit);
            next = Arrays.copyOf(next, limit);
        }
        if (selectedIndex.length < lines.size()) selectedIndex = new boolean[lines.size()];
        for (int index = lines.size() - 1; index >= 0 && found < limit; index--) {
            LyricLine line = lines.get(index);
            if (position < startOf(line) || position >= endOf(line)) continue;
            next[found++] = index;
        }
        // A previous voice can outlast a newer lead line. Keep the most recently started lead
        // in the group until a later line starts, rather than scrolling backwards to that voice.
        // This is derived from the playhead, not a sticky historical index, so seeks and track
        // changes immediately select the right line. The display limit still applies.
        if (found > 0 && latestStarted > next[0] && latestStarted < lines.size()
                && latestStarted >= 0 && lines.get(latestStarted).start <= position) {
            int heldCount = Math.min(found + 1, limit);
            for (int slot = heldCount - 1; slot > 0; slot--) next[slot] = next[slot - 1];
            next[0] = latestStarted;
            found = heldCount;
        }
        Arrays.fill(selectedIndex, false);
        for (int slot = 0; slot < found; slot++) {
            if (selected[slot] != next[slot]) changed = true;
            selected[slot] = next[slot];
            selectedIndex[next[slot]] = true;
        }
        count = found;
        last = found == 0 ? fallback : selected[0];
        first = found == 0 ? fallback : selected[found - 1];
        return changed || previousCount != count;
    }

    boolean contains(int index) {
        if (count == 0) return index == last;
        return index >= 0 && index < selectedIndex.length && selectedIndex[index];
    }

    boolean visible(int index) {
        return index <= first || index >= last || contains(index);
    }

    int distance(int index) {
        if (contains(index)) return 0;
        int nearest = Math.min(Math.abs(index - first), Math.abs(index - last));
        // Selected indices are descending. Locate the two neighbours without scanning all
        // simultaneously active voices for every rendered lyric row.
        int low = 0, high = count;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (selected[mid] > index) low = mid + 1;
            else high = mid;
        }
        if (low < count) nearest = Math.min(nearest, Math.abs(index - selected[low]));
        if (low > 0) nearest = Math.min(nearest, Math.abs(index - selected[low - 1]));
        return nearest + (index < first ? 1 : 0);
    }

    /** Match the renderer: unfinished, waiting, departing and still-fading rows need space. */
    static boolean retainSlot(boolean unfinished, boolean waiting, boolean departing,
                              boolean finished, boolean visible, float alpha) {
        return !finished && (unfinished || departing || visible && (waiting || alpha > 0.003f));
    }

    /** Prevent independently moving rows from crossing before their springs have settled. */
    static float separationOffset(float top, float previousBottom) {
        return Math.max(0f, previousBottom - top);
    }

    void layout(float[] base, float[] heights, float gap) {
        layout(base, heights, gap, null);
    }

    /** Keep a slot for a row which is still drawn between simultaneously singing rows. */
    void layout(float[] base, float[] heights, float gap, boolean[] retained) {
        if (positions.length != base.length) positions = new float[base.length];
        System.arraycopy(base, 0, positions, 0, base.length);
        if (count < 2) return;
        float cursor = base[first];
        for (int index = first; index <= last; index++) {
            positions[index] = cursor;
            if (contains(index) || retained != null && retained[index])
                cursor += heights[index] + gap;
        }
        float removed = base[last] - positions[last];
        for (int index = last + 1; index < base.length; index++) positions[index] -= removed;
    }

    float position(int index, float[] base) {
        return positions.length == base.length ? positions[index] : base[index];
    }

    float height(float[] base, float[] heights) {
        return position(last, base) - position(first, base) + heights[last];
    }

    void reset() {
        count = 0;
        first = last = -1;
        positions = new float[0];
        Arrays.fill(selected, -1);
        Arrays.fill(selectedIndex, false);
    }
}
