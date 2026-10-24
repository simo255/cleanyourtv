# Upstream integration and compatibility

This fork incorporates the upstream launcher and tooling through `cc4c7c59`. It adds the optional
catalog shelf, Google TV snapshot import, remote dock editor and VPN status indicator documented in
[the personalization guide](../PERSONALIZATION.md).

Upstream's reference launcher is deliberately offline. This fork's extra network permission is used
for the opt-in catalogs and bounded poster downloads; it does not add analytics or account writes.
The app also requests TV-listings access when the local app-content view is opened.

Saved dock preferences feed the updated adapter. The new reorder mode shares dock edits with the
fork editor, empty dock slots remain empty, and every Home layout includes the optional shelf.
The Hebrew layout retains upstream's translated UI with English fallbacks for fork-specific menus.

The upstream merge passed release compilation, 219 JVM tests, Android lint with zero errors and
853 Python tests (11 optional/platform cases skipped) in a clean Ubuntu snapshot. The reference
480 KiB size gate was initially increased for this fork's extra features. As of 2.21 this fork has no
hard APK size limit: CI reports the release size without failing on it. No prebuilt APK is published.

Earlier runtime checks used an onn Streaming Device 4K Pro with Android TV 12. The merged launcher
and generic publication defaults have not been installed on additional devices. Successful builds
and policy tests do not establish firmware compatibility, performance, every app's playback path,
or continued ADB availability after reboot. Google TV UI capture supports English/French layouts
only, preserves the original package state, and remains an explicitly launched dated snapshot.

The repository contains no personal account exports, viewing history, ADB keys or private captures.
Each user supplies their own account connection and device configuration locally.
