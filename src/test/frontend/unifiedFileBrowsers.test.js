import { describe, expect, it } from 'vitest';
import { staticSource, staticJsSource } from './support/sharedBrowserHarness.js';
import { readKnowledgeBundle } from './support/knowledgeModules.js';

const SITES = {
  'shared local repos': 'fragments/shared/scripts-local-repo.html',
  'load test analysis': 'fragments/lasttest-analysis/scripts-repo-panel.html',
  'security analysis': 'fragments/security-analysis/scripts-repo-panel.html',
  'ticket implementation': 'fragments/ticket-implementation/scripts.html',
  'logging analysis': 'fragments/logging/scripts-repo.html',
  'prompt browsers': 'fragments/layout/scripts-prompt-file-browsers.html',
  'review analysis export': 'fragments/review-analysis/scripts-export.html',
  'grafana analysis save': 'fragments/grafana-analysis/scripts-export-save-browser.html',
  'code analysis diagrams': 'fragments/code-analysis/scripts-diagrams.html',
  'load test save history': 'fragments/lasttest-analysis/scripts-save-history.html',
  'tooling export import': 'fragments/tooling/scripts-export-import.html',
  'agent pipeline mockups': 'fragments/ticket-implementation/scripts-agent-pipeline.html',
  'image extraction': 'fragments/image-extraction/scripts.html',
  'config import': 'fragments/config/scripts-io-browser.html',
  'ki chat attachments': 'fragments/layout/scripts-ki-chat.html',
};

const INHERITING_SITES = {
  'code analysis': 'fragments/code-analysis/scripts-file-browser-gitlab.html',
};

const PAGE_JS_SITES = {
  'knowledge base import': () => readKnowledgeBundle(),
  'knowledge file upload': () => readKnowledgeBundle(),
  'ticket refinement': () => staticJsSource('js/fragments/ticket-refinement.js'),
  'ticket creation attachments': () => staticJsSource('js/fragments/ticket-creation.js'),
};

