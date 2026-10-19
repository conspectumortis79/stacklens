import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { resolve, join, relative, dirname } from 'node:path';

export const RESOURCES = resolve(process.cwd(), 'src/main/resources');
export const TEMPLATES = join(RESOURCES, 'templates');
export const STATIC = join(RESOURCES, 'static');
export const KOTLIN = resolve(process.cwd(), 'src/main/kotlin');
export const FRONTEND = resolve(process.cwd(), 'src/test/frontend');

const FRAGMENT_RE = /th:(?:replace|insert)\s*=\s*"~\{([^:}]+?)(?:\s*::\s*[^"}]+)?\}"/g;
const DECORATE_RE = /layout:decorate\s*=\s*"~\{([^}"]+)\}"/g;
const SRC_RE = /(?:th:)?src\s*=\s*"@?\{?([^"}]+)\}?"/g;
const KEYWORDS = new Set([
  'if', 'else', 'return', 'var', 'let', 'const', 'function', 'async', 'await', 'try', 'catch',
  'finally', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'typeof', 'instanceof',
  'new', 'delete', 'void', 'in', 'of', 'this', 'null', 'true', 'false', 'throw', 'yield', 'class',
  'extends', 'super', 'default', 'static', 'get', 'set',
]);

function read(file) {
  return readFileSync(file, 'utf8');
}

export function walk(dir, extension) {
  const out = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full, extension));
    else if (entry.name.endsWith(extension)) out.push(full);
  }
  return out.sort();
}

export function templateFiles() {
  return walk(TEMPLATES, '.html');
}

export function ownScriptFiles() {
  return walk(join(STATIC, 'js'), '.js').filter((f) => !f.endsWith('.min.js'));
}

export function splitScripts(text) {
  const blocks = [];
  let i = 0;
  const n = text.length;
  while (i < n) {
    const rest = text.slice(i);
    const open = /^<script\b/i.test(rest) ? 0 : rest.search(/<script\b/i);
    if (open < 0) break;
    const nameMatch = /^<script\b/i.exec(rest.slice(open));
    const start = i + open;
    let j = start + nameMatch[0].length;
    while (j < n) {
      const c = text[j];
      if (c === '"' || c === "'") {
        const q = c;
        j += 1;
        while (j < n && text[j] !== q) j += 1;
      } else if (c === '>') {
        break;
      }
      j += 1;
    }
    if (j >= n) break;
    const attrs = text.slice(start + nameMatch[0].length, j);
    j += 1;
    let k = j;
    let prev = '';
    while (k < n) {
      const c = text[k];
      if (c === '/' && text[k + 1] === '/') {
        const nl = text.indexOf('\n', k);
        k = nl < 0 ? n : nl;
        continue;
      }
      if (c === '/' && text[k + 1] === '*') {
        const end = text.indexOf('*/', k + 2);
        k = end < 0 ? n : end + 2;
        continue;
      }
      if (c === '"' || c === "'" || c === '`') {
        const q = c;
        k += 1;
        while (k < n) {
          if (text[k] === '\\') { k += 2; continue; }
          if (text[k] === q) { k += 1; break; }
          k += 1;
        }
        prev = 'str';
        continue;
      }
      if (c === '/' && valueLike(prev) === false) {
        const end = readRegex(text, k);
        if (end > k) {
          k = end;
          prev = 'regex';
          continue;
        }
      }
      if (c === '<' && text.slice(k, k + 9).toLowerCase() === '</script>') break;
      if (!/\s/.test(c)) prev = c;
      k += 1;
    }
    blocks.push({ attrs, body: text.slice(j, k), line: text.slice(0, start).split('\n').length });
    i = k + 9;
  }
  return blocks;
}

function valueLike(prev) {
  return prev === ')' || prev === ']' || /[A-Za-z0-9_$]/.test(prev);
}

