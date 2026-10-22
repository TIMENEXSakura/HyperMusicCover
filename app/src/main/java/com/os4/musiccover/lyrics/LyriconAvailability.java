package com.os4.musiccover.lyrics;

/** Runtime evidence from the subscriber protocol, never an inference from an APK package. */
public final class LyriconAvailability {
    private boolean observed;
    private String connection = "not started";
    private boolean provider;

    public synchronized void connection(String state) {
        connection = state;
        if (connected()) observed = true;
        else provider = false;
    }

    public synchronized void provider(boolean active) { provider = active && connected(); }
    public synchronized boolean observed() { return observed; }
    public synchronized boolean connected() {
        return "connected".equals(connection) || "reconnected".equals(connection);
    }
    public synchronized boolean active() { return connected() && provider; }
    public synchronized String status() {
        return connected() ? (provider ? "active" : "connected") : connection;
    }
}
