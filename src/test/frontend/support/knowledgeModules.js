import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const STATIC_DIR = resolve(here, '..', '..', '..', 'main', 'resources', 'static');

export const KNOWLEDGE_MODULE_PATHS = [
  'js/shared-helpers.js',
  'js/knowledge/core.js',
  'js/knowledge/config.js',
  'js/knowledge/sources.js',
  'js/knowledge/file-picker.js',
  'js/knowledge/archive.js',
  'js/knowledge/bases.js',
  'js/knowledge/add-source.js',
  'js/knowledge/maintenance.js',
  'js/knowledge/bootstrap.js',
];

export function readKnowledgeModule(relativePath) {
  return readFileSync(resolve(STATIC_DIR, relativePath), 'utf8');
}

export function readKnowledgeBundle() {
  return KNOWLEDGE_MODULE_PATHS.map(readKnowledgeModule).join('\n;\n');
}
