import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const DOCS_DIR = resolve(__dirname, '../../main/resources/static/docs');

function readDoc(name) {
  return readFileSync(resolve(DOCS_DIR, name), 'utf8');
}

const germanGuide = readDoc('user-guide-de.md');
const englishGuide = readDoc('user-guide.md');
const germanApplicationDocs = readDoc('app-documentation-de.md');
const englishApplicationDocs = readDoc('app-documentation.md');

describe('Knowledge Database documentation', () => {
  it('documents the complete user workflow in German and English', () => {
    const expectedGermanSections = [
      '## Wissensdatenbank (RAG)',
      '### Embedding-Verbindung einrichten',
      '### Wissensdatenbanken verwalten',
      '### Quellen hinzufügen und aktualisieren',
      '### Wissen im globalen KI-Chat verwenden',
      '### Export und Import',
      '### Wartung, Reindexierung und Zurücksetzen',
    ];
    const expectedEnglishSections = [
      '## Knowledge Database (RAG)',
      '### Configuring the Embedding Connection',
      '### Managing Knowledge Bases',
      '### Adding and Updating Sources',
      '### Using Knowledge in the Global KI Chat',
      '### Export and Import',
      '### Maintenance, Reindexing, and Reset',
    ];

    expectedGermanSections.forEach((heading) => expect(germanGuide).toContain(heading));
    expectedEnglishSections.forEach((heading) => expect(englishGuide).toContain(heading));
  });

  it('documents every supported source, provider, and portable archive format', () => {
    const requiredTerms = [
      'Ollama',
      'OpenAI',
      'OpenRouter',
      'Confluence',
      'Jira',
      'PDF',
      '.slknowledge',
      '.slbundle',
      'AES-256-GCM',
      '[KB:document-4711#chunk-3]',
    ];

    requiredTerms.forEach((term) => {
      expect(germanGuide).toContain(term);
      expect(englishGuide).toContain(term);
    });
  });

  it('documents architecture, persistence entities, and REST endpoints in both application references', () => {
    const commonTechnicalTerms = [
      'KnowledgeRetrievalService',
      'KnowledgePublicationExecutor',
      'LuceneKnowledgeIndexAdapter',
      'KnnFloatVectorField',
      'KnowledgeEmbeddingConfig',
      'KnowledgeIndexGeneration',
      '/api/knowledge',
      '/reindex-all',
      '/export-all',
      '/import-bundle',
      'KNOWLEDGE_RESET_ON_FACTORY_RESET',
    ];

    commonTechnicalTerms.forEach((term) => {
      expect(germanApplicationDocs).toContain(term);
      expect(englishApplicationDocs).toContain(term);
    });
  });

  it('states the vector-only retrieval contract and model-change recovery path', () => {
    expect(germanGuide).toContain('es gibt keinen Stichwort-Fallback');
    expect(englishGuide).toContain('there is no keyword fallback');
    expect(germanApplicationDocs).toContain('es existiert kein BM25-Fallback');
    expect(englishApplicationDocs).toContain('there is no BM25 fallback');

    expect(germanGuide).toContain('Quellen mit dem neuen Modell erneut eingelesen');
    expect(englishGuide).toContain('ingest the sources again with the new model');
  });
});
