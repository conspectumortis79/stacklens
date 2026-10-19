import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';



function paShowModal(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.className = (el.className || '').replace(/\bshow\b/g, '').trim() + ' show';
  el.style.setProperty('display', 'block', 'important');
  el.removeAttribute('aria-hidden');
  el.setAttribute('aria-modal', 'true');
  document.body.className =
    (document.body.className || '').replace(/\bmodal-open\b/g, '').trim() +
    ' modal-open';
  if (!document.querySelector('.modal-backdrop')) {
    const bd = document.createElement('div');
    bd.className = 'modal-backdrop fade show';
    bd.style.setProperty('display', 'block', 'important');
    document.body.appendChild(bd);
  }
}

function paHideModal(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.className = (el.className || '').replace(/\bshow\b/g, '').trim();
  el.style.setProperty('display', 'none', 'important');
  el.setAttribute('aria-hidden', 'true');
  el.removeAttribute('aria-modal');
  const bd = document.querySelector('.modal-backdrop');
  if (bd) bd.parentNode.removeChild(bd);
  if (!document.querySelector('.modal.show')) {
    document.body.className =
      (document.body.className || '').replace(/\bmodal-open\b/g, '').trim();
  }
}


const PA_REPO_MODAL_HTML = `
<div class="modal fade" id="paRepoModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-dialog-centered">
    <div class="modal-content">
      <div class="modal-header fb-modal-header">
        <h5 class="modal-title">Repository hinzufügen</h5>
        <button type="button" id="paRepoModalX" class="btn-close"
                data-bs-dismiss="modal"></button>
      </div>
      <div class="modal-body">
        <input id="paRepoName" type="text"/>
        <input id="paRepoPath" type="text"/>
      </div>
      <div class="modal-footer">
        <button id="paRepoModalCancel" class="btn btn-outline-secondary"
                data-bs-dismiss="modal">Abbrechen</button>
        <button id="paRepoModalSave" class="btn">Speichern</button>
      </div>
    </div>
  </div>
</div>
`;

const PA_FILE_BROWSER_MODAL_HTML = `
<div class="modal fade" id="paFileBrowserModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-dialog-centered">
    <div class="modal-content">
      <div class="modal-header fb-modal-header">
        <h5 class="modal-title">Verzeichnis auswählen</h5>
        <button type="button" id="paFileBrowserModalX" class="btn-close"
                data-bs-dismiss="modal"></button>
      </div>
      <div class="modal-body"></div>
      <div class="modal-footer">
        <button id="paFileBrowserModalCancel" class="btn btn-outline-secondary"
                data-bs-dismiss="modal">Abbrechen</button>
        <button id="paFileBrowserModalSelect" class="btn">Auswählen</button>
      </div>
    </div>
  </div>
</div>
`;

function setupDom() {
  document.body.innerHTML = PA_REPO_MODAL_HTML + PA_FILE_BROWSER_MODAL_HTML;
  document
    .getElementById('paRepoModalX')
    .addEventListener('click', () => paHideModal('paRepoModal'));
  document
    .getElementById('paRepoModalCancel')
    .addEventListener('click', () => paHideModal('paRepoModal'));
  document
    .getElementById('paFileBrowserModalX')
    .addEventListener('click', () => paHideModal('paFileBrowserModal'));
  document
    .getElementById('paFileBrowserModalCancel')
    .addEventListener('click', () => paHideModal('paFileBrowserModal'));
}

describe('Pipeline-Analyse modal close buttons (X and Abbrechen)', () => {

  beforeEach(() => {
    setupDom();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });


  describe('paRepoModal', () => {
    it('opens via paShowModal (display: block !important, class "show")', () => {
      paShowModal('paRepoModal');
      const m = document.getElementById('paRepoModal');
      expect(m.style.display).toBe('block');
      expect(m.style.getPropertyPriority('display')).toBe('important');
      expect(m.classList.contains('show')).toBe(true);
      expect(document.querySelector('.modal-backdrop')).not.toBeNull();
      expect(document.body.classList.contains('modal-open')).toBe(true);
    });

    it('clicking the X button closes the modal (display: none, "show" removed)', () => {
      paShowModal('paRepoModal');
      const x = document.getElementById('paRepoModalX');
      x.click();
      const m = document.getElementById('paRepoModal');
      expect(m.style.display).toBe('none');
      expect(m.style.getPropertyPriority('display')).toBe('important');
      expect(m.classList.contains('show')).toBe(false);
      expect(m.getAttribute('aria-hidden')).toBe('true');
      expect(m.hasAttribute('aria-modal')).toBe(false);
      expect(document.querySelector('.modal-backdrop')).toBeNull();
      expect(document.body.classList.contains('modal-open')).toBe(false);
    });

    it('clicking the Abbrechen (Cancel) button closes the modal', () => {
      paShowModal('paRepoModal');
      const cancel = document.getElementById('paRepoModalCancel');
      cancel.click();
      const m = document.getElementById('paRepoModal');
      expect(m.style.display).toBe('none');
      expect(m.classList.contains('show')).toBe(false);
      expect(document.body.classList.contains('modal-open')).toBe(false);
    });

    it('clicking the X button removes the modal backdrop', () => {
      paShowModal('paRepoModal');
      expect(document.querySelector('.modal-backdrop')).not.toBeNull();
      document.getElementById('paRepoModalX').click();
      expect(document.querySelector('.modal-backdrop')).toBeNull();
    });

    it('clicking the Abbrechen button removes the modal backdrop', () => {
      paShowModal('paRepoModal');
      expect(document.querySelector('.modal-backdrop')).not.toBeNull();
      document.getElementById('paRepoModalCancel').click();
      expect(document.querySelector('.modal-backdrop')).toBeNull();
    });
  });


  describe('paFileBrowserModal', () => {
    it('opens via paShowModal (display: block !important, class "show")', () => {
      paShowModal('paFileBrowserModal');
      const m = document.getElementById('paFileBrowserModal');
      expect(m.style.display).toBe('block');
      expect(m.style.getPropertyPriority('display')).toBe('important');
      expect(m.classList.contains('show')).toBe(true);
      expect(document.querySelector('.modal-backdrop')).not.toBeNull();
    });

    it('clicking the X button closes the modal', () => {
      paShowModal('paFileBrowserModal');
      const m = document.getElementById('paFileBrowserModal');
      expect(m.style.display).toBe('block');

      document.getElementById('paFileBrowserModalX').click();

      expect(m.style.display).toBe('none');
      expect(m.classList.contains('show')).toBe(false);
      expect(m.getAttribute('aria-hidden')).toBe('true');
      expect(document.body.classList.contains('modal-open')).toBe(false);
    });

    it('clicking the Abbrechen button closes the modal', () => {
      paShowModal('paFileBrowserModal');
      const m = document.getElementById('paFileBrowserModal');
      expect(m.style.display).toBe('block');

      document.getElementById('paFileBrowserModalCancel').click();

      expect(m.style.display).toBe('none');
      expect(m.classList.contains('show')).toBe(false);
      expect(document.body.classList.contains('modal-open')).toBe(false);
    });
  });

});