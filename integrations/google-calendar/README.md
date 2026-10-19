# Google Calendar display endpoint

This read-only Apps Script can be deployed by any Google Calendar account owner. It returns
only today's next two timed events from that account's **default calendar**: title, start and end.
Only regular meetings (`EventType.DEFAULT`) are included. All-day, declined, ended, out-of-office,
focus-time and working-location events are skipped; recurring instances are resolved by Google.
Unanswered invitations (`INVITED`) are **hidden by default**. Optional Script Property
`TRUSTED_DOMAIN` (one domain, e.g. `example.com`) permits them only when every creator
is from that exact domain. Do not use consumer domains such as `gmail.com`: any account
there could send text to the shared screen. Owner, accepted and tentative meetings remain visible. Untitled, private and confidential events display as “Termin”.
Calendar-day boundaries use the calendar's own time zone. Set the TV to the same time zone:
the TV additionally filters by its local day and never shows tomorrow's events.

`example-response.json` contains fake events and dates only. With no events, `next` and `then`
are both `null`. Invalid/missing tokens and authenticated service failures return `{}`. There are no guests, descriptions,
locations, links or account identifiers in the response.

## Set up

1. Sign into the Google account whose default calendar should appear on the TV. Create a
   standalone project at [Google Apps Script](https://script.google.com/).
2. Replace `Code.gs` with the supplied file. Open Project Settings and enable **Show
   appsscript.json manifest file in editor**. Replace that manifest with `appsscript.json`.
   The manifest requests only `calendar.readonly`; its neutral script time zone does not
   override the calendar's own time zone. Execution exception logging is disabled.
3. Generate a token locally with `openssl rand -hex 32`. In Project Settings → Script
   Properties, add `TOKEN` with that value. Keep it out of source, logs, screenshots and chat.
   Optionally add `TRUSTED_DOMAIN` with a single organization-controlled domain (no `@`,
   scheme, subdomain wildcard or list). Leave it unset to hide all unanswered invitations.
4. Choose **Deploy → New deployment → Web app**. Set **Execute as: Me** and **Who has
   access: Anyone**. Authorize the read-only calendar permission, then deploy. If account or
   organization policy disallows anonymous web apps, this integration needs that policy
   resolved by the owner; do not change the TV to follow login redirects.
5. Copy the deployment URL ending in `/exec` (not `/dev`). Form `EXEC_URL?token=TOKEN`.
   This is the clearly fake format example, with literal placeholders that must be replaced:

   ```text
   https://script.google.com/macros/s/EXEC_ID/exec?token=TOKEN
   ```

   `EXEC_ID` must be the real deployment ID (20–200 letters, digits, `_` or `-`);
   `TOKEN` must be 32–128 alphanumeric characters. Never commit or paste the real URL.
6. Outside any repository, create `~/.config/firetv-launcher/calendar-url` containing the
   full URL on one line, ending with a newline. Create the directory with `mkdir -p
   ~/.config/firetv-launcher`, use `umask 077` before saving, and run
   `chmod 600 ~/.config/firetv-launcher/calendar-url`. Use a local editor, not a shell
   command containing the expanded secret. Never use shell tracing for provisioning.
7. Install the calendar-capable APK, set `TV` to a trusted TLS/wired ADB connection, then run:

   ```sh
   scripts/tv.sh calendar
   scripts/tv.sh status
   ```

   Network serials (`host:port`) are refused for both `calendar` and `key` unless
   `TV_TRANSPORT_TRUSTED=1` is set. Legacy network ADB is unencrypted: prefer TLS pairing
   or a wired/isolated link, and set the override only after verifying that transport.
   A wired serial needs no override. Provisioning prints `stored` on success or `invalid`
   and exits non-zero on rejection/storage failure; clear broadcasts return `cleared`.
   `CALENDAR_URL_FILE` overrides the local file path. The URL goes through ADB stdin,
   a DUMP-protected receiver and AES/GCM AndroidKeyStore storage (`officetv_calendar`).
   It is never embedded in the APK or logged. The app hides calendar UI until provisioned.

## Rotate or clear

Set Script Property `TOKEN` to a new `openssl rand -hex 32` value, update the local URL
file, and rerun `scripts/tv.sh calendar`. Script properties take effect without a code
redeployment; the previous URL stops authenticating. Keep the file at mode 600.
For source changes, update the existing web-app deployment to a new script version.

To clear only the TV's URL:

```sh
adb -s "$TV" shell am broadcast -a ai.droidrun.officetv.CLEAR_CALENDAR_URL -n ai.droidrun.officetv/.ControlReceiver
```

## Troubleshoot

- `{}` usually means a missing/wrong token (or missing `TOKEN` Script Property). Check that
  privately first. Authenticated service/quota errors also return `{}`, without an error page
  or details; inspect Apps Script executions privately if credentials are correct. The TV
  retains its last good snapshot on failures; re-provisioning synchronously clears both
  persisted and in-memory cache before changing the URL, even while the screen is inactive.
- Apps Script ContentService normally issues a **302** to `https://script.googleusercontent.com/…`.
  The app follows exactly one absolute HTTPS hop to that host, with no credentials or
  non-default port; a second redirect or a login/other-host redirect fails. Do not add login
  cookies or relax the redirect rule. Check deployment is **Anyone**, not Google users only.
- Polling is every five minutes while home or screensaver is visible. Network, malformed-response
  and quota failures back off 1, 2, 4, 8, 16, 32, then 60 minutes; successful polling resets it.
  Check Apps Script executions/quotas privately if it stops responding. Quotas vary by account;
  do not repeatedly redeploy or provision to force retries.
- No meeting: check the **default** calendar, its time zone, the TV time zone, declined/all-day
  status, event type and whether the event starts today. Unanswered invitations require
  explicit `TRUSTED_DOMAIN` and matching creators; private/confidential titles show “Termin”.
- Display states: no provisioned URL hides the home block. A missing snapshot or one generated
  on a different local date shows “Kalender nicht erreichbar”. A snapshot generated today
  with no remaining events shows “Heute keine Termine mehr”. A usable cached snapshot from
  today stays visible during failures. Countdown omits zero minutes, e.g. “in 1 Std.”;
  while a meeting runs, the screensaver shows its end time large (“bis 15:30”).
- Offline: the TV keeps a small private last-good snapshot and recalculates countdowns each
  minute; ended and previous-day events disappear. Titles may persist locally, so clear app
  data when decommissioning the TV. The secret URL is never stored with snapshots.
  Polling resumes from the persisted generation time plus five minutes, so opening the screen
  does not force another request immediately after a recent successful fetch.
