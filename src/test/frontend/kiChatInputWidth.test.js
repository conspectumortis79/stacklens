import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const DRAWER = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/ki-chat-drawer.html',
);


const drawerSource = readFileSync(DRAWER, 'utf8');

function ruleBody(selector) {
  const re = new RegExp(`\\${selector}\\s*\\{([^}]+)\\}`, 'm');
  const m = drawerSource.match(re);
  return m ? m[1] : null;
}

describe('KI-Chat input footer flex layout', () => {
  it('.ki-chat-input declares min-width: 0 to defeat textarea intrinsic min-width', () => {
    const body = ruleBody('.ki-chat-input');
    expect(body, '.ki-chat-input CSS rule not found in drawer').not.toBeNull();
    expect(
      /min-width\s*:\s*0/.test(body),
      '.ki-chat-input must declare `min-width: 0`. Without it, the ' +
        '<textarea> retains min-width:auto (its intrinsic content ' +
        'size derived from cols) and refuses to fill the flex ' +
        'container, leaving empty space between the textarea and the ' +
        'send button.',
    ).toBe(true);
  });

  it('.ki-chat-input declares flex: 1 1 0% (explicit grow + basis 0%)', () => {
    const body = ruleBody('.ki-chat-input');
    expect(body).not.toBeNull();
    expect(
      /flex\s*:\s*1\s+1\s+0%/.test(body),
      '.ki-chat-input must declare `flex: 1 1 0%` (equivalent to ' +
        '`flex: 1` but with explicit basis). This ensures the ' +
        'textarea grows into the available space instead of stopping ' +
        'at its intrinsic content size.',
    ).toBe(true);
  });

  it('.ki-chat-input-footer declares min-width: 0', () => {
    const body = ruleBody('.ki-chat-input-footer');
    expect(body, '.ki-chat-input-footer CSS rule not found').not.toBeNull();
    expect(
      /min-width\s*:\s*0/.test(body),
      '.ki-chat-input-footer must declare `min-width: 0`. Without it, ' +
        'the footer refuses to shrink inside the drawer container, ' +
        'and the textarea inside it cannot fill the available space.',
    ).toBe(true);
  });

  it('footer layout is still flex row (display: flex, gap: 8px)', () => {
    const body = ruleBody('.ki-chat-input-footer');
    expect(body).not.toBeNull();
    expect(/display\s*:\s*flex/.test(body), 'footer must be display:flex').toBe(true);
    expect(/gap\s*:\s*8px/.test(body), 'footer must have gap: 8px').toBe(true);
  });
});