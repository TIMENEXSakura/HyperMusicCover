package com.os4.musiccover.lyrics;

import static org.junit.Assert.assertEquals;
import android.media.AudioDeviceInfo;
import org.junit.Test;

public class LyricBluetoothOffsetTest {
    @Test public void defaultsOnlyApplyToBluetooth() {
        assertEquals(400, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.CLASSIC, 400, 100));
        assertEquals(100, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.LE, 400, 100));
        assertEquals(0, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.NONE, 400, 100));
        assertEquals(0, LyricBluetoothOffset.adjustment(false, LyricBluetoothOffset.LE, 400, 100));
    }
    @Test public void offsetsAreSignedAndBounded() {
        assertEquals(-500, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.CLASSIC, -900, 100));
        assertEquals(500, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.LE, 400, 900));
        assertEquals(600, LyricBluetoothOffset.position(1000, -400));
        assertEquals(0, LyricBluetoothOffset.position(100, -400));
        assertEquals(0, LyricBluetoothOffset.position(-1, 400));
        assertEquals(Integer.MAX_VALUE, LyricBluetoothOffset.position(Long.MAX_VALUE, 400));
    }
    @Test public void disabledCompensationLeavesPlaybackPositionUnchangedAndKeepsSettings() {
        int classic = 350, le = -80;
        for (int route : new int[] { LyricBluetoothOffset.CLASSIC, LyricBluetoothOffset.LE }) {
            int disabled = LyricBluetoothOffset.adjustment(false, route, classic, le);
            assertEquals(12_345, LyricBluetoothOffset.position(12_345, disabled));
        }
        // Switching back on applies the remembered values independently to each route.
        assertEquals(350, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.CLASSIC, classic, le));
        assertEquals(-80, LyricBluetoothOffset.adjustment(true, LyricBluetoothOffset.LE, classic, le));
    }
    @Test public void routeClassificationNeverTreatsWiredOrBuiltInAsBluetooth() {
        assertEquals(LyricBluetoothOffset.NONE, LyricBluetoothDevice.kind(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals(LyricBluetoothOffset.NONE, LyricBluetoothDevice.kind(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertEquals(LyricBluetoothOffset.CLASSIC, LyricBluetoothDevice.kind(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertEquals(LyricBluetoothOffset.LE, LyricBluetoothDevice.kind(AudioDeviceInfo.TYPE_BLE_HEADSET));
        assertEquals(LyricBluetoothOffset.LE, LyricBluetoothDevice.combine(LyricBluetoothOffset.CLASSIC, LyricBluetoothOffset.LE));
    }
}