function readRegex(text, start) {
  const n = text.length;
  let j = start + 1;
  let inClass = false;
  while (j < n) {
    const c = text[j];
    if (c === '\\') { j += 2; continue; }
    if (c === '\n') return start;
    if (c === '[') inClass = true;
    else if (c === ']') inClass = false;
    else if (c === '/' && !inClass) {
      j += 1;
      while (j < n && /[a-z]/.test(text[j])) j += 1;
      return j;
    }
    j += 1;
  }
  return start;
}

export function stripCdta(block) {
  return block.replace(/\/\*<!\[CDATA\[\*\//g, '').replace(/\/\*\]\]>\*\//g, '');
}

export function tokenize(code) {
  const out = [];
  const n = code.length;
  let i = 0;
  let prev = '';
  while (i < n) {
    const c = code[i];
    if (/\s/.test(c)) { i += 1; continue; }
    if (c === '/' && code[i + 1] === '/') {
      const nl = code.indexOf('\n', i);
      i = nl < 0 ? n : nl;
      continue;
    }
    if (c === '/' && code[i + 1] === '*') {
      const end = code.indexOf('*/', i + 2);
      i = end < 0 ? n : end + 2;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') {
      const q = c;
      i += 1;
      while (i < n) {
        if (code[i] === '\\') { i += 2; continue; }
        if (code[i] === q) { i += 1; break; }
        i += 1;
      }
      out.push({ pos: i - 1, kind: 'str', text: '' });
      prev = 'str';
      continue;
    }
    if (c === '/' && prev && valueLike(prev) && !keywordTail(code.slice(0, i))) {
      out.push({ pos: i, kind: 'punct', text: c });
      prev = '/';
      i += 1;
      continue;
    }
    if (c === '/') {
      const end = readRegex(code, i);
      if (end > i) {
        prev = 'regex';
        i = end;
        out.push({ pos: end - 1, kind: 'regex', text: '' });
        continue;
      }
    }
    if ('({['.includes(c) || ')}]'.includes(c)) {
      out.push({ pos: i, kind: 'punct', text: c });
      prev = c;
      i += 1;
      continue;
    }
    const m = /^[A-Za-z_$][\w$]*/.exec(code.slice(i));
    if (m) {
      out.push({ pos: i, kind: 'name', text: m[0] });
      prev = m[0];
      i += m[0].length;
      continue;
    }
    out.push({ pos: i, kind: 'punct', text: c });
    prev = c;
    i += 1;
  }
  return out;
}

function keywordTail(before) {
  const m = /(?:^|[^\w$])(return|typeof|instanceof|in|of|new|delete|void|case|do|else|if|while|for|yield|await|throw)$/.exec(before);
  return Boolean(m);
}

const FUN_RE = /^(?:async[ \t]+)?function[ \t]+([A-Za-z_$][\w$]*)[ \t]*\(/;
const VAR_RE = /^(const|let|var)[ \t]+([A-Za-z_$][\w$]*)[ \t]*(?::[^=]{0,80})?=/;
const WIN_RE = /^(?:window|globalThis|self)\.([A-Za-z_$][\w$]*)[ \t]*=/;

export function topLevelDeclarations(code) {
  const tokens = tokenize(code);
  const funcs = [];
  const vars = [];
  let depth = 0;
  for (const tok of tokens) {
    const c = code[tok.pos];
    if (tok.kind === 'punct' && '({['.includes(c)) { depth += 1; continue; }
    if (tok.kind === 'punct' && ')}]'.includes(c)) { depth -= 1; continue; }
    const atGlobal = depth === 0 && (tok.pos === 0 || !/[.$]/.test(code[tok.pos - 1]));
    const seg = code.slice(tok.pos, tok.pos + 120);
    if (atGlobal) {
      const fun = FUN_RE.exec(seg);
      if (fun) {
        const line = lineOf(code, tok.pos);
        if (!funcs.length || funcs[funcs.length - 1].name !== fun[1] || funcs[funcs.length - 1].line !== line) {
          funcs.push({ name: fun[1], line });
        }
        continue;
      }
      const vr = VAR_RE.exec(seg);
      if (vr) { vars.push({ name: vr[2], line: lineOf(code, tok.pos), keyword: vr[1] }); continue; }
    }
    const win = WIN_RE.exec(seg);
    if (win) vars.push({ name: win[1], line: lineOf(code, tok.pos), keyword: 'window' });
  }
  return { funcs, vars };
}

function lineOf(code, pos) {
  return code.slice(0, pos).split('\n').length;
}

export function importsOf(code) {
  const out = [];
  for (const m of code.matchAll(/(?:^|\n)[ \t]*import[ \t]*(?:\{([^}]*)\}|[*\w$]+)[ \t]*from[ \t]*['"]([^'"]+)['"]/g)) {
    if (m[1]) {
      for (const part of m[1].split(',')) out.push({ binding: part.trim().split(/\s+as\s+/).pop().trim() });
    }
    out.push({ from: m[2] });
  }
  return out;
}

function templateRefToPath(ref) {
  const file = join(TEMPLATES, ref.trim() + '.html');
  return existsSync(file) ? file : null;
}

export function closureForTemplate(file) {
  const order = [];
  const seen = new Set();
  const visit = (current) => {
    const key = relative(TEMPLATES, current);
    if (seen.has(key)) return;
    seen.add(key);
    order.push({ abs: current, rel: key, text: read(current) });
    const text = order[order.length - 1].text;
    for (const m of text.matchAll(FRAGMENT_RE)) {
      const sub = templateRefToPath(m[1]);
      if (sub) visit(sub);
    }
    for (const m of text.matchAll(DECORATE_RE)) {
      const sub = templateRefToPath(m[1]);
      if (sub) visit(sub);
    }
  };
  visit(file);
  return order;
}

export function pages() {
  return walk(TEMPLATES, '.html').filter((f) => dirname(f) === TEMPLATES);
}

export function scriptAssetsOf(entries) {
  const assets = [];
  for (const entry of entries) {
    for (const m of entry.text.matchAll(SRC_RE)) {
      const url = m[1];
      if (url.startsWith('/js/')) assets.push(url);
      else if (url.startsWith('http') || url.startsWith('/vendor/')) assets.push(url);
    }
  }
  return assets;
}

export function jsFileForAsset(url) {
  const file = join(STATIC, url.replace(/^\//, ''));
  return existsSync(file) ? file : null;
}

export function reachableJsAssets(initialUrls) {
  const urls = new Set(initialUrls.filter((u) => u.startsWith('/js/')));
  const pending = [...urls];
  while (pending.length) {
    const url = pending.shift();
    if (!url.startsWith('/js/') || url.endsWith('.min.js')) continue;
    const file = jsFileForAsset(url);
    if (!file) continue;
    for (const imp of importsOf(read(file))) {
      if (!imp.from) continue;
      const target = join(dirname(file), imp.from);
      const rel = '/' + relative(STATIC, target).split('\\').join('/');
      if (!urls.has(rel) && existsSync(target)) {
        urls.add(rel);
        pending.push(rel);
      }
    }
  }
  return [...urls].sort();
}

export function declaredNamesInSources(sources) {
  const names = new Set();
  for (const text of sources) {
    const decl = topLevelDeclarations(text);
    for (const f of decl.funcs) names.add(f.name);
    for (const v of decl.vars) names.add(v.name);
    for (const m of text.matchAll(/\b([A-Za-z_$][\w$]*)[ \t]*:[ \t]*(?:async[ \t]+)?function\b/g)) names.add(m[1]);
  }
  return names;
}

export function idLiterals(text) {
  const found = new Set();
  for (const m of text.matchAll(/\s(?:th:)?id\s*=\s*"([^"{}'`]+)"/g)) found.add(m[1].trim());
  for (const m of text.matchAll(/\.id\s*=\s*['"]([^'"]+)['"]/g)) found.add(m[1]);
  for (const m of text.matchAll(/setAttribute\(\s*['"]id['"]\s*,\s*['"]([^'"]+)['"]/g)) found.add(m[1]);
  return found;
}

export function idConcatPrefixes(text) {
  const out = new Set();
  const add = (v) => { if (v && v.length >= 4) out.add(v); };
  for (const m of text.matchAll(/\.id\s*=\s*['"]([^'"]+)['"]\s*\+/g)) add(m[1]);
  for (const m of text.matchAll(/\bid=(["'])[^"'<>]*?\1\s*\+/g)) {
    const lit = /['"]([^'"]*)['"]\s*\+/.exec(m[0]);
    if (lit) add(lit[1]);
  }
  for (const m of text.matchAll(/\bid="([A-Za-z0-9_\-]*)\$\{/g)) add(m[1]);
  for (const m of text.matchAll(/\b(?:th:)?id="'([A-Za-z0-9_\-]*)'\s*\+/g)) add(m[1]);
  for (const m of text.matchAll(/\b(?:th:)?id="\|([A-Za-z0-9_\-]*)\$\{/g)) add(m[1]);
  return out;
}

export function referencedIds(text) {
  const out = new Map();
  for (const m of text.matchAll(/getElementById\(\s*['"]([^'"]+)['"]\s*\)/g)) {
    if (!out.has(m[1])) out.set(m[1], lineOf(text, m.index));
  }
  return out;
}

export function inlineHandlerCalls(text) {
  const out = [];
  const scrubbed = text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:\\])\/\/[^\n]*/g, '$1 ');
  for (const m of scrubbed.matchAll(/\bon(?:click|change|input|submit|keydown|keyup|focus|blur|load|error|dragover|dragleave|drag|drop|paste|dblclick|wheel|toggle|scroll|contextmenu)\s*=\s*"([^"]*)"/gi)) {
    const attr = m[1];
    const line = lineOf(text, m.index);
    const re = /(^|[^.\w$])([A-Za-z_$][\w$]*)\s*\(/g;
    let c;
    while ((c = re.exec(attr)) !== null) out.push({ name: c[2], line, receiverDot: c[1] === '.' });
  }
  return out;
}

export function kotlinRoutes() {
  const routes = new Set();
  for (const file of walk(KOTLIN, '.kt')) {
    const text = read(file);
    if (!/@RestController|@Controller\b/.test(text)) continue;
    const baseMatch = /@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/.exec(text);
    const base = baseMatch ? baseMatch[1] : '';
    for (const m of text.matchAll(/@(Get|Post|Put|Delete|Patch)Mapping\b(\s*\(([^)]*)\))?/g)) {
      const args = m[3] || '';
      const quoted = [...args.matchAll(/"([^"]*)"/g)].map((q) => q[1]);
      const paths = quoted.length ? quoted : [''];
      for (const p of paths) {
        if (p.startsWith('text/') || p.startsWith('application/')) continue;
        const full = (base + (p.startsWith('/') ? p : p ? '/' + p : '')) || '/';
        routes.add(full.replace(/\/+$/, '') || '/');
      }
    }
  }
  return [...routes].sort();
}

export function frontendUrlLiterals(sources) {
  const urls = new Map();
  for (const { text, file } of sources) {
    const patterns = [
      /['"`](\/(?:api|internal|health|actuator)[A-Za-z0-9_./{}-]*)['"`]/g,
      /['"`](\/(?:api|internal|health|actuator)[A-Za-z0-9_./-]*\{X\}[A-Za-z0-9_./{}-]*)['"`]/g,
    ];
    for (const re of patterns) {
      for (const m of text.matchAll(re)) {
        const raw = m[1].replace(/\$\{[^}]*\}/g, '{X}');
        if (!urls.has(raw)) urls.set(raw, { file: relative(RESOURCES, file), line: lineOf(text, m.index) });
      }
    }
  }
  return urls;
}

export { read, relative, existsSync, join as joinPath, RESOURCES as resourcesRoot };
