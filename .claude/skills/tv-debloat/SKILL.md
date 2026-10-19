---
name: tv-debloat
description: Plan and optionally reduce telemetry and unused packages on an owner-controlled Fire TV or Android TV with ADB, keeping exact local recovery records and verifying HOME, remote and AirPlay.
---

# Optional TV debloat and privacy

Use for an owner's request to reduce telemetry, advertising or unused background features.
This is optional, reversible work at the owner's risk, not a prerequisite for the launcher.
Firmware updates may reset settings or re-enable packages. Package names and effects differ
by firmware; a name alone does not establish that a package is safe to disable.

## Identify and plan first

Ask for or verify `TV=<address:port>` on a trusted ADB connection. Confirm the intended TV
with the owner using its model and firmware; never save addresses, serials or real device
data inside the repository. See the [ADB guide](../../../README.md#enable-adb-on-the-tv).

```sh
export TV='<address:port>'
adb connect "$TV"
adb -s "$TV" get-state
adb -s "$TV" shell 'getprop ro.product.model'
adb -s "$TV" shell 'getprop ro.build.display.id'
adb -s "$TV" shell 'pm list packages --user 0'
adb -s "$TV" shell 'pm list packages -d --user 0'
adb -s "$TV" shell 'settings get secure USAGE_METRICS_UPLOAD_ENABLED'
adb -s "$TV" shell 'settings get secure usage_metrics_marketing_enabled'
adb -s "$TV" shell 'settings get secure pact_usage_collection_enabled'
adb -s "$TV" shell "settings get secure 'settingsApp:appUsageData:value'"
adb -s "$TV" shell "settings get secure 'settingsApp:deviceUsageData:value'"
adb -s "$TV" shell "settings get secure 'advertisingIdApp:interestBasedAds:value'"
```

Show a plan containing every current value, package presence and enabled state, group,
reason, expected effect and rollback action. Skip settings that read `null` (unsupported),
settings already `0`, absent packages and already-disabled packages. An ADB or permission
error is not a value or proof of absence: stop and resolve it. Get the owner's explicit
OK for the concrete plan before any device mutation. Apply only approved entries.

## Privacy settings

Set only these existing **secure** keys to `0` to opt out of the firmware's usage/marketing
collection and interest-based advertising controls. Their precise effect is firmware-dependent;
this does not promise to block every network request or all telemetry.

- `USAGE_METRICS_UPLOAD_ENABLED`
- `usage_metrics_marketing_enabled`
- `pact_usage_collection_enabled`
- `settingsApp:appUsageData:value`
- `settingsApp:deviceUsageData:value`
- `advertisingIdApp:interestBasedAds:value`

Never change `screensaver_enabled`, power, sleep, networking or permissions as part of this
procedure. For each approved key, record its exact old value first, then write and read back:

```sh
adb -s "$TV" shell "settings put secure '<approved-key>' '0'"
adb -s "$TV" shell "settings get secure '<approved-key>'"
```

## Optional package groups

These are candidates, not a universal removal list. Explain each present candidate to the
owner; unused features in particular require confirmation that the owner does not need them.
Use exact package names, never wildcard disabling or removal.

Disabling `com.amazon.ftv.screensaver` means the stock screensaver no longer works. If the owner
later reverts this launcher's screensaver setting, re-enable that package too (or leave it
enabled).

| Group | Reason | Candidates |
|---|---|---|
| Telemetry / logging | Reduce optional usage reporting and background diagnostic collection; disabling diagnostic services can reduce troubleshooting information. | `com.amazon.device.crashmanager`, `com.amazon.device.logmanager`, `com.amazon.device.metrics`, `com.amazon.device.telemetry.emitter`, `com.amazon.dp.logger`, `com.amazon.logan`, `com.amazon.ceviche`, `com.amazon.neo.minerva`, `com.amazon.neo.minerva.logcat`, `com.amazon.perfc`, `com.amazon.perfcollection`, `com.amazon.wirelessmetrics.service`, `com.fireos.usagestats.proxy` |
| Automatic content recognition | Opt out of the optional service that recognizes viewed content. | `com.amazon.tv.acr` |
| Ads / shopping / autoplay previews | Reduce optional advertising, shopping and promotional preview services. | `com.amazon.ftvads.deeplinking`, `com.amazon.hybridadidservice`, `com.amazon.kso.blackbird`, `com.amazon.shoptv.client`, `com.amazon.sneakpeek`, `com.amazon.minitv.android.app` |
| Unused features | Free resources only when the owner does not use the stock screensaver, gaming hub, tutorials, demo/support/early-access or smart-home features. | `com.amazon.ftv.screensaver`, `com.amazon.gamehub`, `com.amazon.tmm.tutorial`, `com.amazon.storm.lightning.tutorial`, `com.amazon.tv.democontent.provider`, `com.amazon.tv.csapp`, `com.amazon.tv.earlyaccess`, `com.amazon.tv.matter`, `com.amazon.tv.mattercohost`, `com.amazon.smarthomemapviewapp` |

### Protected packages: never touch

