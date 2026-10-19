---
name: office-tv
description: Work on the Mobilerun office TV launcher (Fire OS TV) end to end - connect to the TV after a reboot, deploy a build, verify it on the screen, debug the home takeover, the stats, AirPlay and memory, and improve the design safely. Use for any change to this repo, for "the TV shows the Amazon home again", "stats are missing", "AirPlay does not connect", or "deploy to the TV".
---

# Office TV launcher

An Android TV app (`ai.droidrun.officetv`) that replaces what people see on the office Fire TV: clock, featured calendar, AirPlay readiness pill, aggregate Mobilerun stats, two escape buttons, and a screensaver. It cannot replace the Fire OS launcher. It sits on top of it.

Always pass the TV's ADB address as `TV=<address:port>` (prefer the TLS wireless-debugging port). Do not commit addresses, personal paths or keys.

## The device and its traps

- Fire OS 8 TV (Android 11, API 30), a low-RAM Fire TV (under 2 GB) with a 4K panel and a 1920x1080 UI.
- **Some Fire OS builds turn network ADB off after a reboot.** Re-enable it on the TV under Settings, My Fire TV, Developer options, ADB debugging. `adb connect` then reports "Connection refused" until someone does this.
- **The first connection from a new computer** shows an "Allow USB debugging" prompt on the TV. Someone must confirm it with the remote.
- **The Wi-Fi router must not isolate clients.** Client/AP isolation on the router blocks ADB and AirPlay between Wi-Fi clients: if ARP to the TV stays `(incomplete)` while mDNS still shows the TV, turn isolation off in the router's LAN settings.
- **Memory is tight.** Fire OS kills background apps aggressively, and low-memory kills can close system dialogs such as the AirPlay PIN screen (`com.apple.airplay.amazon.airplay`). Keep the launcher under 100 MB PSS (`scripts/tv.sh status`).
- **Fire OS updates** can re-enable disabled packages and reset settings. Re-check after an update.

## Connect

