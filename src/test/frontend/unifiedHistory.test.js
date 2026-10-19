import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..', '..', '..');
const RES = resolve(ROOT, 'src', 'main', 'resources');

const PAGES = [
  'code-analysis',
  'pipeline-analysis',
  'security-analysis',
  'lasttest-analysis',
  'sql-analysis',
  'image-extraction',
  'review-analysis',
  'logging',
  'code-qa',
  'ticket-implementation',
  'ticket-creation',
  'search',
  'ticket-summary',
  'sprint-review',
  'ticket-refinement'
];

function read(abs) {
  return readFileSync(abs, 'utf8');
}

function pageTemplate(page) {
  return read(resolve(RES, 'templates', page + '.html'));
}

function mountConfig(source) {
  const start = source.indexOf('slHistory.mount({');
  let i = source.indexOf('(', start) + 1;
  let depth = 0;
  while (i < source.length) {
    const ch = source[i];
    if (ch === '{') depth++;
    else if (ch === '}') {
      depth--;
      if (depth === 0) return source.slice(start, i + 1);
    }
    i++;
  }
  return source.slice(start);
}

let _templateFiles = null;

function templateFiles() {
  if (_templateFiles) return _templateFiles;
  const out = [];
  const walk = (dir, extensions) => {
    for (const name of readdirSync(dir)) {
      const full = resolve(dir, name);
      if (statSync(full).isDirectory()) walk(full, extensions);
      else if (extensions.some((ext) => full.endsWith(ext))) out.push(full);
    }
  };
  walk(resolve(RES, 'templates'), ['.html']);
  walk(resolve(RES, 'static', 'js'), ['.js']);
  _templateFiles = out;
  return out;
}

