const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

const GuestStatus = { OWNER: 'OWNER', YES: 'YES', MAYBE: 'MAYBE', INVITED: 'INVITED', NO: 'NO' };
const context = { CalendarApp: { GuestStatus, Visibility: { PRIVATE: 'PRIVATE', CONFIDENTIAL: 'CONFIDENTIAL' } } };
runInNewContext(readFileSync(`${__dirname}/Code.gs`, 'utf8'), context);
const event = (status, creators = []) => ({ getMyStatus: () => status, getCreators: () => creators });

test('unanswered invitations are hidden unless a domain is explicitly configured', () => {
  const invitation = event('INVITED', ['organizer@example.com']);
  assert.equal(context.isTrusted(invitation, ''), false);
  assert.equal(context.isTrusted(invitation, 'example.com'), true);
  for (const invalid of ['@example.com', '*.example.com', 'example.com,other.example', 'https://example.com']) {
    assert.equal(context.isTrusted(invitation, invalid), false);
  }
});

test('every creator must belong to the exact trusted domain', () => {
  for (const creators of [[], ['organizer@other.example'], ['organizer@sub.example.com'],
    ['organizer@fakeexample.com'], ['organizer@example.com', 'outside@other.example']]) {
    assert.equal(context.isTrusted(event('INVITED', creators), 'example.com'), false);
  }
  assert.equal(context.isTrusted(event('INVITED', ['organizer@EXAMPLE.COM', 'second@example.com']), 'example.com'), true);
});

test('owner, accepted and tentative meetings remain visible; declined meetings stay hidden', () => {
  for (const status of ['OWNER', 'YES', 'MAYBE']) {
    assert.equal(context.isTrusted(event(status), ''), true);
  }
  assert.equal(context.isTrusted(event('NO', ['organizer@example.com']), 'example.com'), false);
});

test('private and confidential titles stay hidden', () => {
  for (const visibility of ['PRIVATE', 'CONFIDENTIAL']) {
    assert.equal(context.isHidden({ getVisibility: () => visibility }), true);
  }
  assert.equal(context.isHidden({ getVisibility: () => 'DEFAULT' }), false);
});
