package com.os4.musiccover.lyrics;

import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** The active Apple recording and at most one lyric built ahead of its session metadata. */
final class AppleLyricStore {
    static final class Snapshot {
        final String id, payload, ref;
        final boolean duet;
        Snapshot(String id, String payload, boolean duet) {
            this.id = id;
            this.payload = payload;
            this.ref = LyricSource.lyricRef(id, payload);
            this.duet = duet;
        }
    }

    static final class Capture {
        final String id, title;
        final long epoch, order;
        final boolean activeAtStart;
        Capture(String id, String title, long epoch, long order, boolean activeAtStart) {
            this.id = id;
            this.title = title;
            this.epoch = epoch;
            this.order = order;
            this.activeAtStart = activeAtStart;
        }
    }

    private static final class Slot {
        final String id;
        Snapshot best;
        String replacedPlain;
        long latest;
        Slot(String id) { this.id = id; }

        boolean offer(Snapshot next, long order) {
            if (best != null) {
                if (order < latest && (!next.duet || best.duet || !sameLyrics(best, next, true))) return false;
                latest = Math.max(latest, order);
                if (best.payload.equals(next.payload)) return false;
                if (best.duet && !next.duet && (next.payload.equals(replacedPlain) || sameLyrics(best, next, false))) {
                    return false;
                }
                if (next.duet && !best.duet) {
                    replacedPlain = sameLyrics(best, next, true) ? best.payload : null;
                }
                if (!next.duet) replacedPlain = null;
            }
            latest = Math.max(latest, order);
            best = next;
            return true;
        }
    }

    private String playing, title;
    private long epoch, sequence;
    private Slot active, pending;

    synchronized Capture begin(String id) {
        boolean current = id.equals(playing);
        return new Capture(id, current ? title : null, epoch, ++sequence, current);
    }

    synchronized Snapshot playing(String id, String name) {
        if (!id.equals(playing)) {
            epoch++;
            active = pending != null && id.equals(pending.id) ? pending : new Slot(id);
            pending = null;
            playing = id;
        }
        title = name;
        return active.best;
    }

    /** Returns only newly selected material eligible to be published to the current session. */
    synchronized Snapshot offer(Capture capture, String payload, boolean duet) {
        boolean current = capture.id.equals(playing);
        if (capture.epoch != epoch && !(current && !capture.activeAtStart && capture.epoch + 1 == epoch)) return null;
        if (!current && capture.activeAtStart) return null;
        Slot slot;
        if (current) slot = active;
        else {
            if (pending == null || !capture.id.equals(pending.id)) {
                if (pending != null && capture.order < pending.latest) return null;
                pending = new Slot(capture.id);
            }
            slot = pending;
        }
        Snapshot next = new Snapshot(capture.id, payload, duet);
        return slot.offer(next, capture.order) && current ? next : null;
    }

    synchronized long missingEpoch(String id) {
        return id.equals(playing) && active.best == null ? epoch : -1;
    }

    synchronized void clear() {
        epoch++;
        active = pending = null;
        playing = title = null;
    }

    synchronized Snapshot held() {
        return active != null ? active.best : pending == null ? null : pending.best;
    }

    synchronized Object[] save() {
        Slot slot = active != null ? active : pending;
        Snapshot best = slot == null ? null : slot.best;
        return new Object[]{best == null ? null : best.id, best == null ? null : best.payload, playing, title,
                best == null ? null : Boolean.toString(best.duet), slot == null ? null : slot.replacedPlain};
    }

    synchronized void adopt(String id, String payload, String current, String name, String duet, String plain) {
        active = pending = null;
        playing = null;
        epoch++;
        if (id != null && payload != null) {
            pending = new Slot(id);
            pending.best = new Snapshot(id, payload, duet == null ? legacyDuet(payload) : Boolean.parseBoolean(duet));
            pending.replacedPlain = plain;
        }
        if (current != null) playing(current, name);
    }

    // The original hot-reload format only passed the payload. Recover its lead credits,
    // excluding metadata, translations and backing spans, when adopting from that version.
    private static boolean legacyDuet(String payload) {
        try {
            String ttml = new JSONObject(payload).optString("ttml");
            return !ttml.isEmpty() && secondLead(TtmlXml.parse(ttml), false);
        } catch (Exception ignored) { return false; }
    }

    private static boolean secondLead(TtmlXml.Node node, boolean second) {
        String role = node.attr("ttm:role");
        if (role != null || "head".equals(node.name())) return false;
        String agent = node.attr("ttm:agent");
        if (agent != null) second = "v2".equals(agent);
        if (second && node.text() != null && !node.text().trim().isEmpty()) return true;
        for (TtmlXml.Node child : node.children()) if (secondLead(child, second)) return true;
        return false;
    }

    // Independently delivered variants can be recognized without an earlier copy of the
    // plain callback. Require at least two distinct matching lines at comparable song times.
    // When recording a superseded plain variant, its long matching opening within 150 ms
    // also suffices: subsequent rejection then requires that same entire plain payload.
    private static boolean sameLyrics(Snapshot a, Snapshot b, boolean compareOpening) {
        try {
            String[] first = new JSONObject(a.payload).getString("lyric").split("\n");
            String[] second = new JSONObject(b.payload).getString("lyric").split("\n");
            if (compareOpening && first.length > 0 && second.length > 0) {
                String opening = words(first[0]);
                int t1 = time(first[0]), t2 = time(second[0]);
                if (opening.codePointCount(0, opening.length()) >= 12 && t1 >= 0 && t2 >= 0
                        && Math.abs(t1 - t2) <= 150 && opening.equals(words(second[0]))) return true;
            }
            Set<String> matched = new HashSet<>();
            int p = 0;
            for (String line : first) {
                int time = time(line);
                if (time < 0) continue;
                while (p < second.length && time(second[p]) < time - 1800) p++;
                String text = words(line);
                if (text.isEmpty() || matched.contains(text)) continue;
                for (int j = p; j < second.length; j++) {
                    int other = time(second[j]);
                    if (other > time + 1800) break;
                    if (other >= 0 && text.equals(words(second[j]))) {
                        matched.add(text);
                        if (matched.size() >= 2) return true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // A previously superseded identical payload can still be recognized verbatim.
        }
        return false;
    }

    private static int time(String line) {
        int colon = line.indexOf(':'), end = line.indexOf(']');
        if (!line.startsWith("[") || colon < 2 || end != colon + 7) return -1;
        return Integer.parseInt(line.substring(1, colon)) * 60000
                + Integer.parseInt(line.substring(colon + 1, colon + 3)) * 1000
                + Integer.parseInt(line.substring(colon + 4, end));
    }

    private static String words(String line) {
        StringBuilder out = new StringBuilder();
        String text = java.text.Normalizer.normalize(line.substring(line.indexOf(']') + 1),
                java.text.Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
        text.codePoints().filter(Character::isLetterOrDigit).forEach(out::appendCodePoint);
        return out.toString();
    }
}
