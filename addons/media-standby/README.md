# Media Standby

An optional Android screensaver that keeps music playing and lets the TV sleep when playback
stops. It runs independently of the launcher. While music plays, a small clock drifts on black.
Any ordinary remote navigation key returns to the previous app; manual power still sleeps the box.

Android first counts the configured screen timeout. If no media is playing when the screensaver
starts, it finishes after a two-second attachment grace and Android may sleep. If music is playing,
it stays until there has been a full screen-timeout interval without playback. A short gap between
tracks does not put the TV to sleep. The audio check runs once per second only during the dream.
This detects Android's local music stream, including audible video; it cannot detect playback on
a separate Spotify Connect speaker or another external device.

Build with JDK 17 and Android SDK 34: `gradlew.bat testReleaseUnitTest lintRelease assembleRelease`.
Select **Media Standby** in Android's screensaver settings. On firmware managed over ADB, back up
the previous settings and select `io.github.simo255.mediastandby/.MediaDream` as
`secure/screensaver_components`, enable `screensaver_enabled` and `screensaver_activate_on_sleep`,
set `system/screen_off_timeout` to `600000`, and leave `secure/sleep_timeout` and
`secure/attentive_timeout` at `-1`. Disable developer **Stay awake**. A separate hard sleep deadline
would still interrupt playback. Restore the backed-up settings to return to the previous behavior.

## Permissions

No app permissions: no network, account access, notification access, usage access, accessibility,
overlay, wake lock, boot receiver, foreground service or always-running background service.
The exported dream service is protected by `BIND_DREAM_SERVICE`, so only Android can bind it.
It runs only when selected as the screensaver. The module card opens the explanatory settings screen.

## Tested on

onn Streaming Device 4K Pro, Android TV 12: with a temporary 15-second screen timeout, Spotify
continued playing throughout 45 seconds of unchanged user inactivity with this dream active;
the box did not sleep. After dismissing the dream and confirming Spotify was paused, Android
entered standby within the following 25-second observation. Playback was restored and the final
screen timeout set to 600000 ms. The full ten-minute wall-clock interval and a reboot were not tested.
Four JVM policy tests, release lint (zero errors) and the release build pass. The APK is 12572 bytes.
The add-on folder, screenshot and isolation checks pass; the repository-wide no-prebuilt-APK check
also sees ignored APK build outputs, including pre-existing Weather builds in this local checkout.
