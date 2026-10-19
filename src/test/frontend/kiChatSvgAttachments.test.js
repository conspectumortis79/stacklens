import { describe, it, expect, beforeEach } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const MAIN = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/scripts-ki-chat.html',
);

function source(path) {
  return readFileSync(path, 'utf8');
}

function constantBlock(name) {
  const src = source(MAIN);
  const start = src.indexOf('var ' + name + ' = {');
  if (start < 0) throw new Error('constant ' + name + ' not found');
  const end = src.indexOf('\n};', start);
  if (end < 0) throw new Error('unterminated constant ' + name);
  return src.slice(start, end + 3);
}

function constantValue(name) {
  const src = source(MAIN);
  const start = src.indexOf('var ' + name + ' = ');
  if (start < 0) throw new Error('constant ' + name + ' not found');
  return src.slice(start, src.indexOf('\n', start));
}

function boot() {
  const win = makeWindow();
  runInWindow(win, [
    constantBlock('__KI_CHAT_SUPPORTED_MIME_TYPES'),
    constantValue('__KI_CHAT_SVG_RASTER_MAX_DIM'),
    extractFunction(source(MAIN), '__kiChatIsSupportedMimeType'),
    extractFunction(source(MAIN), '__kiChatIsSvgMimeType'),
    extractFunction(source(MAIN), '__kiChatDetectMimeType'),
    extractFunction(source(MAIN), '__kiChatSvgIntrinsicSize'),
    extractFunction(source(MAIN), '__kiChatBase64ToText'),
    extractFunction(source(MAIN), '__kiChatRasterizeSvgToPng'),
    extractFunction(source(MAIN), '__kiChatNormalizeImagePayload'),
    extractFunction(source(MAIN), '__kiChatAttachmentErrorText'),
  ].join('\n'));
  win.i18n = { t: (key) => key };
  return win;
}

describe('KI chat image mime detection', () => {
  let win;
  beforeEach(() => { win = boot(); });

  it('detects svg next to the raster formats', () => {
    expect(win.__kiChatDetectMimeType('Diagramm.SVG')).toBe('image/svg+xml');
    expect(win.__kiChatDetectMimeType('a.png')).toBe('image/png');
    expect(win.__kiChatDetectMimeType('a.jpg')).toBe('image/jpeg');
    expect(win.__kiChatDetectMimeType('a.jpeg')).toBe('image/jpeg');
    expect(win.__kiChatDetectMimeType('a.tiff')).toBe('image/tiff');
    expect(win.__kiChatDetectMimeType('notes.txt')).toBe('');
  });

  it('accepts svg as a supported attachment type', () => {
    expect(win.__kiChatIsSupportedMimeType('image/svg+xml')).toBe(true);
    expect(win.__kiChatIsSupportedMimeType('IMAGE/SVG+XML')).toBe(true);
    expect(win.__kiChatIsSvgMimeType('image/svg+xml')).toBe(true);
    expect(win.__kiChatIsSvgMimeType('image/png')).toBe(false);
    expect(win.__kiChatIsSupportedMimeType('image/gif')).toBe(false);
  });
});

describe('KI chat svg intrinsic size parsing', () => {
  let win;
  beforeEach(() => { win = boot(); });

  it('prefers explicit width and height attributes', () => {
    expect(win.__kiChatSvgIntrinsicSize('<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect/></svg>'))
      .toEqual({ width: 120, height: 80 });
  });

  it('ignores hyphenated attributes that end with width or height', () => {
    expect(win.__kiChatSvgIntrinsicSize('<svg stroke-width="5" viewBox="0 0 200 100" width="120" height="80"/>'))
      .toEqual({ width: 120, height: 80 });
  });

  it('falls back to the viewBox when no size attributes exist', () => {
    expect(win.__kiChatSvgIntrinsicSize('<svg viewBox="0 0 640 480"><path/></svg>'))
      .toEqual({ width: 640, height: 480 });
  });

  it('tolerates px units and comma separated viewBox values', () => {
    expect(win.__kiChatSvgIntrinsicSize('<svg width="24px" height="24px">'))
      .toEqual({ width: 24, height: 24 });
    expect(win.__kiChatSvgIntrinsicSize('<svg viewBox="0,0,32,16">'))
      .toEqual({ width: 32, height: 16 });
  });

  it('reports zero when the document carries no intrinsic size', () => {
    expect(win.__kiChatSvgIntrinsicSize('<svg width="100%" height="100%"><g/></svg>'))
      .toEqual({ width: 0, height: 0 });
    expect(win.__kiChatSvgIntrinsicSize(''))
      .toEqual({ width: 0, height: 0 });
  });
});

describe('KI chat attachment payload normalisation', () => {
  let win;
  beforeEach(() => { win = boot(); });

  it('passes raster payloads through untouched', async () => {
    const payload = await win.__kiChatNormalizeImagePayload('image/png', 'iVBORw0KGgo=');
    expect(payload).toEqual({ mimeType: 'image/png', base64Data: 'iVBORw0KGgo=' });
  });

  it('routes svg payloads through the rasterizer', async () => {
    await expect(win.__kiChatNormalizeImagePayload('image/svg+xml', 'kein-base64'))
      .rejects.toThrow('svg_render_failed');
  });

  it('rejects unparsable svg payloads instead of sending them to the model', async () => {
    await expect(win.__kiChatRasterizeSvgToPng('%%%definitely not base64%%%'))
      .rejects.toThrow('svg_render_failed');
  });
});

describe('KI chat attachment error messages', () => {
  let win;
  beforeEach(() => { win = boot(); });

  it('maps the known failure codes to their i18n keys', () => {
    expect(win.__kiChatAttachmentErrorText(new Error('unsupported_mime')))
      .toBe('ki_chat.attachments.error_unsupported_type');
    expect(win.__kiChatAttachmentErrorText(new Error('svg_render_failed')))
      .toBe('ki_chat.attachments.error_svg_render_failed');
    expect(win.__kiChatAttachmentErrorText(new Error('read_failed')))
      .toBe('ki_chat.attachments.error_read_failed');
  });

  it('leaves unknown failures to the caller', () => {
    expect(win.__kiChatAttachmentErrorText(new Error('HTTP 413'))).toBe('');
    expect(win.__kiChatAttachmentErrorText(null)).toBe('');
  });
});
