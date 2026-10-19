---
name: build-tv-launcher
description: How to build a custom home screen / launcher for an Android TV or Fire OS TV from scratch - feasibility probe first, choosing between a real HOME replacement and an accessibility takeover, escape routes, D-pad UI in Compose, screensaver, live data with a read-only key, memory budget, and on-device verification. Use when starting a new TV launcher, a kiosk/office screen, or porting this one to another TV.
---

# Building a TV launcher

This guide is drawn from building `ai.droidrun.officetv` for a Fire OS TV. The order matters: prove what the device allows before you write the app.

## 1. Probe the device first (about 30 minutes, throwaway)

Every TV vendor locks the home screen differently, so answer three questions on the real device before planning:

1. **Can you become the HOME app?** Install a probe activity with `MAIN` + `HOME` + `DEFAULT` categories and run `cmd package set-home-activity <pkg>/<activity>`.
   - Then press HOME on the real remote.
   - Read `adb logcat` to see who handles the key. On Fire OS, `FireTVKeyPolicyManager` and `PhoneWindowManager` start the Amazon launcher directly.
2. **Can an accessibility service see HOME?** Use a probe `AccessibilityService` with `flagRequestFilterKeyEvents` that logs `onKeyEvent`.
   - On some Android TV devices it sees HOME, so a key redirect works.
   - On Fire OS it never did, because the key is consumed before accessibility filtering.
3. **Can you react to the vendor home appearing?** In the same probe, log `TYPE_WINDOW_STATE_CHANGED` with package and class names. On Fire OS, `com.amazon.tv.launcher/.ui.HomeActivity_vNext` shows up, so a takeover works.

Also check:
- whether the probe survives a reboot,
- whether a `DreamService` (screensaver) can be selected (`settings put secure screensaver_components …`, preview via `am start -n com.android.systemui/.Somnambulator`),
- free RAM (`dumpsys meminfo`) and what the low-memory killer kills under load.

Build probes without Gradle if you like: `aapt2` + `javac` + `d8` + `apksigner`, about 12 KB. Remove the probe afterwards and log every setting you changed with its old value.

**Never ship a probe without an escape route.** A takeover probe with no way out locks you out of Settings: on some Fire OS TVs long-press HOME only offers picture settings. Build the escape before the takeover.

## 2. Pick the home strategy

| Device behaviour | Strategy |
|---|---|
| Your app can be the default HOME and the vendor keys keep working | Real HOME replacement (simplest, no accessibility) |
| Vendor launcher must stay (vendor keys/TV inputs depend on it) but accessibility sees HOME | Accessibility key redirect: consume `KEYCODE_HOME` on ACTION_UP and start your activity |
| HOME is consumed by the system (Fire OS) | Accessibility window takeover: on the vendor home's `TYPE_WINDOW_STATE_CHANGED`, start your activity with `NEW_TASK | REORDER_TO_FRONT` |
| None of the above | Screensaver (`DreamService`) + a normal app entry |

**Never disable the vendor launcher.** On Fire OS, HOME and Settings depend on it, and it is protected anyway.

**For a window takeover:**
- Scope the service to the vendor package (`android:packageNames`) and check `event.packageName` as well as `className`.
- Do not request window content (`canRetrieveWindowContent="false"`).
- Do not filter keys.

## 3. Escape routes (mandatory)

- **App bar tiles:** a **Settings** tile (`Settings.ACTION_SETTINGS`; on failure fall back to the vendor home) and a **vendor home** tile (`MAIN`/`HOME` with `setPackage(vendor)`). Make Settings the first tile and keep it visible at all times.
- **Pause before launching:** both tiles pause the takeover first, e.g. with `Bypass.pause(30_000)` based on `SystemClock.elapsedRealtime()`, then start the activity.
- **Long press OK** on your screen pauses the takeover too, and shows a hint.
- **Crash-loop guard:** stop the takeover for a few minutes after more than three launches in 60 s.
- **Back** on your root screen does nothing, so it does not finish the activity and trigger a re-takeover.
- **Document the manual escape:** Settings, Accessibility, turn the service off.

## 4. UI on a TV

- **Toolkit:** Jetpack Compose, `minSdk` matching the TV's Android version, pure Kotlin (some TVs are 32-bit ARM).
- **Layout:** design for 1920x1080 and size everything in a unit relative to the screen width ("cq" = width/100). The same layout then scales on 4K panels and in a mockup.
- **Focus:** only a few focusable elements, a clear focused state (filled tile), initial focus on Settings, and no focus traps.
- **Readability:** it is read from across a room. Use large type, high contrast, one accent colour and tabular numerals.
- **This app's design:** both home and screensaver use the Mobilerun light palette: #FAFAF8 background, #FFFFFF surfaces, #0A0A0A ink, #4A4A4A secondary, #6B6B6B muted and #E8E6E1 lines. Accent #1D4ED8 colours serif words, the today bar, sparkline and focused tile fill; focused text stays white. Use the black Mobilerun wordmark and mark as vectors.
- **This app's fonts:** bundled Geist for clocks, numbers and body text, JetBrains Mono for labels, and Instrument Serif italic for blue accent words. Declare resource FontFamily instances once, share the stat-accent mapping, and use plain text for unaccented content. Keep the OFL texts with the sources and inside the APK.
- **Mock first:** an HTML mockup with the same unit system is the fastest way to agree on the design. Then port it.

