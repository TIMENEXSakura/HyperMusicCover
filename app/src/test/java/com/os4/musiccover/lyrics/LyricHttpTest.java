package com.os4.musiccover.lyrics;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

public class LyricHttpTest {
    @Test public void disabledNetworkNeverConnectsEvenForDirectFollowUpRequests() throws Exception {
        boolean previous = com.os4.musiccover.Settings.LYRIC_ONLINE.on();
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            com.os4.musiccover.Settings.LYRIC_ONLINE.set(false);
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/lyrics";
            assertNull(LyricHttp.get(url, "test").body);
            assertEquals(0, LyricHttp.request(url, "test", "{}").code);
            server.setSoTimeout(200);
            assertThrows(SocketTimeoutException.class, server::accept);
        } finally {
            com.os4.musiccover.Settings.LYRIC_ONLINE.set(previous);
        }
    }
}
