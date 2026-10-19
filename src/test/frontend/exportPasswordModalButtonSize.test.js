import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OVERLAY = resolve(
  __dirname,
  '../../main/resources/templates/fragments/knowledge/export-password-overlay.html',
);
const KNOWLEDGE_PAGE = resolve(
  __dirname,
  '../../main/resources/templates/knowledge.html',
);

function readOverlay() {
  return readFileSync(OVERLAY, 'utf-8');
}

function readKnowledgePage() {
  return readFileSync(KNOWLEDGE_PAGE, 'utf-8');
}

describe('Wissensdatenbank export-password modal — footer button size', () => {
  it('Abbrechen button carries `btn-sm` so it matches the page-actions buttons', () => {
    const html = readOverlay();
    const footerMatch = html.match(/<div class="modal-footer">([\s\S]*?)<\/div>/);
    expect(footerMatch, 'expected a <div class="modal-footer"> block').not.toBeNull();
    const footer = footerMatch[1];
    const cancelMatch = footer.match(/<button[^>]*?class="[^"]*btn-outline-secondary[^"]*"[^>]*?>/);
    expect(cancelMatch, 'expected a Cancel button with btn-outline-secondary in the footer').not.toBeNull();
    const cancelTag = cancelMatch[0];
    expect(cancelTag).toMatch(/\bbtn-sm\b/);
    expect(cancelTag).toMatch(/\bbtn-outline-secondary\b/);
    expect(cancelTag).toMatch(/data-bs-dismiss="modal"/);
  });

  it('Herunterladen confirm button (#kbExportPasswordConfirm) carries `btn-sm`', () => {
    const html = readOverlay();
    const confirmMatch = html.match(/<button[^>]*id="kbExportPasswordConfirm"[^>]*>/);
    expect(confirmMatch, 'expected #kbExportPasswordConfirm in the overlay').not.toBeNull();
    const confirmTag = confirmMatch[0];
    expect(confirmTag).toMatch(/\bbtn-sm\b/);
    expect(confirmTag).toMatch(/\bbtn-primary\b/);
  });

  it('page-action buttons in knowledge.html use the same `btn-sm` size as a reference', () => {
    const html = readKnowledgePage();
    const exportMatch = html.match(/<button[^>]*onclick="kbOpenExport\(\)"[^>]*>/);
    const importMatch = html.match(/<button[^>]*onclick="kbOpenImport\(\)"[^>]*>/);
    expect(exportMatch, 'expected the "Alle exportieren" button').not.toBeNull();
    expect(importMatch, 'expected the "Importieren" button').not.toBeNull();
    expect(exportMatch[0]).toMatch(/\bbtn-sm\b/);
    expect(importMatch[0]).toMatch(/\bbtn-sm\b/);
  });
});
