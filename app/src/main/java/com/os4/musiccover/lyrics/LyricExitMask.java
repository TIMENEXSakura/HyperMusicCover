package com.os4.musiccover.lyrics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import java.util.ArrayList;

/** A mask in unscaled layout coordinates. The text is drawn normally, including HDR and any
 * blur, and each field then fades in its original place. Keep the shaped positions from Layout
 * so repeated words, RTL runs and wrapped syllables keep their own place in reading order. */
final class LyricExitMask {
    private static final class Cell {
        final int field;
        final RectF bounds;
        Cell(int field, float left, float top, float right, float bottom) {
            this.field = field;
            this.bounds = new RectF(left, top, right, bottom);
        }
    }

    private final ArrayList<Cell> cells = new ArrayList<>();
    private int count;
    final float clipTop, clipBottom;

    LyricExitMask(float clipTop, float clipBottom) {
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
    }

    /** Called once when a visible group starts its exit. For a tall paragraph only measure
     * the visual rows within the captured viewport. */
    void add(StaticLayout layout, int[] timedEnds, float y, float topPad, float bottomPad) {
        if (layout == null || layout.getText().length() == 0
                || y - topPad >= clipBottom || y + layout.getHeight() + bottomPad <= clipTop) return;
        int first = layout.getLineForVertical((int) Math.floor(clipTop - y));
        int last = layout.getLineForVertical((int) Math.ceil(clipBottom - y));
        int from = layout.getLineStart(first), to = layout.getLineEnd(last);
        if (to <= from) return;
        int[] fields = LyricExitWipe.fields(layout.getText().toString(), from, to, timedEnds);
        float[] positions = new float[4 * (to - from)];
        layout.fillCharacterBounds(from, to, positions, 0);
        for (int row = first; row <= last; row++) {
            float top = y + layout.getLineTop(row) - (row == 0 ? topPad : 0f);
            float bottom = y + layout.getLineBottom(row)
                    + (row == layout.getLineCount() - 1 ? bottomPad : 0f);
            Cell leftmost = null, rightmost = null, previous = null;
            for (int offset = layout.getLineStart(row); offset < layout.getLineEnd(row); offset++) {
                int slot = offset - from;
                float left = positions[4 * slot], right = positions[4 * slot + 2];
                if (right <= left) continue;
                int field = count + fields[slot];
                Cell cell = previous;
                if (cell != null && cell.field == field && left <= cell.bounds.right && right >= cell.bounds.left) {
                    cell.bounds.left = Math.min(cell.bounds.left, left);
                    cell.bounds.right = Math.max(cell.bounds.right, right);
                } else {
                    cell = new Cell(field, left, top, right, bottom);
                    cells.add(cell);
                }
                if (leftmost == null || cell.bounds.left < leftmost.bounds.left) leftmost = cell;
                if (rightmost == null || cell.bounds.right > rightmost.bounds.right) rightmost = cell;
                previous = cell;
            }
            // Include first/last glyph overhangs and the edge of the cached blur.
            if (leftmost != null) leftmost.bounds.left = -layout.getWidth();
            if (rightmost != null) rightmost.bounds.right = 2f * layout.getWidth();
        }
        count += fields[fields.length - 1] + 1;
    }

    void erase(Canvas canvas, Paint eraser, float progress, float strength) {
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            int alpha = Math.round(255f * strength * (1f - LyricExitWipe.opacity(cell.field, count, progress)));
            if (alpha == 0) continue;
            eraser.setAlpha(alpha);
            canvas.drawRect(cell.bounds, eraser);
        }
    }
}
