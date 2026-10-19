import { describe, it, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SETTINGS_FRAGMENT = resolve(
    __dirname,
    '../../main/resources/templates/fragments/config/panes-settings.html',
);

const TEST_BUTTON_IDS = [
    'aiTestModelBtn',
    'aiTestTokenBtn',
    'aiTestOpenAiBtn',
    'aiTestOllamaBtn',
    'aiTestLmStudioBtn',
    'aiTestOpenRouterBtn',
    'aiTestClaudeCodeBtn',
    'aiTestCompatibleBtn',
];

function loadDocument() {
    return new JSDOM(readFileSync(SETTINGS_FRAGMENT, 'utf8')).window.document;
}

function columnOf(element, label) {
    if (!element) throw new Error(`missing element: ${label}`);
    const column = element.closest('.row > div');
    if (!column) throw new Error(`no grid column for: ${label}`);
    return column;
}

function gutterClasses(element) {
    return element.classList.value.split(/\s+/).filter((name) => /^g-\d$/.test(name));
}

describe('config pane: KI save button alignment', () => {
    const doc = loadDocument();
    const saveColumn = columnOf(doc.querySelector('button[onclick="saveAiConfig()"]'), 'save button');
    const testColumn = columnOf(doc.getElementById('aiTestCompatibleBtn'), 'aiTestCompatibleBtn');

    it('pins the save column to the right edge of its row', () => {
        expect(saveColumn.classList.contains('ms-auto')).toBe(true);
        expect([...saveColumn.parentElement.children].pop()).toBe(saveColumn);
    });

    it('gives save and test buttons the same width and right edge', () => {
        expect(saveColumn.classList.contains('col-md-3')).toBe(true);
        expect(testColumn.classList.contains('col-md-3')).toBe(true);
        expect(testColumn.classList.contains('offset-md-9')).toBe(true);
    });

    it('uses the same horizontal gutter in both rows', () => {
        expect(gutterClasses(saveColumn.closest('.row'))).toEqual(gutterClasses(testColumn.closest('.row')));
    });

    it('keeps every provider test button in the aligned action column', () => {
        TEST_BUTTON_IDS.forEach((id) => {
            expect(columnOf(doc.getElementById(id), id)).toBe(testColumn);
        });
    });
});
