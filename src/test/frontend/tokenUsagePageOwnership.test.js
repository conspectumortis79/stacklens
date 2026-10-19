import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { resolve, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const TEMPLATES = resolve(__dirname, '../../main/resources/templates');
const FRAGMENTS = resolve(TEMPLATES, 'fragments');
const FRAGMENT_MARKER = 'fragments/shared/token-usage :: scripts';

const KIND_FORMS = [
    [/slTokenUsagePage\(\{\s*kind:\s*'([a-z_]+)'/, 'page'],
    [/slTokenUsageRebind\('([a-z_]+)'/, 'rebind'],
    [/cfTokenUsage\.(?:init|watch)\(\s*'([a-z_]+)'/, 'direct'],
];
const ANCHOR_OF_DECLARATION = /anchor:\s*document\.querySelector\(\s*'([^']+)'\s*\)/g;

function htmlFiles(dir) {
    if (!existsSync(dir)) return [];
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = resolve(dir, entry.name);
        if (entry.isDirectory()) return htmlFiles(full);
        return entry.name.endsWith('.html') ? [full] : [];
    });
}

function pagesUsingTheBadge() {
    return htmlFiles(TEMPLATES)
        .filter((file) => readFileSync(file, 'utf8').includes(FRAGMENT_MARKER))
        .map((file) => ({
            file,
            name: basename(file),
            pageKind: pageLevelKindOf(file),
            usedKinds: usedKindsOf(file),
            anchors: anchorsOf(file),
        }));
}

function anchorsOf(file) {
    const sources = [file, ...htmlFiles(resolve(FRAGMENTS, basename(file, '.html')))];
    const anchors = new Set();
    sources.forEach((source) => {
        const code = readFileSync(source, 'utf8');
        for (const match of code.matchAll(ANCHOR_OF_DECLARATION)) anchors.add(match[1]);
    });
    return [...anchors].sort();
}

function kindOf(source, onlyPageLevel) {
    for (const [re] of KIND_FORMS) {
        const match = re.exec(source);
        if (match) return match[1];
    }
    return null;
}

function pageLevelKindOf(file) {
    const html = readFileSync(file, 'utf8');
    const afterFragment = html.slice(html.lastIndexOf(FRAGMENT_MARKER));
    return kindOf(afterFragment, true);
}

function usedKindsOf(file) {
    const name = basename(file, '.html');
    const sources = [file, ...htmlFiles(resolve(FRAGMENTS, name))];
    const kinds = new Set();
    sources.forEach((source) => {
        const code = readFileSync(source, 'utf8');
        for (const [re] of KIND_FORMS) {
            for (const match of code.matchAll(new RegExp(re.source, re.flags + 'g'))) kinds.add(match[1]);
        }
    });
    return [...kinds].sort();
}

describe('Token-usage-Badge: jede Seite beansprucht ihre Backend-Art selbst', () => {
    const pages = pagesUsingTheBadge();

    it('mindestens eine Seite nutzt den Badge überhaupt', () => {
        expect(pages.length).toBeGreaterThanOrEqual(15);
    });

    it('jede Badge-Seite deklariert beim Seitenaufruf synchron genau eine Art', () => {
        const offenders = pages
            .filter((page) => !page.pageKind)
            .map((page) => page.name);
        expect(offenders, 'pages without a page-level cfTokenUsage declaration: ' + offenders.join(', '))
            .toEqual([]);
    });

    it('jede Seite nutzt ausschließlich die Art, die sie beim Laden deklariert', () => {
        const offenders = [];
        pages.forEach((page) => {
            const foreign = page.usedKinds.filter((kind) => kind !== page.pageKind);
            if (foreign.length) {
                offenders.push(`${page.name}: deklariert '${page.pageKind}', verwendet aber ${foreign.join(', ')}`);
            }
        });
        expect(offenders).toEqual([]);
    });

    it('keine zwei Seiten teilen sich dieselbe Backend-Art', () => {
        const seen = new Map();
        const duplicates = [];
        pages.forEach((page) => {
            if (!page.pageKind) return;
            if (seen.has(page.pageKind)) {
                duplicates.push(`${page.pageKind}: ${seen.get(page.pageKind)} und ${page.name}`);
            }
            seen.set(page.pageKind, page.name);
        });
        expect(duplicates).toEqual([]);
    });

    it('lasttest-analysis und code-analysis verankern am Page-Header wie code-qa', () => {
        const anchorOf = (file) => {
            const html = readFileSync(file, 'utf8');
            const afterFragment = html.slice(html.lastIndexOf(FRAGMENT_MARKER));
            const match = /anchor:\s*document\.querySelector\(\s*'([^']+)'\s*\)/.exec(afterFragment);
            return match ? match[1] : null;
        };

        expect(anchorOf(resolve(TEMPLATES, 'lasttest-analysis.html'))).toBe('.sl-page-header');
        expect(anchorOf(resolve(TEMPLATES, 'code-analysis.html'))).toBe('.sl-page-header');
        expect(anchorOf(resolve(TEMPLATES, 'code-qa.html'))).toBe('.sl-page-header');
        expect(anchorOf(resolve(TEMPLATES, 'pipeline-analysis.html'))).toBe('.sl-page-header');
    });

    it('keine Seite mischt zwei verschiedene Anker, sonst springt der Badge beim Öffnen einer Analyse', () => {
        const offenders = [];
        pages.forEach((page) => {
            if (page.anchors.length > 1) {
                offenders.push(`${page.name}: ${page.anchors.join(' | ')}`);
            }
        });
        expect(offenders).toEqual([]);
    });
});
