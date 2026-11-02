package com.os4.musiccover;

import java.util.Objects;

/** A single published snapshot for selecting and validating artwork across threads. */
final class ArtworkSessionState<C, T> {
    static final class Snapshot<C, T> {
        final boolean capsule;
        final C owner, fallback;
        final T ownerToken, cardToken, fallbackToken;
        final String ownerKey, cardKey, fallbackKey;
        final long version, cardVersion;
        Snapshot(boolean capsule, C owner, T ownerToken, String ownerKey,
                 T cardToken, String cardKey, C fallback, T fallbackToken, String fallbackKey,
                 long version, long cardVersion) {
            this.capsule = capsule; this.owner = owner; this.ownerToken = ownerToken;
            this.ownerKey = ownerKey; this.cardToken = cardToken; this.cardKey = cardKey;
            this.fallback = fallback; this.fallbackToken = fallbackToken; this.fallbackKey = fallbackKey;
            this.version = version; this.cardVersion = cardVersion;
        }
    }

    private volatile Snapshot<C, T> current = new Snapshot<>(false, null, null, "", null, "",
            null, null, "", 0, 0);
    Snapshot<C, T> snapshot() { return current; }
    boolean isCurrent(Snapshot<C, T> captured) { return captured != null && captured.version == current.version; }
    boolean isCardCurrent(Snapshot<C, T> captured) {
        Snapshot<C, T> now = current;
        return captured != null && captured.version == now.version && captured.cardVersion == now.cardVersion;
    }

    /** A different selected session takes precedence over the stale media card. */
    static boolean preferFallback(Snapshot<?, ?> snapshot) {
        return snapshot.fallback != null && snapshot.fallbackToken != null
                && !Objects.equals(snapshot.fallbackToken, snapshot.cardToken);
    }

    static boolean ordinaryCardMatches(Snapshot<?, ?> snapshot, Object liveCardToken) {
        return snapshot.cardToken != null && Objects.equals(snapshot.cardToken, liveCardToken)
                && (snapshot.fallbackToken == null
                    || Objects.equals(snapshot.fallbackToken, snapshot.cardToken));
    }

    /** A missing metadata title is not evidence that two tracks are the same. */
    static boolean hasTrackTitle(String key) {
        if (key == null) return false;
        int first = key.indexOf('|');
        if (first < 0) return false;
        int end = key.indexOf('|', first + 1);
        String title = key.substring(first + 1, end < 0 ? key.length() : end).trim();
        return !title.isEmpty() && !"null".equalsIgnoreCase(title);
    }

    synchronized void capsule(boolean active, C owner, T token, String key) {
        Snapshot<C, T> old = current;
        String k = active && key != null ? key : "";
        T t = active ? token : null;
        boolean changed = old.capsule != active || active &&
                (!Objects.equals(old.ownerToken, t) || !Objects.equals(old.ownerKey, k));
        current = new Snapshot<>(active, active ? owner : null, t, k, old.cardToken, old.cardKey,
                old.fallback, old.fallbackToken, old.fallbackKey, old.version + (changed ? 1 : 0), old.cardVersion);
    }

    synchronized void card(T token, String key) {
        Snapshot<C, T> old = current;
        String k = key == null ? "" : key;
        boolean changed = !Objects.equals(old.cardToken, token) || !Objects.equals(old.cardKey, k);
        current = new Snapshot<>(old.capsule, old.owner, old.ownerToken, old.ownerKey, token, k,
                old.fallback, old.fallbackToken, old.fallbackKey,
                old.version + (changed && !old.capsule ? 1 : 0), old.cardVersion + (changed ? 1 : 0));
    }

    synchronized void fallback(C controller, T token, String key) {
        Snapshot<C, T> old = current;
        String k = key == null ? "" : key;
        boolean changed = !Objects.equals(old.fallbackToken, token) || !Objects.equals(old.fallbackKey, k);
        // The fallback cannot supply a different candidate while it has the card's token.
        // Metadata for that same session often changes from a 3-field card key to a 4-field
        // session key; invalidating a valid in-flight card request there could lose its push.
        boolean affectsSelection = old.cardToken == null || !Objects.equals(old.cardToken, old.fallbackToken)
                || !Objects.equals(old.cardToken, token);
        current = new Snapshot<>(old.capsule, old.owner, old.ownerToken, old.ownerKey, old.cardToken, old.cardKey,
                controller, token, k, old.version + (changed && !old.capsule && affectsSelection ? 1 : 0), old.cardVersion);
    }
}