describe('every analysis screen uses the one shared file browser', () => {
  Object.entries(SITES).forEach(([name, path]) => {
    it(`${name} delegates to __kiChatOpenDirBrowser`, () => {
      expect(staticSource(path)).toContain('__kiChatOpenDirBrowser(');
    });
  });

  Object.entries(SITES).concat(Object.entries(INHERITING_SITES)).forEach(([name, path]) => {
    it(`${name} ships no own directory-listing implementation`, () => {
      const source = staticSource(path);
      expect(source).not.toContain('/api/local-repos/list-directory');
    });
  });

  Object.entries(PAGE_JS_SITES).forEach(([name, readSource]) => {
    it(`${name} delegates to __kiChatOpenDirBrowser`, () => {
      expect(readSource()).toContain('__kiChatOpenDirBrowser(');
    });

    it(`${name} ships no own directory-listing implementation`, () => {
      expect(readSource()).not.toContain('/api/local-repos/list-directory');
    });
  });

  Object.entries(INHERITING_SITES).forEach(([name, path]) => {
    it(`${name} reuses the shared implementation instead of declaring its own`, () => {
      const source = staticSource(path);
      expect(source).not.toContain('function fbOpenBrowser');
      expect(source).not.toContain('function fbLoadDirectory');
      expect(source).toContain('function caLoadGitlabConfigs');
    });
  });

  it('keeps the shared loader as the only caller of the directory endpoint', () => {
    expect(staticSource('fragments/layout/scripts-sidebar.html')).toContain('/api/local-repos/list-directory');
  });

  it('no longer renders the retired browser overlays', () => {
    expect(staticSource('fragments/shared/modals-local-repo.html')).not.toContain('fileBrowserModal');
    expect(staticSource('fragments/ticket-implementation/modals.html')).not.toContain('taFbModal');
    expect(staticSource('lasttest-analysis.html')).not.toContain('ltFileBrowserModal');
    expect(staticSource('security-analysis.html')).not.toContain('baFileBrowserModal');
    expect(staticSource('fragments/layout/modals.html')).not.toContain('peExportOverlay');
    expect(staticSource('fragments/layout/modals.html')).not.toContain('peImportOverlay');
    expect(staticSource('review-analysis.html')).not.toContain('raExportOverlay');
    expect(staticSource('grafana-analysis.html')).not.toContain('gaDashSaveOverlay');
    expect(staticSource('fragments/code-analysis/modals.html')).not.toContain('caSaveOverlay');
    expect(staticSource('lasttest-analysis.html')).not.toContain('ltSaveOverlay');
    expect(staticSource('tooling.html')).not.toContain('exportOverlay');
    expect(staticSource('tool-view.html')).not.toContain('exportOverlay');
    expect(staticSource('ticket-creation.html')).not.toContain('tcFileBrowserOverlay');
    expect(staticSource('ticket-implementation.html')).not.toContain('taMockupFbModal');
    expect(staticSource('image-extraction.html')).not.toContain('ieBrowserOverlay');
    expect(staticSource('image-extraction.html')).not.toContain('ieSavePathOverlay');
    expect(staticSource('fragments/config/modals-io.html')).not.toContain('cfImportOverlay');
    expect(staticSource('fragments/config/modals-io.html')).not.toContain('cfSaveOverlay');
    expect(staticSource('fragments/layout/ki-chat-drawer.html')).not.toContain('kiChatBrowserOverlay');
    expect(staticSource('fragments/layout/ki-chat-drawer.html')).not.toContain('ki-chat-fb-item');
    expect(staticSource('knowledge.html')).not.toContain('file-picker-overlay');
    expect(readKnowledgeBundle()).not.toContain('kbFilePickerOverlay');
  });

  it('no page keeps a private fetch of the retired browser markup ids', () => {
    const retired = ['fbDirectoryList', 'ltFbDirectoryList', 'baFbDirectoryList', 'taFbDirList', 'peExportBrowserContent', 'peImportBrowserContent'];
    retired.forEach((id) => {
      expect(staticSource('fragments/shared/scripts-local-repo.html')).not.toContain(id);
    });
  });

  it('local repo pages keep their own scope when creating the repository', () => {
    expect(staticSource('fragments/lasttest-analysis/scripts-repo-panel.html')).toContain("scope: 'LOAD_TEST_ANALYSIS'");
    expect(staticSource('fragments/security-analysis/scripts-repo-panel.html')).toContain("scope: 'BUG_ANALYSIS'");
    expect(staticSource('fragments/ticket-implementation/scripts.html')).toContain("scope: 'TICKET_ANALYSIS'");
    expect(staticSource('fragments/shared/scripts-local-repo.html')).toContain('window.localRepoConfig.scope');
  });

  it('prompt export uses save-as mode and prompt import uses the ki file filter', () => {
    const prompts = staticSource('fragments/layout/scripts-prompt-file-browsers.html');
    expect(prompts).toContain('saveAs: { filename: filename }');
    expect(prompts).toContain("files: ['ki']");
    expect(prompts).toContain('/api/config/save-to-path');
    expect(prompts).toContain('/api/prompts/read-file?path=');
  });

  it('clone target selection reopens the clone modal with the picked directory', () => {
    const findings = staticSource('fragments/code-analysis/scripts-findings.html');
    expect(findings).toContain("window._fbTargetInputId = 'cloneTargetDir'");
    expect(findings).toContain('fbOpenBrowser(');
    expect(staticSource('fragments/shared/scripts-local-repo.html')).toContain("showModalById('cloneModal')");
  });

  it('save flows use save-as mode and keep their derived default filename', () => {
    expect(staticSource('fragments/code-analysis/scripts-diagrams.html')).toContain('saveAs: { filename: _caSaveFilename');
    expect(staticSource('fragments/lasttest-analysis/scripts-save-history.html')).toContain("saveAs: { filename: _ltSaveFilename }");
    expect(staticSource('fragments/layout/scripts-prompt-file-browsers.html')).toContain('saveAs: { filename: filename }');
  });

  it('attachment flows use multi-select and keep their own extension filter', () => {
    const creation = staticJsSource('js/fragments/ticket-creation.js');
    expect(creation).toContain('multi: true');
    expect(creation).toContain('selected: selected');
    expect(creation).toContain('onToggle:');
    expect(creation).toContain('TC_FB_EXTS');
    const mockups = staticSource('fragments/ticket-implementation/scripts-agent-pipeline.html');
    expect(mockups).toContain('multi: true');
    expect(mockups).toContain('_MOCKUP_IMAGE_EXTS');
  });

  it('the knowledge datei upload browser filters the supported document types', () => {
    const knowledge = readKnowledgeBundle();
    expect(knowledge).toContain("files: KB_FILE_EXTENSIONS");
    expect(knowledge).toContain("load: kbLoadKnowledgeFileEntries");
    expect(knowledge).toContain("storageKey: 'stacklens.fb.last.knowledgeFile'");
    expect(knowledge).toContain("fileIcon: 'bi-file-earmark-text'");
    expect(knowledge).toContain('awaitSelection: true');
    expect(knowledge).toContain("titleKey: 'knowledge.add.file_picker.title'");
    expect(knowledge).toContain('SLBrowseFiles({');
    expect(knowledge).not.toContain('function kbLoadFilePickerDirectory');
  });

  it('the ki chat attachment browser filters images and remembers its own folder', () => {
    const chat = staticSource('fragments/layout/scripts-ki-chat.html');
    expect(chat).toContain("files: __KI_CHAT_IMAGE_EXTS");
    expect(chat).toContain("load: __kiChatImageFileEntries");
    expect(chat).toContain("storageKey: 'stacklens.fb.last.kiChat'");
    expect(chat).toContain("fileIcon: 'bi-file-earmark-image'");
    expect(chat).not.toContain('multi: true');
  });

  it('config import filters json files through the shared browser and keeps the confirm flow', () => {
    const config = staticSource('fragments/config/scripts-io-browser.html');
    expect(config).toContain("files: CF_IMPORT_EXTS.split(',')");
    expect(config).toContain("storageKey: 'stacklens.fb.last.configImport'");
    expect(config).toContain('titleKey: \'config.import_file_select_title\'');
    expect(config).toContain('load: cfLoadImportEntries');
    expect(config).toContain('SLBrowseFiles({ path: path, fileExtensions: CF_IMPORT_EXTS');
    expect(config).toContain('/api/prompts/read-file?path=');
    expect(config).toContain('/api/config/import-from-path');
    expect(config).not.toContain('cfImportBrowserContent');
  });

  it('config export uses save-as mode and keeps its own folder memory', () => {
    const config = staticSource('fragments/config/scripts-io-browser.html');
    expect(config).toContain('saveAs: { filename: filename }');
    expect(config).toContain("storageKey: 'stacklens.fb.last.configExport'");
    expect(config).toContain("titleKey: 'common.save_location'");
    expect(config).toContain('load: cfLoadSaveDirectoryEntries');
    expect(config).toContain('SLBrowseFiles({ path: path, directoriesOnly: true');
    expect(config).toContain('/api/config/save-to-path');
    expect(config).not.toContain('cfSaveBrowserContent');
    expect(config).not.toContain('cfSaveConfirmBtn');
    expect(config).not.toContain('desktopApp');
  });

  it('image extraction keeps the server file size for the chip through a path cache', () => {
    const ie = staticSource('fragments/image-extraction/scripts.html');
    expect(ie).toContain('_ieFileSizeByPath');
    expect(ie).toContain('ieApplyServerFile');
    expect(ie).toContain('window._ie.fileSize = _ieFileSizeByPath[path] || 0');
  });

  it('grafana save keeps hiding the export code scrollbar while browsing', () => {
    const ga = staticSource('fragments/grafana-analysis/scripts-export-save-browser.html');
    expect(ga).toContain("exportCode.style.overflowY = 'hidden'");
    expect(ga).toContain("exportCode.style.overflowY = ''");
    expect(ga).toContain('_gaHideBackgroundScrollbars()');
  });

  it('the shared browser exposes the hooks the migrated flows rely on', () => {
    const browser = staticSource('fragments/layout/scripts-sidebar.html');
    ['saveAs', 'multi', 'startPath', 'onToggle', 'describe', 'options.load', 'options.fileIcon'].forEach((token) => {
      expect(browser).toContain(token);
    });
  });
});
