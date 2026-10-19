---
name: google-calendar-setup
description: Set up the read-only Google Apps Script calendar endpoint for any Google account, provision an office TV, rotate its token, or troubleshoot empty responses, redirects and quotas.
---

# Google Calendar setup

Use the owner's default calendar. Google Calendar → token-protected read-only Apps Script →
restricted HTTPS redirect → `CalendarRepository` → private snapshot → home/screensaver.
The [integration README](../../../integrations/google-calendar/README.md) is the single
reference for deployment options, secret format, transport, rotation and troubleshooting.

1. Have the owner sign into the intended Google account and create a standalone Apps Script.
2. Copy `integrations/google-calendar/Code.gs` and the supplied `appsscript.json` manifest
   (enable manifest visibility in Project Settings). Keep read-only calendar scope.
3. Generate a local token with `openssl rand -hex 32`; save it as Script Property `TOKEN`.
   Unanswered invitations (`INVITED`) are hidden by default. Only if the owner wants them,
   set optional Script Property `TRUSTED_DOMAIN` to one organization-controlled domain,
   e.g. `example.com`; all creators must match it. Never use consumer domains such as
   `gmail.com`. OWNER/YES/MAYBE remain visible; private/confidential titles stay “Termin”.
4. Deploy a Web app, execute as **Me**, access **Anyone**; authorize and copy the `/exec` URL.
5. Form the token-bearing URL privately and save it outside the repo to
   `~/.config/firetv-launcher/calendar-url`, mode 600, one newline-terminated line.
6. Install the APK, choose trusted TLS/wired ADB, then run `scripts/tv.sh calendar` and
   `scripts/tv.sh status`. Network serials require `TV_TRANSPORT_TRUSTED=1` after verification.
   Expect `stored`; `invalid` exits non-zero. See the integration README for overrides.
7. Check future/running/empty events on home and screensaver; match TV/calendar time zones.
8. Rotate by changing Script Property `TOKEN`, updating the private file and re-provisioning.
   Update the web-app deployment version after source changes.

Never commit or paste real URLs, tokens, account names or event data. Keep secrets out of
build settings, APKs, command arguments, screenshots and logs. The README supplies fake
examples. For `{}` check token/property privately first, then service failures; for 302
check anonymous access and the single allowed Google content-host hop. For quotas, inspect
executions privately and respect the polling backoff. Use the README's troubleshooting
and clear instructions rather than relaxing authentication or redirect restrictions.
