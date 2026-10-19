import crypto from 'crypto';
import fs from 'fs';
import fsp from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..', '..', '..', '..', '..', '..',
);
const fixtureDir = path.join(repoRoot, 'build', 'e2e-cdn-fixtures');
const manifestPath = path.join(fixtureDir, 'manifest.json');
const downloadTimeoutMs = 15_000;

export const CDN_HOSTS = [
  'https://cdn.jsdelivr.net',
  'https://cdn.datatables.net',
  'https://cdnjs.cloudflare.com',
];

export const CDN_ASSET_URLS = [
  'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css',
  'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css',
  'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/fonts/bootstrap-icons.woff2',
  'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/fonts/bootstrap-icons.woff',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js',
  'https://cdn.jsdelivr.net/npm/jquery@3.7.1/dist/jquery.min.js',
  'https://cdn.datatables.net/2.0.8/css/dataTables.bootstrap5.min.css',
  'https://cdn.datatables.net/2.0.8/js/dataTables.min.js',
  'https://cdn.datatables.net/2.0.8/js/dataTables.bootstrap5.min.js',
  'https://cdn.datatables.net/buttons/3.0.2/css/buttons.bootstrap5.min.css',
  'https://cdn.datatables.net/buttons/3.0.2/js/dataTables.buttons.min.js',
  'https://cdn.datatables.net/buttons/3.0.2/js/buttons.bootstrap5.min.js',
  'https://cdn.datatables.net/buttons/3.0.2/js/buttons.html5.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js',
];

const CONTENT_TYPES = {
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

let fixturesPromise = null;

export function fixtureKey(url) {
  const parsed = new URL(url);
  return `${parsed.origin}${parsed.pathname}`;
}

function fixtureName(url) {
  return crypto.createHash('sha1').update(url).digest('hex') + path.extname(new URL(url).pathname);
}

function fallbackContentType(url) {
  return CONTENT_TYPES[path.extname(new URL(url).pathname)] || 'application/octet-stream';
}

async function readManifest() {
  try {
    return JSON.parse(await fsp.readFile(manifestPath, 'utf8'));
  } catch {
    return {};
  }
}

function tempPath(target) {
  return `${target}.${process.pid}.${crypto.randomBytes(4).toString('hex')}.tmp`;
}

async function writeManifest(manifest) {
  const temp = tempPath(manifestPath);
  await fsp.writeFile(temp, JSON.stringify(manifest, null, 2));
  await fsp.rename(temp, manifestPath);
}

async function download(url) {
  const file = fixtureName(url);
  const target = path.join(fixtureDir, file);
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(downloadTimeoutMs) });
    if (!response.ok) return null;
    const body = Buffer.from(await response.arrayBuffer());
    const temp = tempPath(target);
    await fsp.writeFile(temp, body);
    await fsp.rename(temp, target);
    const header = (response.headers.get('content-type') || '').split(';')[0].trim();
    return { file, contentType: header || fallbackContentType(url) };
  } catch {
    return null;
  }
}

export async function refreshCdnFixtures() {
  await fsp.mkdir(fixtureDir, { recursive: true });
  const manifest = await readManifest();
  let downloaded = 0;
  let missing = 0;
  for (const url of CDN_ASSET_URLS) {
    const cached = manifest[url];
    if (cached && fs.existsSync(path.join(fixtureDir, cached.file))) continue;
    const entry = await download(url);
    if (entry) {
      manifest[url] = entry;
      downloaded += 1;
    } else {
      missing += 1;
    }
  }
  if (downloaded > 0) await writeManifest(manifest);
  return { served: Object.keys(manifest).length, downloaded, missing, total: CDN_ASSET_URLS.length };
}

export async function loadCdnFixtures() {
  if (!fixturesPromise) {
    fixturesPromise = (async () => {
      const manifest = await readManifest();
      const fixtures = new Map();
      for (const [url, entry] of Object.entries(manifest)) {
        const body = await fsp.readFile(path.join(fixtureDir, entry.file)).catch(() => null);
        if (body) fixtures.set(fixtureKey(url), { contentType: entry.contentType || fallbackContentType(url), body });
      }
      return fixtures;
    })();
  }
  return fixturesPromise;
}