## 5. Screensaver

A `DreamService` (`BIND_DREAM_SERVICE`) can render the same Compose content. Show something calmer there, for example one big stat at a time, without the app bar. In the dream, skip work that only the interactive screen needs, such as discovery or app lists.

## 6. Live data without leaking secrets

- **Read aggregates from one server-side endpoint**, never from a general query API.
  - Configure its full HTTPS URL at build time via `OFFICETV_STATS_URL`, rather than committing a deployment URL. An empty value disables cloud polling and shows a clear unconfigured state.
  - Read settings in order from environment, Gradle properties, then untracked `local.properties`. The build/deploy script can source local values from `~/.config/firetv-launcher/env`, outside the repo.
  - Never put `OFFICETV_STATS_URL` in the tracked project `gradle.properties`; use the environment, `~/.gradle/gradle.properties` or untracked `local.properties` instead. Trim surrounding URL whitespace and reject userinfo (credentials) before embedding the URL in an APK.
  - Keep the response contract in a synthetic fixture (`app/src/test/resources/stats-sample.json` here). Configure the public stars source separately with `OFFICETV_GITHUB_REPO` (`owner/name`).
  - Send `GET OFFICETV_STATS_URL` to the HTTPS stats endpoint with `Authorization: Bearer <stats key>`; do not follow redirects. Stats keys are 16–512 characters from `[A-Za-z0-9._~+/=_-]`, without whitespace or control characters. The response is a JSON object with a `columns` array of names and a `results` array whose first element is one row of values; map columns by name, with series values `[[time string, number], …]` (see `app/src/test/resources/stats-sample.json`).
  - Use a read-only key for your stats endpoint and keep it aggregate-only (see the [aggregate-only rule](../../../README.md#configuration)).
- **Get the key onto the TV without putting it in the APK:**
  - An exported receiver protected by `android.permission.DUMP`. Only the ADB shell holds it, so third-party apps cannot send to it.
  - Provision the key through stdin, so it is not in argv or shell history: `adb shell 'read k; am broadcast … --es key "$k"' < keyfile`.
- **Store the key** with AES/GCM under an Android Keystore key, with `allowBackup="false"`.
  - Clear it only on a real decrypt failure, not on transient keystore errors.
  - Read it right before each request and never log it.
- **Make the installed build non-debuggable** (`isDebuggable = false`, even on the debug build type). Otherwise anyone with ADB can dump the heap and read the key.
- **Plain ADB over the LAN is unencrypted.** Prefer TLS wireless debugging (`adb pair`) when you provision secrets.
- **Networking:**
  - Use `HttpURLConnection` with timeouts, `instanceFollowRedirects = false` when an auth header is set, and a capped response size.
  - Back off per source on failures.
  - Persist the last good snapshot and show its time ("Stand HH:mm").
  - Parse responses by column name.

## 7. Memory and motion budget

TVs have little RAM, and the low-memory killer also takes system UIs you depend on: low-memory kills can close system dialogs such as the AirPlay PIN screen.

- **Budget:** set a PSS budget (ours is < 100 MB). Measure right after start **and** after 2 minutes, because leaks and per-frame allocations show up later.
- **Never use** blur, video, large bitmaps or runtime shaders beyond gradients.
- **Animate only on real changes:** for example rolling digits and a draw-in of a chart on new data, never on the first load or on a clock tick.
- **Infinite animations:**
  - Keep them small and put each in its own `graphicsLayer()`. Otherwise the whole screen is re-recorded every frame.
  - Cache paths and brushes with `drawWithCache`.
  - A full-screen redraw per frame can more than double memory use.
- **Dim with a scrim:** a black rectangle drawn over the content, not layer alpha, which allocates a full-screen offscreen buffer.
- **Respect a system animator scale of 0** by showing the final states.
- **Burn-in:** an always-on TV needs protection. Drift the layout a few dp every few minutes and dim it at night.

## 8. Verify on the device, every time

```sh
adb install -r app-debug.apk && adb shell am start -n <pkg>/.MainActivity
adb exec-out screencap -p > screen.png            # look at it
adb shell dumpsys meminfo <pkg> | grep 'TOTAL PSS'
adb shell dumpsys accessibility | grep -E 'Enabled services|Bound services'
adb logcat -d | grep -E 'FATAL|lowmemorykiller|<your tag>'
```

- **Wrap these in a repo script** (`scripts/tv.sh` here) so contributors use the same loop.
- **Before handing over,** test on the device: the escape routes first, then the takeover from another app, a reboot, the screensaver, and memory over time.

## 9. Device and network checklist

- Network ADB may switch off on reboot on some Fire OS TVs. Document how to turn it back on.
- Client/AP isolation on the router blocks ADB and AirPlay between Wi-Fi clients. A symptom is that mDNS works but ARP stays incomplete.
- For optional telemetry and unused-package changes, follow the [TV debloat skill](../tv-debloat/SKILL.md): inventory and owner approval first, protected capabilities, exact local recovery records and post-change verification.
- Long screen-off and sleep timeouts are system settings (`screen_off_timeout`, `sleep_timeout`). Log the old values before changing them.

## Calendar integration

Setup, provisioning, rotation and troubleshooting live in the [setup skill](../google-calendar-setup/SKILL.md)
and the [calendar integration guide](../../../integrations/google-calendar/README.md); do not repeat them here.