describe('einheitlicher Verlauf über alle Analysen', () => {
  it('bindet CSS und JS des gemeinsamen Bausteins global ein', () => {
    const layout = read(resolve(RES, 'templates', 'layout.html'));
    expect(layout).toContain('@{/css/history.css}');
    expect(layout).toContain('@{/js/history-list.js}');
    expect(existsSync(resolve(RES, 'static', 'css', 'history.css'))).toBe(true);
    expect(existsSync(resolve(RES, 'static', 'js', 'history-list.js'))).toBe(true);
    expect(existsSync(resolve(RES, 'templates', 'fragments', 'shared', 'history.html'))).toBe(true);
  });

  it.each(PAGES)('%s nutzt das gemeinsame Verlauf-Fragment', (page) => {
    expect(pageTemplate(page)).toContain("fragments/shared/history :: card('" + page + "'");
  });

  it.each(PAGES)('%s hängt den Baustein mit passender Seite und Container auf', (page) => {
    const source = templateFiles()
      .map(read)
      .filter((html) => html.includes("page: '" + page + "'") && html.includes('slHistory.mount'));
    expect(source.length).toBe(1);
    expect(source[0]).toContain("root: 'hxCard-" + page + "'");
  });

  it.each(PAGES)('%s übergibt Status, Titel und Erstellt an den Baustein', (page) => {
    const mounted = templateFiles().map(read).find((html) => html.includes("page: '" + page + "'") && html.includes('slHistory.mount'));
    const config = mountConfig(mounted);
    expect(config).toContain('status:');
    expect(config).toContain('title:');
    expect(config).toContain('createdAt:');
    expect(config).toContain('actions:');
    expect(config).toContain('open:');
  });

  it('enthält keine seitenspezifischen Verlaufs-Akkordeons mehr', () => {
    const legacy = /[a-z]{2}-history-card|[a-z]{2}-acrd-|[a-z]{2}-history-status|[a-z]{2}-history-table|[a-z]{2}-history-preview|[a-z]{2}-history-empty|[a-z]{2}-history-issue-pill/;
    const hits = templateFiles()
      .filter((file) => legacy.test(read(file)))
      .map((file) => file.replace(RES + '/', ''));
    expect(hits).toEqual([]);
  });

  it('verlässt sich im Verlauf auf Ereignisdelegation statt Inline-OnClick', () => {
    const inline = /onclick="[a-z]+(Show|Select|Load|Restore|Open|Delete|Cancel|Rerun)[A-Za-z]*History|onclick="[a-z]+DeleteHistoryItem|onclick="loadHistoryItem|onclick="mrViewHistoryResult|onclick="apShowPipelineRun/;
    const hits = templateFiles()
      .filter((file) => inline.test(read(file)))
      .map((file) => file.replace(RES + '/', ''));
    expect(hits).toEqual([]);
  });

  it('verwendet nur die fünf einheitlichen Zustände', () => {
    const component = read(resolve(RES, 'static', 'js', 'history-list.js'));
    for (const state of ['COMPLETED', 'RUNNING', 'PENDING', 'FAILED', 'CANCELLED']) {
      expect(component).toContain(state + ':');
    }
    for (const alias of ['DONE', 'ERROR', 'ABGEBROCHEN']) {
      expect(component).toContain(alias + ": '");
    }
    const css = read(resolve(RES, 'static', 'css', 'history.css'));
    for (const state of ['COMPLETED', 'RUNNING', 'PENDING', 'FAILED', 'CANCELLED']) {
      expect(css).toContain('[data-status="' + state + '"]');
    }
  });

  it('definiert keine hartcodierten Flächenfarben im Baustein', () => {
    const css = read(resolve(RES, 'static', 'css', 'history.css'));
    const hex = /#[0-9a-fA-F]{3,6}\b/g.exec(css);
    expect(hex).toBe(null);
    expect(css).toContain('var(--app-cream)');
    expect(css).toContain('var(--app-accent-green)');
    expect(css).toContain('color-mix(');
  });

  it('zeigt Öffnen- und Löschen-Button in jeder Zeile dauerhaft an', () => {
    const css = read(resolve(RES, 'static', 'css', 'history.css'));
    const rail = /\.hx-rail\s*\{([^}]*)\}/.exec(css);
    expect(rail).not.toBe(null);
    expect(rail[1]).not.toMatch(/opacity/);
    expect(rail[1]).not.toMatch(/display:\s*none/);
    expect(css).not.toMatch(/:hover[^{]*\.hx-rail/);
    expect(css).not.toMatch(/:focus-within[^{]*\.hx-rail/);
    for (const selector of ['.hx-open', '.hx-act']) {
      const rule = new RegExp('\\' + selector + '\\s*\\{([^}]*)\\}').exec(css);
      expect(rule).not.toBe(null);
      expect(rule[1]).not.toMatch(/opacity:\s*0/);
      expect(rule[1]).not.toMatch(/display:\s*none/);
      expect(rule[1]).not.toMatch(/visibility:\s*hidden/);
    }
  });

  it('deckt alle vom Baustein genutzten i18n-Schlüssel in de und en ab', () => {
    const de = JSON.parse(read(resolve(RES, 'static', 'i18n', 'de.json')));
    const en = JSON.parse(read(resolve(RES, 'static', 'i18n', 'en.json')));
    const lookup = (obj, key) => key.split('.').reduce((acc, part) => (acc && acc[part] !== undefined ? acc[part] : undefined), obj);

    const used = new Set();
    for (const file of templateFiles().concat([resolve(RES, 'static', 'js', 'history-list.js')])) {
      const html = read(file);
      if (!html.includes('slHistory.mount') && !html.includes('common.history_')) continue;
      for (const m of html.matchAll(/(?:titleKey|emptyKey|key):\s*'([\w.]+)'/g)) used.add(m[1]);
      for (const m of html.matchAll(/'(common\.history_[\w]+)'/g)) used.add(m[1]);
    }
    const missing = [...used].filter((key) => lookup(de, key) === undefined || lookup(en, key) === undefined);
    expect(missing.sort()).toEqual([]);
  });
});