Check every candidate against this list **immediately before every change or rollback**.
If its role is uncertain or overlaps a protected capability, skip it, even if listed above.

- Launchers: `com.amazon.tv.launcher`, `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`.
- This app: `ai.droidrun.officetv`.
- Settings: `com.android.settings`, `com.amazon.tv.settings` and `com.amazon.tv.settings.v2` (exact names; no substring matching).
- Input methods, remote control, Bluetooth/input services and their dependencies.
- AirPlay, including `com.apple.airplay.amazon.airplay`; Cast and DIAL receivers/discovery.
- OTA/system updates and their dependencies.
- Alexa/voice, Live TV and HbbTV and their dependencies.

The latter roles have different package names across devices. Identify them from the device's
package metadata and firmware documentation; never guess from a substring or expand the
candidate list. Keep protected capabilities intact. Do not change the HOME activity.

Prefer disabling for user 0. Read back its disabled state. Only if disabling fails, and the
owner's approved plan allows fallback, uninstall **for user 0 with data retained**, never
system-wide. Inspect the command result and user-0 package list; shell exit status alone
may not indicate package-manager success.

```sh
adb -s "$TV" shell "pm disable-user --user 0 '<approved-package>'"
# Fallback only after a failed disable and confirmation it did not already disable the package:
adb -s "$TV" shell "pm uninstall -k --user 0 '<approved-package>'"
```

## Local recovery log: before every change

Use a private **untracked file outside the repo**, e.g.
`~/.config/firetv-launcher/debloat-changes.jsonl`, separate from `tv.sh`'s TSV recovery log.
Create its directory and file with `umask 077`; keep it mode 600. Group entries by TV address
and confirmed model/firmware locally. On first change record the exact old setting value,
including empty versus literal `null`; for packages record whether installed for user 0 and
its original enabled state. Do not overwrite originals on a repeated apply.

For example, append a JSON record for an approved setting, with `key` and `old` populated
from the successful read (remove ADB transport CR, not spaces or other value content):

```sh
umask 077
mkdir -p "$HOME/.config/firetv-launcher"
LOG="$HOME/.config/firetv-launcher/debloat-changes.jsonl"
python3 -c 'import json,sys; print(json.dumps({"tv":sys.argv[1],"kind":"setting","key":sys.argv[2],"old":sys.argv[3]}))' "$TV" "$key" "$old" >> "$LOG"
```

Before a package change, log the original state and intended method (`disable-user` or
`uninstall-user0`), then append its verified result. Record a fallback attempt separately.
If a command is interrupted, reconcile actual state against that pending record before
retrying or reverting; never assume success. Keep the log until recovery is verified.
Do not `source` or `eval` the log. If logging fails, do not change the TV.

### Quoting through ADB

ADB shell commands are parsed a second time on the TV. Host-side `"$value"` alone does
not protect that second parse. Surround each remote argument with POSIX single quotes;
escape an embedded `'` as `'\''`. The fixed-key/package examples above show remote quoting.
For an exact saved setting value (not arbitrary shell code), escape it before restoring:

```sh
# key must be one of the six approved fixed keys, old must come from the local JSON log.
remote_old=${old//\'/\'\\\'\'}
adb -s "$TV" shell "settings put secure '$key' '$remote_old'"
```

Use Bash for that example. Never interpolate unvalidated package/key names or run commands
copied from device output. Preserve empty values and special characters in the local log.

## Verify and revert

After each small group, check the remote's navigation, HOME, Settings, both launcher escape
tiles and AirPlay mirroring including its PIN UI. Stop and restore the group on regression.
Compare free memory and HOME with the baseline; query service discovery without probing
device HTTP ports:

```sh
adb -s "$TV" shell 'cat /proc/meminfo' | grep MemAvailable
adb -s "$TV" shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
adb mdns services
```

Look for `_airplay._tcp` belonging to this TV. Some ADB versions list only ADB services;
absence there is **inconclusive**, not proof AirPlay stopped. Use the launcher's existing
AirPlay readiness indicator and an actual mirroring test to verify discovery on the TV.
Recheck after reboot and firmware updates, including PSS (`scripts/tv.sh status`).

To revert, use only this TV's confirmed log entries. Restore each changed setting to its
**exact old value**, using the quoting above. If the logged original is `null`, delete that
key instead (`settings delete secure '<approved-key>'`); an empty string must be restored
as `''`, not deleted. Do not guess `1` as the original. If a key or package was logged more than once, the FIRST logged old value is authoritative. Re-enable only packages this procedure
disabled; restore only user-0 installs it removed:

```sh
adb -s "$TV" shell "pm enable --user 0 '<logged-disabled-package>'"
adb -s "$TV" shell "cmd package install-existing --user 0 '<logged-uninstalled-package>'"
```

Restore the original enabled state if it was not the normal enabled state. Never enable
pre-existing disabled packages. Refuse to guess without a matching log entry, and verify
read-back, HOME, remote and AirPlay again. Keep recovery records; firmware updates or a
changed ADB address require matching the original TV before using them.
