import { describe, it, expect, beforeEach } from 'vitest';


let state = { issueType: 'Story' };

function tcSetGenerateIssueType(type) {
  const supported = ['Story', 'Technical Task', 'Bug'];
  if (supported.indexOf(type) < 0) type = 'Story';
  state.issueType = type;
}

function buildGenerateBody(inputText) {
  return JSON.stringify({
    inputText: inputText,
    attachmentSection: '',
    issueType: state.issueType || 'Story',
  });
}

describe('Ticket-Creation - issueType forwarding to /api/ticket-creation/generate', () => {
  beforeEach(() => {
    state = { issueType: 'Story' };
  });

  it('forwards "Bug" verbatim when the user picks Bug in the segmented control', () => {
    tcSetGenerateIssueType('Bug');
    const body = JSON.parse(buildGenerateBody('Login form throws 500'));

    expect(body.issueType).toBe('Bug');
    expect(body.inputText).toBe('Login form throws 500');
  });

  it('forwards "Technical Task" verbatim when the user picks Technical Task', () => {
    tcSetGenerateIssueType('Technical Task');
    const body = JSON.parse(buildGenerateBody('Refactor the auth module'));

    expect(body.issueType).toBe('Technical Task');
    expect(body.inputText).toBe('Refactor the auth module');
  });

  it('forwards "Story" verbatim when the user picks Story', () => {
    tcSetGenerateIssueType('Story');
    const body = JSON.parse(buildGenerateBody('Add SSO login'));

    expect(body.issueType).toBe('Story');
    expect(body.inputText).toBe('Add SSO login');
  });

  it('falls back to "Story" when the segmented control has not been initialised yet', () => {
    state = { issueType: undefined };
    const body = JSON.parse(buildGenerateBody('Anything'));

    expect(body.issueType).toBe('Story');
  });

  it('coerces an unknown value to "Story" when the helper is used', () => {
    tcSetGenerateIssueType('Epic');
    const body = JSON.parse(buildGenerateBody('Add feature'));

    expect(body.issueType).toBe('Story');
    expect(state.issueType).toBe('Story');
  });

  it('preserves the issue type across rapid toggles (no stale closure)', () => {
    tcSetGenerateIssueType('Bug');
    tcSetGenerateIssueType('Technical Task');
    tcSetGenerateIssueType('Story');

    const body = JSON.parse(buildGenerateBody('Pick me'));
    expect(body.issueType).toBe('Story');
  });
});

describe('Ticket-Creation - wire format compatibility with backend IssueType.fromString', () => {
  beforeEach(() => {
    state = { issueType: 'Story' };
  });

  it.each([
    ['Bug', 'Bug'],
    ['Story', 'Story'],
    ['Technical Task', 'Technical Task'],
  ])('"%s" is the canonical wire value the backend expects', (input, expected) => {
    tcSetGenerateIssueType(input);
    const body = JSON.parse(buildGenerateBody('x'));
    expect(body.issueType).toBe(expected);
  });
});
