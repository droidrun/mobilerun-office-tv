#!/usr/bin/env bash
# Contributor deploy loop and owner-only TV setup/recovery.
set -euo pipefail

ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
APK=${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}
STATS_KEY_FILE=${STATS_KEY_FILE:-$HOME/.config/firetv-launcher/stats-key}
CALENDAR_URL_FILE=${CALENDAR_URL_FILE:-$HOME/.config/firetv-launcher/calendar-url}
STATE=$HOME/.config/firetv-launcher/tv-changes.log
PKG=ai.droidrun.officetv
SVC=$PKG/$PKG.HomeTakeoverService
DREAM=$PKG/$PKG.OfficeDreamService
SETTINGS=(enabled_accessibility_services accessibility_enabled screensaver_components screensaver_enabled)

usage() {
  echo "Usage: TV=<address:port> $0 build|deploy|install|key|calendar|reminder-test|enable|dream|status|screenshot|revert"
  echo "build needs no TV. deploy builds and installs; owner setup is separate."
}

build() { (
  # OFFICETV_* settings, including OFFICETV_DEMO, pass to Gradle via the environment.
  if [ -f "$HOME/.config/firetv-launcher/env" ]; then
    set -a
    # shellcheck source=/dev/null
    source "$HOME/.config/firetv-launcher/env"
    set +a
  fi
  if [ -z "${OFFICETV_STATS_URL:-}" ]; then
    echo "Warning: OFFICETV_STATS_URL is empty in the environment; configure it via ~/.gradle/gradle.properties or local.properties or statistics will be disabled." >&2
  fi
  cd "$ROOT"
  ./gradlew assembleDebug
); }
# ADB joins arguments into a command parsed again by the TV's shell.
# POSIX single quotes preserve empty strings, quotes and shell metacharacters.
a() {
  local argument command="" quoted
  for argument in "$@"; do
    quoted=${argument//\'/\'\\\'\'}
    command+=" '$quoted'"
  done
  adb -s "$TV" shell "$command" | tr -d '\r'
}
provision_result() {
  case "$1" in
    *'data="stored"'*) echo "stored" ;;
    *'data="cleared"'*) echo "cleared" ;;
    *'data="invalid"'*) echo "invalid"; return 1 ;;
    *) echo "Provisioning failed: no receiver result (install the current APK)." >&2; return 1 ;;
  esac
}
install() {
  adb -s "$TV" install -r "$APK"
  a am start -n "$PKG/.MainActivity"
  echo "Test Einstellungen and Fire TV Startseite on the TV before enabling takeover."
}

# Tab-separated originals are scoped to the ADB address, never evaluated as shell code.
original() {
  [ -f "$STATE" ] || return 1
  awk -F '\t' -v tv="$TV" -v setting="$1" '
    $1 == tv && $2 == setting { print $3; found=1; exit }
    END { if (!found) exit 1 }
  ' "$STATE"
}

save_originals() {
  local setting value snapshot=""
  if original "${SETTINGS[0]}" >/dev/null; then return; fi
  # Read all settings successfully before recording or changing anything.
  for setting in "${SETTINGS[@]}"; do
    value=$(a settings get secure "$setting")
    snapshot+=$(printf '%s\t%s\t%s' "$TV" "$setting" "$value")$'\n'
  done
  (umask 077; mkdir -p "$(dirname "$STATE")"; printf '%s' "$snapshot" >> "$STATE")
}

case "${1:-}" in
  build) build; exit ;;
  deploy|install|key|calendar|reminder-test|enable|dream|status|screenshot|revert) ;;
  *) usage >&2; exit 1 ;;
esac

if [ -z "${TV:-}" ]; then
  echo "Set TV to the TV's ADB address and port (export TV=<address:port>)." >&2
  exit 1
fi
case "$1:$TV" in
  key:*:*|calendar:*:*)
    if [ "${TV_TRANSPORT_TRUSTED:-}" != 1 ]; then
      echo "Refusing secret provisioning over network ADB: legacy network ADB is unencrypted. Prefer TLS pairing or a wired/isolated link; set TV_TRANSPORT_TRUSTED=1 only after verifying the transport." >&2
      exit 1
    fi
    ;;
esac
if [ "$1" = deploy ]; then build; fi
# Wired ADB serials are already connected; network targets include a port.
case "$TV" in *:*) adb connect "$TV" ;; esac