For unlocking Developer options and resolving authorization, refused/offline connections,
network isolation or TLS pairing, use [Enable ADB on the TV](../../../README.md#enable-adb-on-the-tv).

1. Be on the office Wi-Fi.
2. Preferred: TLS wireless debugging. `adb pair <address>:<pair-port>` with the code shown on the TV, then `adb connect` to the port advertised as `_adb-tls-connect._tcp` (`adb mdns services` lists it). Legacy `adb connect <address>:5555` works too, but it is unencrypted on the LAN.
3. Check with `adb devices`. The state must be `device`, not `unauthorized` or `offline`. For `offline`, run `adb disconnect` first, then connect again.

## Everyday loop

```sh
export TV=<address:port>
scripts/tv.sh deploy      # gradle assembleDebug + adb install -r + start the app
scripts/tv.sh status      # version, accessibility service, screensaver, PSS
scripts/tv.sh screenshot  # tv-screenshot.png in the repo root, never commit it
```

- `install -r` keeps app data and cached stats. The stats key survives redeploys. The host key file defaults to `~/.config/firetv-launcher/stats-key` (override with `STATS_KEY_FILE`).
- Build with JDK 17 (`JAVA_HOME`) and an Android SDK with platform 34 (`local.properties` with `sdk.dir`, untracked).
- Before a PR: `./gradlew testDebugUnitTest lintDebug assembleDebug`, and look at a fresh screenshot from the real TV. Layout and memory problems only show up on the device.

## First-time setup (TV owner only)

```sh
scripts/tv.sh key     # stats key from ~/.config/firetv-launcher/stats-key via stdin
scripts/tv.sh enable  # turn on the home takeover accessibility service
scripts/tv.sh dream   # set the screensaver and start a preview
scripts/tv.sh revert  # restore the saved original settings and uninstall
```

- **The stats key lives only on the TV**, encrypted in the Android Keystore, and in the owner's local key file. It is never in the repo, the APK or the logs.
- **Use a read-only key for your stats endpoint** and keep the endpoint aggregate-only; see the [aggregate-only rule](../../../README.md#configuration).
- **To rotate:** create a new read-only key, write it to the key file, run `scripts/tv.sh key`, then rotate the key on your server. Never paste a key into a chat, an issue or a PR.

## How the home takeover works (and why)

- **Fire OS handles the HOME key itself.** `FireTVKeyPolicyManager` consumes `KEYCODE_HOME` before any accessibility service sees it. Amazon's `HOME_PRESSED` broadcast does not reach third-party apps. Disabling `com.amazon.tv.launcher` breaks Fire OS. These approaches do not work on Fire OS.
- **What works:** `HomeTakeoverService` watches `TYPE_WINDOW_STATE_CHANGED` for `com.amazon.tv.launcher.ui.HomeActivity_vNext`, scoped to that package, and brings `MainActivity` to the front. The Amazon home flashes for about 0.75 s. This survives reboots.
- **Escape routes are mandatory, never remove them.**
  - The "Einstellungen" tile (always first in the app bar) and the "Fire TV Startseite" tile pause the takeover for 30 s (`Bypass`).
  - A long press on OK also pauses it.
  - `TakeoverLimiter` stops the takeover for 5 min if the app crash-loops.
  - Without these routes a takeover can lock you out of Settings: on some Fire OS TVs long-pressing HOME only shows picture settings. Recovery is the quick menu or Settings via the remote.
- **If someone is locked out:** if ADB still works, run `scripts/tv.sh revert`. Otherwise use the Einstellungen tile, then Accessibility, then turn off "Mobilerun Office TV".

## Stats data path

- Configure `OFFICETV_STATS_URL` (full HTTPS endpoint URL; default empty) and `OFFICETV_GITHUB_REPO` (`owner/name`; default `droidrun/mobilerun`) at build time. Precedence: environment, Gradle property, untracked `local.properties`. The build/deploy script also sources `~/.config/firetv-launcher/env` before invoking Gradle.
- Never put `OFFICETV_STATS_URL` in the tracked project `gradle.properties`; use the environment, `~/.gradle/gradle.properties` or untracked `local.properties` instead. Surrounding URL whitespace is trimmed; URLs containing userinfo (credentials) are rejected.
- `StatsRepository` fetches the configured stats endpoint every 15 min with backoff from 1 min doubling up to 60 min. An empty URL disables cloud polling and displays "Statistik nicht konfiguriert". GitHub stars are fetched every 60 min without auth.
- Send `GET OFFICETV_STATS_URL` to the HTTPS stats endpoint with `Authorization: Bearer <stats key>`; do not follow redirects. Stats keys are 16–512 characters from `[A-Za-z0-9._~+/=_-]`, without whitespace or control characters. The response is a JSON object with a `columns` array of names and a `results` array whose first element is one row of values; series values are `[[time string, number], …]`. See the column names and synthetic data in `app/src/test/resources/stats-sample.json`. The UI says "Stand HH:mm", not "live".
- The parser maps by column name (`columns`), never by position.
- **To add a stat:** add the column to your stats endpoint (aggregate-only), then in `StatsParser`, its synthetic sample in the test resources and a cell on the screen. The "Tasks · 24 h" tile is the sum of the `tasks_per_hour_24h` series, not a separate column.

## Design rules

- **Colours:** home and screensaver use the light Mobilerun palette: background #FAFAF8, surfaces #FFFFFF, ink #0A0A0A, secondary #4A4A4A, muted #6B6B6B and lines #E8E6E1. `MobilerunBlue` #1D4ED8 is the only accent, for serif words, the today bar, sparkline and focused tile fill. Focused tiles keep white text and the normal line border.
- **Typography:** bundled Geist for clocks, numbers and body text (light weights and tabular numerals for numbers), JetBrains Mono for labels, and Instrument Serif italic for blue accent words. Use the shared annotated-text helper and `StatLabels.accent` mapping; unaccented text uses the plain String path. Font licences are under `licenses/` and bundled in APK raw resources.
- **Brand:** use the black Mobilerun wordmark on both screens and the Mobilerun mark for the app icon/banner, preserving the supplied SVG paths as vector drawables.
- **Layout:** sized in `cq` units (1/100 of the screen width), German texts, de-DE number grouping with a narrow separator.
- **Motion budget:**
  - Rolling digits and the blue "+N" only on real changes, never on the first load.
  - The sparkline draws in only when new data arrives.
  - The only infinite animations are the sparkline end dot and the dream progress line. Each must have its own `graphicsLayer()` so it does not redraw the whole screen.
  - Night dimming uses a black scrim, not layer alpha, which would allocate a full-screen offscreen buffer.
  - With the system animator scale at 0, show the final states.
- **Never use** blur, video or large bitmaps. The burn-in drift (±6 dp every 3 min) and the night dim (22:00–07:00) stay, because the TV is on for hours.
- **Measure PSS after every visual change:** right after start and again after 2 min. A full-screen redraw per frame can more than double memory use.

## Improving power and standby behaviour

- Screen-off and sleep timeouts are system settings, not app code: `system screen_off_timeout`, `secure sleep_timeout`, `secure contextual_screen_off_timeout`, in ms. For example 18000000 (5 h).
- Opening the Sleep menu in Fire OS settings can overwrite these values.
- The TV has its own auto power-off (`secure SHUTDOWN_TIMER_SETTING`). Check it in the TV's power menu.
- Log old values before you change any device setting so they can be restored.

## Debugging checklist

| Symptom | Check |
|---|---|
| Amazon home stays | `scripts/tv.sh status` shows the service under Enabled/Bound; for a crash loop look at `adb logcat -d \| grep -E 'FATAL\|officetv'` |
| "Statistik nicht konfiguriert" | set `OFFICETV_STATS_URL`, rebuild and reinstall |
| "Stats-Schlüssel fehlt" / "abgelehnt" | key not provisioned or rotated; run `scripts/tv.sh key` |
| Stats frozen | "Stand" time; backoff after network errors retries within ≤60 min |
| AirPlay does not connect | `adb logcat \| grep -E 'airplaydaemon\|pair-pin\|lowmemorykiller'`; PIN UI killed → reduce memory |
| Screen does not change after deploy | `install -r` succeeded? Open the app (`am start -n ai.droidrun.officetv/.MainActivity`) |
| `adb` offline / refused | TV rebooted → re-enable network ADB on the TV |

## Contributing

- Branch from `main`, open a PR against `main`, and use small commits (`type(scope): message`).
- Add a screenshot from the real TV and the PSS numbers to the PR.
- Changes to the key handling, the receiver (protected by `android.permission.DUMP`) or the takeover service need a security-minded review.
- The debug build is deliberately non-debuggable so the key cannot be dumped from the heap. Keep it that way.

## Calendar integration

Home features the calendar below clock/date on the left: mono “Nächster Termin” with
serif accent, a very large blue Instrument Serif italic title (two ellipsized lines),
large Geist time range, blue countdown/status and a thin progress bar. The bar fills
during the final 60 minutes before the event, then shows elapsed meeting share. It is
unanimated and uses the existing minute clock. A muted “Danach: HH:mm” plus serif title
shows the following event. Empty/unreachable states use larger muted text; unconfigured
calendars stay hidden. The header keeps only the AirPlay readiness pill alongside the
brand; no mirroring instructions or network name. Only Einstellungen (first/focused)
and Fire TV Startseite are the only buttons in the bottom bar.
Verify the two-line title, following event and empty states at 1920×1080, then measure
PSS immediately and after two minutes, keeping it below 100 MB.

Setup, provisioning, rotation and troubleshooting live in the [setup skill](../google-calendar-setup/SKILL.md)
and the [calendar integration guide](../../../integrations/google-calendar/README.md); do not repeat them here.

## Silent calendar reminders

- Enable the existing accessibility service with `scripts/tv.sh enable`. While it is alive
  and the screen is on, it collects the shared calendar repository even over other apps.
  UI and service collectors share one five-minute poll/backoff; screen-off pauses polling
  and reminder scheduling, and screen-on resumes the existing schedule.
- One minute before today's next upcoming event, a top-centre white accessibility overlay
  shows "In 1 Min.", its title in blue Instrument Serif italic, and local `HH:mm–HH:mm` times.
  It slides/fades in for 400 ms, holds 10 s, fades out for 600 ms, then removes itself.
  Animator scale 0 shows/removes it instantly. It has no sound, focus, key or touch handling.
- Turning on the TV inside that one-minute window shows it immediately; already-started
  events are skipped. Start instant + title is remembered once for the day in process
  memory, including service reconnects; a process restart clears that memory.
- Cards with the same start queue in order, each retaining its full lifetime. A failed
  window addition retries after 15 s while the event is still upcoming. Clock/time-zone
  changes replan the pending coroutine. Text stays bounded to the card at large font scale.
- `TV=<address:port> scripts/tv.sh reminder-test` uses the existing DUMP-protected receiver
  to show the fixed synthetic "Test Review" card, starting in one minute. No calendar
  configuration is required. The screen and accessibility service must be on.
  An already visible card is preserved; the test command reports unavailable until it ends.
- Verify over Netflix, AirPlay and the screensaver; remote navigation must still reach
  the underlying app. Verify removal after about 11 s, screen-off pause, late screen-on,
  reduced motion, a real event without duplicates, and PSS after removal and after 2 min.
  Service destruction must also remove any visible card.
