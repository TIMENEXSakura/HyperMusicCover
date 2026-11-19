package com.os4.musiccover.lyrics;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class LyricLayoutEpochTest {
    @Test public void newSongAndDetachInvalidateOldResults() {
        LyricLayoutEpoch epoch = new LyricLayoutEpoch();
        long oldSong = epoch.current();
        epoch.advance();
        long newSong = epoch.current();
        assertFalse(epoch.isCurrent(oldSong));
        assertTrue(epoch.isCurrent(newSong));
        epoch.advance(); // Detach while its result is still on its way to the UI.
        assertFalse(epoch.isCurrent(newSong));
    }

    @Test public void workerStopsBuildingSupersededSongBetweenRows() throws Exception {
        LyricLayoutEpoch epoch = new LyricLayoutEpoch();
        long oldSong = epoch.current();
        CountDownLatch firstRow = new CountDownLatch(1), continueWork = new CountDownLatch(1);
        AtomicInteger built = new AtomicInteger();
        Thread worker = new Thread(() -> {
            for (int i = 0; i < 100 && epoch.isCurrent(oldSong); i++) {
                built.incrementAndGet();
                if (i == 0) {
                    firstRow.countDown();
                    try { continueWork.await(); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
        worker.start();
        try {
            assertTrue(firstRow.await(5, TimeUnit.SECONDS));
            epoch.advance(); // Another track or style now owns the worker.
        } finally {
            continueWork.countDown();
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
        assertEquals(1, built.get());
    }
}
