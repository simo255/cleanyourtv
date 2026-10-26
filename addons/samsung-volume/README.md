# Samsung Volume

Use the physical onn remote's volume and mute buttons to change a Samsung Tizen
television's own volume over the local network. This is an optional, separate
application; it does not modify the launcher, firmware, or Google infrared service.

The Samsung must support its local remote-control WebSocket on port 8002. Pairing
requires approval on the television. The configured television certificate is
pinned; the app refuses a different certificate. No accounts or cloud service are
used, and private pairing data is excluded from backups.

## Build and setup

Build from this directory with JDK 17 and Android SDK 34:

```powershell
.\gradlew.bat --no-daemon assembleRelease lintRelease
```

Install your locally built APK using ADB. Run the configuration tool from the
repository root (replace each bracketed value):

```powershell
python addons/samsung-volume/configure.py --adb <adb executable> --device <box endpoint> --tv-host <Samsung address>
```

Accept the Samsung permission request for **onn Volume**. Enable **Samsung Volume**
in the box's accessibility settings. For Android TV 12 on onn, also accept the
box's local USB-debugging authorization request and tick **Always allow**.

Set the remote's **Volume control** to **Automatic (CEC)** and keep the existing
power-button setting. The local reader relays physical volume presses without
adjusting the box's digital audio level. Mute is filtered by the accessibility
service. The setting is intended for HDMI TV speakers, and relaying stops when a
headphone output is connected.

Android TV 12 consumes Volume + and Volume - before accessibility key callbacks.
The app therefore uses a locally authenticated ADB connection, restricted in its
code to `127.0.0.1:5555`, to read the onn remote's keyboard input interface (the observed volume source; its consumer-control interface carries navigation and mute). It retains only
volume presses, not typing, navigation, screenshots, or screen content. The private
ADB key is generated on the box and encrypted with Android Keystore. Debugging
must remain enabled for this mode. Reconnection after reboot uses the saved local
authorization; no computer is required once configured. If debugging or its saved
authorization is revoked, the local reader must be authorized again.

If the TV's address or certificate changes, rerun the configuration tool and
approve the TV again. It never silently trusts a replacement certificate.

## Disable and restore

Turn off **Samsung Volume** under the box's accessibility settings. Its connection
and button reader stop; other apps and the existing power-button setting are
unchanged. You can also pause relaying from the app's own screen. Device setup
backups are private under the repository's ignored `backup/` folder.

## Permissions

- `INTERNET`: connects to the approved Samsung on the local network and the
  box's own loopback ADB endpoint. No public network service, telemetry, or ads.
- Accessibility service, protected by `BIND_ACCESSIBILITY_SERVICE`: keeps the
  relay available across apps and filters mute/volume keys when Android provides
  them. It cannot retrieve window contents. The system does not supply the two
  volume keys on the tested Android TV 12 firmware.
- The setup receiver is restricted to callers holding `WRITE_SECURE_SETTINGS`,
  such as the authorized ADB shell; ordinary apps cannot reconfigure the TV.

The accessibility service maintains local connections only while enabled. It
does not poll catalogs or start a foreground media service. Only the allowed
Samsung commands `KEY_VOLUP`, `KEY_VOLDOWN`, and `KEY_MUTE` are sent.

Protocol references: [Samsung WebSocket client](https://github.com/xchwarze/samsung-tv-ws-api),
[Android ADB protocol](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/OVERVIEW.TXT).

Test target: onn Streaming Device 4K Pro, Android TV 12, firmware
`SGZ3.231226.086.A1.12555768`, with Samsung UN50TU7000FXZC. See the device-validation
notes recorded for the individual installation; a network-command test alone is
not proof of physical-button behavior.
