package com.cleanyourtv.samsungvolume;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Keeps local relay connections alive and intercepts mute; never changes Android volume. */
public final class VolumeService extends AccessibilityService {
    private AudioManager audio;
    private SharedPreferences prefs;
    private InputManager input;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (p, key) -> {
        if ("enabled".equals(key) || "raw_monitor".equals(key) || "host".equals(key)) handler.post(this::syncState);
    };
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { syncState(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { syncState(); }
    };
    private final InputManager.InputDeviceListener inputListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { syncState(); }
        @Override public void onInputDeviceChanged(int id) { syncState(); }
        @Override public void onInputDeviceRemoved(int id) { }
    };

    @Override protected void onServiceConnected() {
        Log.i("SamsungVolume", "Volume key service connected");
        prefs = getSharedPreferences("samsung_volume", MODE_PRIVATE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
        audio.registerAudioDeviceCallback(deviceCallback, handler);
        input = (InputManager) getSystemService(INPUT_SERVICE);
        input.registerInputDeviceListener(inputListener, handler);
        syncState();
    }

    private void syncState() {
        if (prefs == null) return;
        prefs.edit().putBoolean("raw_monitor_route_hdmi", hdmiOutput()).apply();
        boolean enabled = prefs.getBoolean("enabled", false) && !SamsungRemote.get(this).host().isEmpty();
        if (enabled) SamsungRemote.get(this).connect();
        else SamsungRemote.get(this).disconnect();
        if (enabled && prefs.getBoolean("raw_monitor", true)) LocalAdbVolumeKeys.get(this).start();
        else LocalAdbVolumeKeys.get(this).stop();
    }

    private boolean hdmiOutput() {
        if (audio == null) return false;
        boolean hdmi = false;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_HDMI || type == AudioDeviceInfo.TYPE_HDMI_ARC
                    || type == AudioDeviceInfo.TYPE_HDMI_EARC) hdmi = true;
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE
                    || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) return false;
        }
        return hdmi;
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (prefs == null || !prefs.getBoolean("enabled", false) || !hdmiOutput()) return false;
        String key;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (prefs.getBoolean("raw_monitor", true)) return false;
                key = "KEY_VOLUP"; break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (prefs.getBoolean("raw_monitor", true)) return false;
                key = "KEY_VOLDOWN"; break;
            case KeyEvent.KEYCODE_VOLUME_MUTE: key = "KEY_MUTE"; break;
            default: return false;
        }
        if (SamsungRemote.get(this).host().isEmpty()) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            SamsungRemote.get(this).key(key);
            prefs.edit().putLong("last_key_at", System.currentTimeMillis())
                .putInt("key_count", prefs.getInt("key_count", 0) + 1).apply();
            Log.i("SamsungVolume", "Volume command received: " + key);
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (audio != null) audio.unregisterAudioDeviceCallback(deviceCallback);
        if (input != null) input.unregisterInputDeviceListener(inputListener);
        handler.removeCallbacksAndMessages(null);
        LocalAdbVolumeKeys.get(this).stop();
        SamsungRemote.get(this).disconnect();
        super.onDestroy();
    }
}
