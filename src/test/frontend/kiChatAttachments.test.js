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
const MAIN_SCRIPT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/scripts-ki-chat.html',
);
const SEND_SCRIPT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/scripts-ki-chat-send.html',
);
const DE_I18N = JSON.parse(
  readFileSync(
    resolve(
      __dirname,
      '../../main/resources/static/i18n/de.json',
    ),
    'utf8',
  ),
);
const EN_I18N = JSON.parse(
  readFileSync(
    resolve(
      __dirname,
      '../../main/resources/static/i18n/en.json',
    ),
    'utf8',
  ),
);

const REQUIRED_DRAWER_IDS = [
  'kiChatDropOverlay',
  'kiChatDropOverlayTitle',
  'kiChatDropOverlaySubtitle',
  'kiChatAttachmentStrip',
  'kiChatAttachmentStripHead',
  'kiChatAttachmentStripClear',
  'kiChatAttachmentList',
  'kiChatAttachBtn',
];

const RETIRED_DRAWER_IDS = [
  'kiChatBrowserOverlay',
  'kiChatBrowserDialog',
  'kiChatBrowserHeader',
  'kiChatBrowserTitle',
  'kiChatBrowserPath',
  'kiChatBrowserList',
  'kiChatBrowserSelected',
  'kiChatBrowserConfirmBtn',
  'kiChatBrowserCancelBtn',
  'kiChatBrowserClose',
  'kiChatBrowserAlert',
];

const REQUIRED_MAIN_SCRIPT_TOKENS = [
  '__kiChatGetAttachments',
  '__kiChatAddAttachmentFromFile',
  '__kiChatAddAttachmentFromServerFile',
  '__kiChatRemoveAttachment',
  '__kiChatClearAttachments',
  '__kiChatRenderAttachments',
  '__kiChatBindDragDrop',
  '__kiChatOpenBrowser',
  '__kiChatOpenDirBrowser',
  '__kiChatImageFileEntries',
  '__kiChatNormalizeImagePayload',
  '__kiChatRasterizeSvgToPng',
  '__kiChatCreateImageAttachment',
  '__kiChatIsSupportedMimeType',
  '__kiChatDetectMimeType',
  '__kiChatAttachmentSize',
  'ki_chat.attachments.error_unsupported_type',
  'ki_chat.attachments.error_too_many',
  'ki_chat.attachments.error_read_failed',
  'ki_chat.attachments.error_svg_render_failed',
  'ki_chat.attachments.error_send_failed',
];

const RETIRED_MAIN_SCRIPT_TOKENS = [
  '__kiChatCloseBrowser',
  '__kiChatConfirmBrowser',
  '__kiChatBrowserLoad',
  '__kiChatBrowserConfirm',
  '__kiChatBindBrowser',
  '__kiChatBrowserState',
];

const REQUIRED_DRAWER_I18N_KEYS = [
  'ki_chat.attachments.add_button_title',
  'ki_chat.attachments.preview_heading',
  'ki_chat.attachments.preview_clear_all',
  'ki_chat.attachments.drag_overlay_title',
  'ki_chat.attachments.drag_overlay_subtitle',
];

const REQUIRED_SEND_SCRIPT_TOKENS = [
  '__kiChatGetAttachments',
  'readyAttachments',
  'attachments',
  '__kiChatRenderAttachments',
];

const REQUIRED_I18N_KEYS = [
  'ki_chat.attachments.add_button_title',
  'ki_chat.attachments.add_button_label',
  'ki_chat.attachments.browser_title',
  'ki_chat.attachments.browser_loading',
  'ki_chat.attachments.preview_heading',
  'ki_chat.attachments.preview_clear_all',
  'ki_chat.attachments.preview_remove_title',
  'ki_chat.attachments.preview_empty_hint',
  'ki_chat.attachments.preview_size_label',
  'ki_chat.attachments.drag_overlay_title',
  'ki_chat.attachments.drag_overlay_subtitle',
  'ki_chat.attachments.error_unsupported_type',
  'ki_chat.attachments.error_svg_render_failed',
  'ki_chat.attachments.error_too_many',
  'ki_chat.attachments.error_too_large',
  'ki_chat.attachments.error_read_failed',
  'ki_chat.attachments.error_send_failed',
];