case "$1" in
  deploy|install) install ;;
  reminder-test)
    result=$(a am broadcast -a ai.droidrun.officetv.SHOW_TEST_REMINDER -n "$PKG/.ControlReceiver")
    case "$result" in
      *'data="shown"'*) echo "Test reminder shown for 10 seconds." ;;
      *) echo "Reminder unavailable: check the screen, accessibility service and current APK, or wait for the visible card to finish." >&2; exit 1 ;;
    esac
    ;;
  key)
    [ -s "$STATS_KEY_FILE" ] || { echo "Missing key file: $STATS_KEY_FILE" >&2; exit 1; }
    # Use a trusted TLS/wired transport. The key reaches ADB only through stdin.
    # Expand $k on the TV, never on the host.
    # shellcheck disable=SC2016
    result=$(adb -s "$TV" shell 'IFS= read -r k; am broadcast -a ai.droidrun.officetv.SET_STATS_KEY -n ai.droidrun.officetv/.ControlReceiver --es key "$k"' < "$STATS_KEY_FILE" | tr -d '\r')
    provision_result "$result"
    ;;
  calendar)
    [ -s "$CALENDAR_URL_FILE" ] || { echo "Missing calendar URL file: $CALENDAR_URL_FILE" >&2; exit 1; }
    # Trusted TLS/wired transport only. The URL is read on the TV, never expanded on the host.
    # shellcheck disable=SC2016
    result=$(adb -s "$TV" shell 'IFS= read -r u; am broadcast -a ai.droidrun.officetv.SET_CALENDAR_URL -n ai.droidrun.officetv/.ControlReceiver --es url "$u"' < "$CALENDAR_URL_FILE" | tr -d '\r')
    provision_result "$result"
    ;;
  enable)
    save_originals
    services=$(a settings get secure enabled_accessibility_services)
    case ":$services:" in
      *":$SVC:"*) ;;
      :null:|::) services=$SVC ;;
      *) services=$services:$SVC ;;
    esac
    a settings put secure enabled_accessibility_services "$services"
    a settings put secure accessibility_enabled 1
    echo "Takeover enabled. Test HOME, both escape tiles and long-press OK."
    ;;
  dream)
    save_originals
    a settings put secure screensaver_components "$DREAM"
    a settings put secure screensaver_enabled 1
    a am start -n com.android.systemui/.Somnambulator
    ;;
  status)
    calendar_status=$(a am broadcast -a ai.droidrun.officetv.CALENDAR_STATUS -n "$PKG/.ControlReceiver")
    case "$calendar_status" in
      *'data="set"'*) echo "Calendar URL: set" ;;
      *'data="unset"'*) echo "Calendar URL: unset" ;;
      *) echo "Calendar URL: unknown (install a calendar-capable build)" ;;
    esac
    a dumpsys package "$PKG" | awk '/versionCode=|versionName=/'
    a dumpsys accessibility | awk '/[Bb]ound services|HomeTakeoverService/'
    echo "Accessibility services: $(a settings get secure enabled_accessibility_services)"
    echo "Screensaver: $(a settings get secure screensaver_components)"
    a dumpsys meminfo "$PKG" | awk '/TOTAL PSS:|^[[:space:]]*TOTAL[[:space:]]/'
    ;;
  screenshot)
    adb -s "$TV" exec-out screencap -p > tv-screenshot.png
    echo "Saved tv-screenshot.png in $PWD (keep it out of commits)."
    ;;
  revert)
    # Refuse to uninstall if any original is missing; never guess the TV's settings.
    for setting in "${SETTINGS[@]}"; do
      original "$setting" >/dev/null || { echo "No saved originals for $TV in $STATE; restore settings manually before uninstalling." >&2; exit 1; }
    done
    for setting in "${SETTINGS[@]}"; do
      value=$(original "$setting")
      if [ -z "$value" ] || [ "$value" = null ]; then
        a settings delete secure "$setting"
      else
        a settings put secure "$setting" "$value"
      fi
    done
    adb -s "$TV" uninstall "$PKG"
    a am start -a android.intent.action.MAIN -c android.intent.category.HOME -p com.amazon.tv.launcher
    printf '# reverted %s %s\n' "$TV" "$(date '+%F %T')" >> "$STATE"
    ;;
esac
