import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFunction } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const DIAGRAMS = resolve(RES, 'templates/fragments/code-analysis/scripts-diagrams.html');

const normalize = (() => {
  const source = extractFunction(readFileSync(DIAGRAMS, 'utf8'), 'caNormalizeDrawioXml');
  const factory = new Function('return ' + source);
  return factory();
})();

const MODEL_OPEN = /<mxGraphModel\b[^>]*>/i;

describe('draw.io normalisation contract', () => {
  it('drops the root attribute that breaks the bundled graph viewer', () => {
    const xml = '<mxGraphModel root="1"><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>';
    const out = normalize(xml);
    expect(MODEL_OPEN.exec(out)[0]).not.toMatch(/\broot\s*=/i);
  });

  it('keeps the viewer attributes the normaliser injects', () => {
    const xml = '<mxGraphModel root="1"><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>';
    const open = MODEL_OPEN.exec(normalize(xml))[0];
    expect(open).toContain('dx="1422"');
    expect(open).toContain('pageWidth="1654"');
  });

  it('drops a root attribute that sits between other attributes', () => {
    const xml = '<mxGraphModel grid="1" root="1" page="1"><root><mxCell id="0"/></root></mxGraphModel>';
    const open = MODEL_OPEN.exec(normalize(xml))[0];
    expect(open).not.toMatch(/\broot\s*=/i);
    expect(open).toContain('grid="1"');
    expect(open).toContain('page="1"');
  });

  it('still unwraps an inline diagram wrapper before normalising', () => {
    const xml = '<mxfile><diagram name="x"><mxGraphModel root="1"><root><mxCell id="0"/></root></mxGraphModel></diagram></mxfile>';
    const out = normalize(xml);
    expect(out).not.toContain('<diagram');
    expect(MODEL_OPEN.exec(out)[0]).not.toMatch(/\broot\s*=/i);
  });

  it('leaves a model without the root attribute untouched', () => {
    const xml = '<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>';
    expect(MODEL_OPEN.exec(normalize(xml))[0]).not.toMatch(/\broot\s*=/i);
  });
});
