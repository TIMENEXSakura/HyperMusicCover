package com.os4.musiccover.lyrics;

import android.media.AudioDeviceInfo;

/** Route categories used only for an output reported by the local media route query. */
final class LyricBluetoothDevice {
    private LyricBluetoothDevice() { }

    static int kind(int type) {
        if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || type == AudioDeviceInfo.TYPE_BLE_BROADCAST) return LyricBluetoothOffset.LE;
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            return LyricBluetoothOffset.CLASSIC;
        return LyricBluetoothOffset.NONE;
    }

    static int combine(int current, int next) {
        if (current == LyricBluetoothOffset.LE || next == LyricBluetoothOffset.LE) return LyricBluetoothOffset.LE;
        if (current == LyricBluetoothOffset.CLASSIC || next == LyricBluetoothOffset.CLASSIC) return LyricBluetoothOffset.CLASSIC;
        return LyricBluetoothOffset.NONE;
    }
}