describe('KI chat attachment UI', () => {
  it('renders every required DOM id in the drawer fragment', () => {
    const html = readFileSync(DRAWER, 'utf8');
    for (const id of REQUIRED_DRAWER_IDS) {
      expect(html, `drawer must contain id="${id}"`).toContain(`id="${id}"`);
    }
  });

  it('drops the retired private browser overlay from the drawer fragment', () => {
    const html = readFileSync(DRAWER, 'utf8');
    for (const id of RETIRED_DRAWER_IDS) {
      expect(html, `drawer must not contain id="${id}" anymore`).not.toContain(`id="${id}"`);
    }
  });

  it('exposes every attachment helper in the main KI chat script', () => {
    const js = readFileSync(MAIN_SCRIPT, 'utf8');
    for (const token of REQUIRED_MAIN_SCRIPT_TOKENS) {
      expect(js, `main KI chat script must reference ${token}`).toContain(token);
    }
    for (const token of RETIRED_MAIN_SCRIPT_TOKENS) {
      expect(js, `main KI chat script must not keep ${token} anymore`).not.toContain(token);
    }
  });

  it('embeds every i18n key the drawer markup needs', () => {
    const html = readFileSync(DRAWER, 'utf8');
    for (const key of REQUIRED_DRAWER_I18N_KEYS) {
      expect(html, `drawer markup must reference ${key}`).toContain(key);
    }
  });

  it('forwards attachments in the send script', () => {
    const js = readFileSync(SEND_SCRIPT, 'utf8');
    for (const token of REQUIRED_SEND_SCRIPT_TOKENS) {
      expect(js, `send script must reference ${token}`).toContain(token);
    }
    expect(js, 'send script must serialise the attachments array').toMatch(
      /attachments:\s*readyAttachments/,
    );
  });

  it('clears attachments on the assistant finish path', () => {
    const js = readFileSync(SEND_SCRIPT, 'utf8');
    const clears = (js.match(/\.length\s*=\s*0/g) || []).length;
    expect(clears, 'send script must clear attachments on success and on error').toBeGreaterThanOrEqual(2);
    expect(js, 'send script must reference __kiChatGetAttachments in both branches').toContain('var tAtts = __kiChatGetAttachments');
    expect(js, 'send script must reference __kiChatGetAttachments in the error branch').toContain('var tErrAtts = __kiChatGetAttachments');
  });

  it('renders the file input as a multi-image picker for drag-and-drop', () => {
    const html = readFileSync(DRAWER, 'utf8');
    expect(html).toContain('id="kiChatAttachBtn"');
    expect(html).toContain('onclick="__kiChatOpenBrowser()"');
  });

  it('opens the shared file browser with the image extension filter', () => {
    const js = readFileSync(MAIN_SCRIPT, 'utf8');
    expect(js).toContain("var __KI_CHAT_IMAGE_EXTS = ['svg', 'png', 'jpg', 'jpeg', 'tif', 'tiff']");
    expect(js).toContain("'/api/image-extraction/files/browse?fileExtensions=' + __KI_CHAT_IMAGE_EXTS.join(',')");
    expect(js).toContain('__kiChatOpenDirBrowser(function(picked){');
    expect(js).toContain("titleKey: 'ki_chat.attachments.browser_title'");
    expect(js).toContain("storageKey: 'stacklens.fb.last.kiChat'");
  });
});

describe('attachment MIME-type allow-list matches the controller', () => {
  it('allows png/jpeg/tiff images and converts svg to png before sending', () => {
    const js = readFileSync(MAIN_SCRIPT, 'utf8');
    expect(js).toMatch(/'image\/png'/, 'main script must allow image/png');
    expect(js).toMatch(/'image\/jpeg'/, 'main script must allow image/jpeg');
    expect(js).toMatch(/'image\/tiff'/, 'main script must allow image/tiff');
    expect(js).toMatch(/'image\/svg\+xml'/, 'main script must allow image/svg+xml');
    expect(js).toContain("resolve({ mimeType: 'image/png', base64Data: pngBase64 })");
  });

  it('never hands an svg payload to the chat backend', () => {
    const js = readFileSync(MAIN_SCRIPT, 'utf8');
    const normalize = js.slice(
      js.indexOf('function __kiChatNormalizeImagePayload'),
      js.indexOf('function __kiChatCreateImageAttachment'),
    );
    expect(normalize).toContain('__kiChatIsSvgMimeType(mimeType)');
    expect(normalize).toContain('__kiChatRasterizeSvgToPng(base64Data)');
  });
});

describe('i18n keys for KI chat attachments', () => {
  it.each(REQUIRED_I18N_KEYS)('declares %s in both de.json and en.json', (key) => {
    const parts = key.split('.');
    let curDe = DE_I18N;
    let curEn = EN_I18N;
    for (const part of parts) {
      curDe = curDe && curDe[part];
      curEn = curEn && curEn[part];
    }
    expect(curDe, `de.json missing key ${key}`).toBeTypeOf('string');
    expect(curEn, `en.json missing key ${key}`).toBeTypeOf('string');
    expect(curDe).not.toEqual('');
    expect(curEn).not.toEqual('');
  });
});