/** Read-only, token-protected display contract. Never log tokens or calendar data. */
function doGet(e) {
  const expected = PropertiesService.getScriptProperties().getProperty('TOKEN');
  const supplied = e && e.parameter && e.parameter.token;
  if (!sameToken(expected, supplied)) return json({});

  try {
    const now = new Date();
    const calendar = CalendarApp.getDefaultCalendar();
    // Unanswered invitations are hidden unless the owner explicitly trusts one domain.
    const domain = (PropertiesService.getScriptProperties().getProperty('TRUSTED_DOMAIN') || '').trim().toLowerCase();
    const zone = calendar.getTimeZone();
    // Advance the calendar date, not the instant: today can be 23 or 25 hours long.
    const today = Utilities.formatDate(now, zone, 'yyyy-MM-dd');
    const tomorrow = new Date(today + 'T12:00:00Z');
    tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
    const endOfToday = Utilities.parseDate(
      Utilities.formatDate(tomorrow, 'UTC', 'yyyy-MM-dd') + ' 00:00', zone, 'yyyy-MM-dd HH:mm');
    const events = calendar.getEvents(now, endOfToday)
      .filter(event => event.getEventType() === CalendarApp.EventType.DEFAULT && !event.isAllDayEvent() &&
        isTrusted(event, domain) && event.getEndTime() > now &&
        Utilities.formatDate(event.getStartTime(), zone, 'yyyy-MM-dd') === today)
      .sort((a, b) => a.getStartTime() - b.getStartTime())
      .slice(0, 2)
      .map(event => ({
        title: isHidden(event) ? 'Termin' : event.getTitle().trim() || 'Termin',
        start: event.getStartTime().toISOString(),
        end: event.getEndTime().toISOString()
      }));
    return json({ generatedAt: now.toISOString(), next: events[0] || null, then: events[1] || null });
  } catch (_) {
    return json({});
  }
}

// Trust is opt-in: never infer it from the calendar account (including consumer domains).
function isTrusted(event, domain) {
  const status = event.getMyStatus();
  if (status === CalendarApp.GuestStatus.OWNER || status === CalendarApp.GuestStatus.YES ||
      status === CalendarApp.GuestStatus.MAYBE) return true;
  if (status !== CalendarApp.GuestStatus.INVITED || !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/.test(domain)) return false;
  const creators = event.getCreators();
  return creators.length > 0 && creators.every(email => email.toLowerCase().endsWith('@' + domain));
}

// Private or confidential events show only a neutral title on the shared screen.
function isHidden(event) {
  const visibility = event.getVisibility();
  return visibility === CalendarApp.Visibility.PRIVATE || visibility === CalendarApp.Visibility.CONFIDENTIAL;
}

function sameToken(expected, supplied) {
  if (typeof expected !== 'string' || !expected.length ||
      typeof supplied !== 'string' || expected.length !== supplied.length) return false;
  let different = 0;
  for (let i = 0; i < expected.length; i++) {
    different |= expected.charCodeAt(i) ^ supplied.charCodeAt(i);
  }
  return different === 0;
}

function json(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
