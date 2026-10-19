
if (typeof window.escapeHtml !== 'function') {
    window.escapeHtml = function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };
}

if (typeof window._appVisible === 'undefined') window._appVisible = true;

const _ALERT_ICONS = {
    success: 'bi-check-circle-fill',
    danger:  'bi-x-octagon-fill',
    warning: 'bi-exclamation-triangle-fill',
    info:    'bi-info-circle-fill'
};
const _ALERT_PALETTES = {
    modern: {
        success: { bg: '#f0fdf4', border: '#86efac', accent: '#1e7a3a', text: '#1f1f1f' },
        danger:  { bg: '#fef2f2', border: '#fca5a5', accent: '#b91c1c', text: '#1f1f1f' },
        warning: { bg: '#fffbeb', border: '#fcd34d', accent: '#7a5a0a', text: '#1f1f1f' },
        info:    { bg: '#eff6ff', border: '#93c5fd', accent: '#1e3a8a', text: '#1f1f1f' }
    },
    dark: {
        success: { bg: '#1a2b1c', border: '#2e5432', accent: '#4ade80', text: '#ece6ff' },
        danger:  { bg: '#2a1414', border: '#5c1c1c', accent: '#f87171', text: '#ece6ff' },
        warning: { bg: '#2a2010', border: '#5c4a18', accent: '#fcd34d', text: '#ece6ff' },
        info:    { bg: '#1a1c30', border: '#363080', accent: '#a07ef5', text: '#ece6ff' }
    },
    popos: {
        success: { bg: '#1b2b1e', border: '#2e5c32', accent: '#3dba70', text: '#e8e8f0' },
        danger:  { bg: '#2b1b1b', border: '#5c2020', accent: '#f07878', text: '#e8e8f0' },
        warning: { bg: '#2b2010', border: '#604510', accent: '#faa41a', text: '#e8e8f0' },
        info:    { bg: '#1b222e', border: '#2e4a60', accent: '#48b9c7', text: '#e8e8f0' }
    },
    legacy: {
        success: { bg: '#e8f5e9', border: '#a5d6a7', accent: '#126a5a', text: '#212529' },
        danger:  { bg: '#fde8e8', border: '#ef9a9a', accent: '#842029', text: '#212529' },
        warning: { bg: '#fff8e1', border: '#ffe082', accent: '#856404', text: '#212529' },
        info:    { bg: '#e3f2fd', border: '#90caf9', accent: '#0d6efd', text: '#212529' }
    }
};

function showAlert(message, type = 'info') {
    const area = document.getElementById('alertArea');
    if (!area) return;

    const msgStr = String(message || '');

    const existing = Array.from(area.querySelectorAll('[data-sl-toast]')).find(
        el => el.dataset.slToast === msgStr
    );
    if (existing) {
        existing.style.transition = 'none';
        existing.style.opacity = '0.3';
        setTimeout(() => { existing.style.transition = 'opacity .2s ease'; existing.style.opacity = '1'; }, 150);
        return;
    }

    const _theme = (document.documentElement.getAttribute('data-theme') || 'modern').toLowerCase();
    const _palette = (_ALERT_PALETTES[_theme] || _ALERT_PALETTES.modern);
    const p = _palette[type] || _palette.info;
    const icon = _ALERT_ICONS[type] || _ALERT_ICONS.info;
    const id = 'sl-toast-' + Date.now();
    const closeLabel = window.i18n ? window.i18n.t('common.close') : 'Schließen';
    const safe = msgStr.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    const el = document.createElement('div');
    el.id = id;
    el.className = `alert alert-${type}`;
    el.dataset.slToast = msgStr;
    el.style.cssText =
        'pointer-events:auto;width:100%;max-width:420px;padding:11px 14px;border-radius:12px;'
        + `background:${p.bg};border:1.5px solid ${p.border};`
        + `box-shadow:0 6px 22px rgba(0,0,0,.13);font-size:13px;color:${p.text};`
        + 'display:flex;align-items:flex-start;gap:10px;font-family:inherit;font-weight:700;'
        + 'transition:opacity .25s ease,transform .25s ease;opacity:0;transform:translateY(10px);'
        + 'margin-top:8px;';
    el.innerHTML =
        `<i class="bi ${icon}" style="color:${p.accent};font-size:16px;flex-shrink:0;margin-top:1px;"></i>`
        + `<div style="flex:1;line-height:1.5;">${safe}</div>`
        + `<button title="${closeLabel}" onclick="this.closest('[data-sl-toast]').remove()" `
        + `style="background:none;border:none;color:${p.text};opacity:0.55;font-size:18px;line-height:1;cursor:pointer;padding:0;flex-shrink:0;">&times;</button>`;

    area.appendChild(el);
    requestAnimationFrame(() => {
        el.style.opacity = '1';
        el.style.transform = 'translateY(0)';
    });

    const duration = type === 'danger' ? 8000 : 6000;
    setTimeout(() => {
        const a = document.getElementById(id);
        if (!a) return;
        a.style.opacity = '0';
        a.style.transform = 'translateY(10px)';
        setTimeout(() => { const b = document.getElementById(id); if (b) b.remove(); }, 270);
    }, duration);
}

function showConfirm(message, title, onConfirm, okLabel, okIcon, okColor) {
    var msgEl        = document.getElementById('confirmModalMessage');
    var titleEl      = document.getElementById('confirmModalTitle');
    var okBtn        = document.getElementById('confirmModalOkBtn');
    var headerIconEl = document.getElementById('confirmModalHeaderIcon');
    if (!msgEl || !okBtn) {
        if (confirm(message) && onConfirm) onConfirm();
        return;
    }
    msgEl.textContent   = message || '';
    titleEl.textContent = title   || (window.i18n ? window.i18n.t('confirm.title')  : 'Bestätigung');
    var lbl   = okLabel || (window.i18n ? window.i18n.t('confirm.ok') : 'Löschen');
    var icon  = okIcon  || 'bi-trash';
    var color = okColor || '#b91c1c';
    if (headerIconEl) {
        headerIconEl.className = 'bi ' + icon + ' me-2';
        headerIconEl.style.color = color;
    }
    okBtn.style.background = color;
    okBtn.innerHTML = '<i class="bi ' + icon + ' me-1"></i>' + lbl;
    var fresh = okBtn.cloneNode(true);
    okBtn.parentNode.replaceChild(fresh, okBtn);
    fresh.style.background = color;
    fresh.addEventListener('click', function() {
        __hideConfirmModal();
        if (onConfirm) onConfirm();
    });
    __showConfirmModal();
}

function __showConfirmModal() {
    var el = document.getElementById('confirmModal');
    if (!el) return;
    el.style.setProperty('display', 'block', 'important');
    el.getBoundingClientRect();
    if (el.className.indexOf('show') === -1) el.className += ' show';
    el.removeAttribute('aria-hidden');
    el.setAttribute('aria-modal', 'true');
    if (document.body.className.indexOf('modal-open') === -1)
        document.body.className += ' modal-open';
    if (!document.querySelector('.modal-backdrop')) {
        var bd = document.createElement('div');
        bd.className = 'modal-backdrop fade show';
        bd.style.setProperty('display', 'block', 'important');
        document.body.appendChild(bd);
    }
    if (typeof _gaHideBackgroundScrollbars === 'function') _gaHideBackgroundScrollbars();
}

function __hideConfirmModal() {
    var el = document.getElementById('confirmModal');
    if (!el) return;
    el.className = el.className.replace(/\bshow\b/g, '').trim();
    el.style.setProperty('display', 'none', 'important');
    el.setAttribute('aria-hidden', 'true');
    el.removeAttribute('aria-modal');
    var bd = document.querySelector('.modal-backdrop');
    if (bd && bd.parentNode) bd.parentNode.removeChild(bd);
    if (!document.querySelector('.modal.show'))
        document.body.className = document.body.className.replace(/\bmodal-open\b/g, '').trim();
    if (typeof _gaRestoreBackgroundScrollbars === 'function') _gaRestoreBackgroundScrollbars();
}

function togglePw(inputId) {
    const inp = document.getElementById(inputId);
    if (!inp) return;
    inp.type = inp.type === 'password' ? 'text' : 'password';
}

function resetForm(ids) {
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
}

function _t(k) { return window.i18n ? window.i18n.t(k) : k; }

if (typeof window !== 'undefined') {
    window.showAlert = showAlert;
    window.showConfirm = showConfirm;
    window.__showConfirmModal = __showConfirmModal;
    window.__hideConfirmModal = __hideConfirmModal;
    window.togglePw = togglePw;
    window.resetForm = resetForm;
}

async function uninstallTool(id) {
    var confirmMsg = window.i18n ? window.i18n.t('tooling.delete_confirm') : 'Tool und JAR-Datei wirklich löschen?';
    var confirmTitle = window.i18n ? window.i18n.t('tooling.delete_title') : 'Tool löschen';
    showConfirm(confirmMsg, confirmTitle, async function() {
        try {
            await fetch(`/api/tooling/${id}`, { method: 'DELETE' });
            showAlert(window.i18n ? window.i18n.t('config.deleted') : 'Tool wurde entfernt.', 'success');
            loadSidebarTools();
            if (typeof reloadToolList === 'function') {
                reloadToolList();
            }
        } catch (e) {
            showAlert(window.i18n ? window.i18n.t('common.error') : 'Fehler beim Löschen des Tools', 'danger');
        }
    });
}

async function runSidebarTool(id, btn) {
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split"></i>'; }
    try {
        const res  = await fetch(`/api/tooling/${id}/run`, { method: 'POST' });
        const data = await res.json();
        if (!res.ok) {
            showAlert((window.i18n ? window.i18n.t('tooling.error_run') : 'Fehler:') + ' ' + (data.error || ''), 'danger');
        } else {
            showAlert(window.i18n ? window.i18n.t('tooling.run_success') : 'Tool ausgeführt.', 'success');
        }
    } catch (e) {
        showAlert((window.i18n ? window.i18n.t('tooling.error_network') : 'Netzwerkfehler:') + ' ' + e.message, 'danger');
    } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-play-fill"></i>'; }
    }
}

function loadSidebarTools(autoExpand = false) {
    const sidebarNav   = document.getElementById('sidebarNav');
    const separator    = document.getElementById('sidebarToolsSeparator');
    const labelEl      = document.getElementById('sidebarToolsEmpty');
    const toolsLi      = document.getElementById('sidebarDynamicToolsLi');
    const toolsContainer = document.getElementById('sidebarDynamicTools');
    if (!sidebarNav) return;

    fetch('/api/tooling', { cache: 'no-store' })
        .then(r => r.ok ? r.json() : [])
        .then(tools => {
            sidebarNav.querySelectorAll('.sidebar-tool-entry').forEach(el => el.remove());
            if (toolsContainer) toolsContainer.innerHTML = '';

            const ready = tools.filter(t => t.status === 'READY').sort((a, b) => a.name.localeCompare(b.name, 'de', { sensitivity: 'base' }));

            if (ready.length === 0) {
                if (separator) separator.style.display = 'none';
                if (labelEl)   labelEl.style.display   = 'none';
                if (toolsLi)   toolsLi.style.display   = 'none';
                return;
            }

            if (separator) separator.style.display = '';
            if (labelEl)   labelEl.style.display   = '';
            if (toolsLi)   toolsLi.style.display   = '';

            let savedOrder = null;
            try {
                const all = JSON.parse(localStorage.getItem('sl-nav-section-order') || '{}');
                if (Array.isArray(all['tools'])) savedOrder = all['tools'];
            } catch(_) {}

            let orderedTools = ready;
            if (savedOrder) {
                const byId = {};
                ready.forEach(t => { byId[String(t.id)] = t; });
                const sorted = savedOrder.map(id => byId[id]).filter(Boolean);
                const rest = ready.filter(t => !savedOrder.includes(String(t.id)));
                orderedTools = sorted.concat(rest);
            }

            orderedTools.forEach(t => {
                const isActive = window.location.pathname === '/tool/' + t.id;
                const wrapper = document.createElement('div');
                wrapper.className = 'sl-nav-drag-item';
                wrapper.setAttribute('data-nav-id', String(t.id));
                wrapper.innerHTML = `
                  <div class="d-flex align-items-center" style="overflow:hidden;flex:1;min-width:0;">
                    <a draggable="false" href="/tool/${t.id}"
                       class="nav-link text-white sidebar-link flex-grow-1 text-truncate${isActive ? ' active' : ''}"
                       style="font-size:13px;padding:0.45rem 0.9rem;" title="${escNavHtml(t.name)}">
                      <span class="tl-sidebar-icon-badge me-2"><i class="bi ${t.icon || 'bi-play-circle'}"></i></span>${escNavHtml(t.name)}
                    </a>
                    <button class="btn btn-link text-danger p-0 px-2 border-0 flex-shrink-0"
                            title="${escNavHtml(window.i18n ? window.i18n.t('common.delete') : 'Löschen')}"
                            onclick="uninstallTool(${t.id})"
                            style="font-size:0.78rem;opacity:0.65;">
                      <i class="bi bi-x-lg"></i>
                    </button>
                    <button class="btn btn-link text-white p-0 px-2 border-0 flex-shrink-0"
                            title="${escNavHtml(window.i18n ? window.i18n.t('tooling.run') : 'Ausführen')}"
                            onclick="runSidebarTool(${t.id}, this)"
                            style="font-size:0.82rem;opacity:0.75;">
                      <i class="bi bi-play-fill"></i>
                    </button>
                  </div>`;
                if (toolsContainer) toolsContainer.appendChild(wrapper);
            });

            if (typeof window.__slInitNavSections === 'function') {
                window.__slInitNavSections();
            }
        })
        .catch(() => {});
}

function _syncSidebarActive() {
    const path  = window.location.pathname;
    const focus = new URLSearchParams(window.location.search).get('focus');

    let activeLink = null;
    document.querySelectorAll('.sidebar-link').forEach(link => {
        const href = link.getAttribute('href') || link.getAttribute('data-href') || '';
        let active;
        if (href.includes('?focus=')) {
            try {
                const lUrl = new URL(href, location.origin);
                active = lUrl.pathname === path
                    && new URLSearchParams(lUrl.search).get('focus') === focus;
            } catch (_e) { active = false; }
        } else {
            active = !focus && (href === path || (href !== '/' && path.startsWith(href)));
        }
        link.classList.toggle('active', active);
        if (active && !activeLink) activeLink = link;
    });

    if (activeLink) {
        var activeItems = activeLink.closest('.sg-subgroup-items');
        if (activeItems && !activeItems.classList.contains('open')) {
            activeItems.classList.add('open');
            var activeHeader = activeItems.previousElementSibling;
            if (activeHeader) activeHeader.classList.add('open');
        }
    }

    if (activeLink) {
        var nav = document.getElementById('sidebarNav');
        if (nav) {
            setTimeout(function() {
                var linkRect = activeLink.getBoundingClientRect();
                var navRect  = nav.getBoundingClientRect();
                if (linkRect.top < navRect.top) {
                    nav.scrollTop += linkRect.top - navRect.top - 8;
                } else if (linkRect.bottom > navRect.bottom) {
                    nav.scrollTop += linkRect.bottom - navRect.bottom + 8;
                }
            }, 270);
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    _syncSidebarActive();
    loadSidebarTools();
});

(function () {
    let _navGen = 0;

    function rerunScripts(container) {
        container.querySelectorAll('script').forEach(old => {
            const text = old.textContent.trim();
            if (old.src) {
                window.SpaPageScripts.loadScript(old.src);
            } else if (text) {
                window.SpaPageScripts.runScript(old.textContent);
            }
        });
    }

    async function navigateTo(url, push = true) {
        const _thisGen = ++_navGen;
        if (window._ta) window._ta.gen++;
        if (window._ra) window._ra.gen++;
        if (window._sp) window._sp.gen++;
        if (window._ts) window._ts.gen++;
        if (window._sr) window._sr.gen++;
        if (window._pa) { window._pa.gen++; if (window._pa.pollTimer) { clearInterval(window._pa.pollTimer); window._pa.pollTimer = null; } }
        if (window._tlGen) { window._tlGen.active = false; if (window._tlGen.poll) { clearTimeout(window._tlGen.poll); window._tlGen.poll = null; } }
        try {
            const res = await fetch(url);
            if (_thisGen !== _navGen) return;
            if (!res.ok) { location.href = url; return; }
            const html = await res.text();
            if (_thisGen !== _navGen) return;
            const curMain = document.querySelector('main');
            if (!curMain) { location.href = url; return; }

            const doc = new DOMParser().parseFromString(html, 'text/html');
            if (doc.title) document.title = doc.title;
            const alertArea = document.getElementById('alertArea');
            if (alertArea) alertArea.innerHTML = '';

            const mainMatch = html.match(/<main\b[^>]*>([\s\S]*?)<\/main\s*>/i);
            if (!mainMatch) { location.href = url; return; }

            if (window.GrafanaBoard) {
                window.GrafanaBoard.destroy();
                const _gbModal = document.getElementById('gbPanelModal');
                if (_gbModal) _gbModal.remove();
            }

            document.querySelectorAll('.modal.show, .modal.showing').forEach(function(el) {
                try {
                    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                        const inst = bootstrap.Modal.getInstance(el);
                        if (inst) inst.dispose();
                    }
                } catch (_) {}
            });

            const _diffOverlay = document.getElementById('baFixPrDiffOverlay');
            if (_diffOverlay) _diffOverlay.remove();
            document.querySelectorAll('.modal-backdrop').forEach(function(bd) { bd.remove(); });
            document.body.classList.remove('modal-open');

            curMain.innerHTML = mainMatch[1];

            document.querySelectorAll('.modal-backdrop').forEach(function(bd) { bd.remove(); });
            document.body.classList.remove('modal-open');

            try { delete window.kiChatConfig; } catch (_) { window.kiChatConfig = undefined; }
            try { if (typeof window.kiChatHideForNavigation === 'function') window.kiChatHideForNavigation(); } catch (_) {}
            const headEndIdx = html.search(/<\/head\s*>/i);
            const headHtml   = headEndIdx >= 0 ? html.slice(0, headEndIdx) : '';

            try {
                const newMcpMeta = doc.querySelector('meta[name="mcp-sources"]');
                let liveMeta = document.head.querySelector('meta[name="mcp-sources"]');
                if (newMcpMeta) {
                    if (!liveMeta) {
                        liveMeta = document.createElement('meta');
                        liveMeta.setAttribute('name', 'mcp-sources');
                        document.head.appendChild(liveMeta);
                    }
                    liveMeta.setAttribute('content', newMcpMeta.getAttribute('content') || '');
                } else if (liveMeta) {
                    liveMeta.parentNode.removeChild(liveMeta);
                }
            } catch (_) {}
            const hsRe = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
            let hm;
            while ((hm = hsRe.exec(headHtml)) !== null) {
                if (!hm[1].trim()) continue;
                window.SpaPageScripts.runScript(hm[1]);
            }

            try {
                const linkRe = /<link\b[^>]*?\brel\s*=\s*["']stylesheet["'][^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*?>/gi;
                let lm;
                const existingHrefs = new Set();
                document.head.querySelectorAll('link[rel="stylesheet"]').forEach(function (l) {
                    if (l.getAttribute('href')) existingHrefs.add(l.getAttribute('href'));
                });
                while ((lm = linkRe.exec(headHtml)) !== null) {
                    const href = lm[1];
                    if (!href || existingHrefs.has(href)) continue;
                    const link = document.createElement('link');
                    link.rel = 'stylesheet';
                    link.href = href;
                    link.setAttribute('data-spa-injected', '1');
                    document.head.appendChild(link);
                    existingHrefs.add(href);
                }
            } catch (_) {
            }

            rerunScripts(curMain);

            const mainEndIdx = html.search(/<\/main\s*>/i);
            const afterMain  = mainEndIdx >= 0 ? html.slice(mainEndIdx) : '';
            await window.SpaPageScripts.runAfterMain(afterMain, { isCancelled: () => _thisGen !== _navGen });
            if (_thisGen !== _navGen) return;

            if (push) history.pushState({ url }, '', url);
            _syncSidebarActive();
            try { if (typeof window.kiChatRestoreForCurrentTab === 'function') window.kiChatRestoreForCurrentTab(); } catch (_) {}
            document.dispatchEvent(new CustomEvent('app:navigate', { detail: { url } }));
        } catch (_e) {
            if (_thisGen === _navGen) {
                showAlert(window.i18n ? window.i18n.t('common.nav_error') : 'Navigation fehlgeschlagen – Seite wird neu geladen…', 'warning');
                location.href = url;
            }
        }
    }

    document.addEventListener('click', e => {
        const a = e.target.closest('.sidebar-link');
        if (!a) return;
        const href = a.getAttribute('href');
        if (!href || href.startsWith('#') || /^(https?:)?\/\//.test(href)) return;
        e.preventDefault();
        navigateTo(href);
    });

    window.addEventListener('popstate', e => {
        navigateTo((e.state && e.state.url) || location.href, false);
    });

    history.replaceState({ url: location.href }, '', location.href);

    window.navigateTo = navigateTo;
})();

function escNavHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

window.SLBrowseFiles = async function (opts) {
    const o = opts || {};
    const parts = [];
    if (o.path) parts.push('path=' + encodeURIComponent(o.path));
    if (o.fileExtensions) parts.push('fileExtensions=' + encodeURIComponent(o.fileExtensions));
    if (o.directoriesOnly) parts.push('directoriesOnly=true');
    const qs = parts.length ? '?' + parts.join('&') : '';
    const url = '/api/tooling/files/browse' + qs;
    try {
        const res = await fetch(url);
        if (!res.ok) {
            const txt = await res.text().catch(function () { return ''; });
            if (!o.silent) {
                showAlert(
                    (window.i18n ? window.i18n.t('tooling.error_load_dir') : 'Fehler beim Laden des Verzeichnisses') + ': ' + (txt || ('HTTP ' + res.status)),
                    'danger'
                );
            }
            return { ok: false, status: res.status, message: txt || ('HTTP ' + res.status) };
        }
        return { ok: true, data: await res.json() };
    } catch (e) {
        const msg = e && e.message ? e.message : String(e);
        if (!o.silent) {
            showAlert(
                (window.i18n ? window.i18n.t('tooling.error_load_dir') : 'Fehler beim Laden des Verzeichnisses') + ': ' + msg,
                'danger'
            );
        }
        return { ok: false, status: 0, message: msg };
    }
};

function escHtml(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function SLScroll(box, track, thumb) {
    if (!box || !track || !thumb) return { update: function() {} };

    function update() {
        var trackH      = track.clientHeight;
        var ratio       = box.scrollHeight > 0 ? box.clientHeight / box.scrollHeight : 1;
        var thumbH      = Math.max(32, ratio * trackH);
        var maxScroll   = box.scrollHeight - box.clientHeight;
        var scrollRatio = maxScroll > 0 ? box.scrollTop / maxScroll : 0;
        thumb.style.height    = thumbH + 'px';
        thumb.style.transform = 'translateY(' + (scrollRatio * (trackH - thumbH)) + 'px)';
        var needsScroll = box.scrollHeight > box.clientHeight;
        if (track.dataset.alwaysVisible) {
            thumb.style.visibility = needsScroll ? '' : 'hidden';
        } else {
            track.style.visibility = needsScroll ? '' : 'hidden';
        }
    }

    box.addEventListener('scroll', update);

    track.addEventListener('click', function(e) {
        if (e.target === thumb) return;
        var rect  = track.getBoundingClientRect();
        var ratio = (e.clientY - rect.top) / track.clientHeight;
        box.scrollTop = ratio * (box.scrollHeight - box.clientHeight);
    });

    var _dragStartY = 0, _dragStartScrollTop = 0;
    thumb.addEventListener('mousedown', function(e) {
        e.preventDefault();
        thumb.classList.add('dragging');
        _dragStartY         = e.clientY;
        _dragStartScrollTop = box.scrollTop;
        document.addEventListener('mousemove', _onDrag);
        document.addEventListener('mouseup',   _onDragEnd);
    });
    function _onDrag(e) {
        var dy          = e.clientY - _dragStartY;
        var scrollRange = box.scrollHeight - box.clientHeight;
        var trackUsable = track.clientHeight - thumb.clientHeight;
        box.scrollTop   = trackUsable > 0
            ? _dragStartScrollTop + (dy / trackUsable) * scrollRange
            : _dragStartScrollTop;
    }
    function _onDragEnd() {
        thumb.classList.remove('dragging');
        document.removeEventListener('mousemove', _onDrag);
        document.removeEventListener('mouseup',   _onDragEnd);
    }

    new MutationObserver(update).observe(box, { childList: true, subtree: true, characterData: true });
    window.addEventListener('resize', update);
    update();

    return { update: update };
}

if (typeof window !== 'undefined') {
    window.escHtml = escHtml;
    window.escapeHtml = escHtml;
    window.ieEsc = escHtml;
    window.SLScroll = SLScroll;
}

document.addEventListener('keydown', function(e) {
    if (e.key !== 'Escape') return;

    function tryClose(overlayId, closeFn) {
        var el = document.getElementById(overlayId);
        if (el && el.style.display !== 'none' && el.style.display !== '') {
            if (typeof closeFn === 'function') closeFn();
            else { el.style.display = 'none'; document.body.style.overflow = ''; }
            return true;
        }
        return false;
    }

    if (tryClose('editOverlay',   typeof closeEditOverlay   === 'function' ? closeEditOverlay   : null)) return;
    if (tryClose('promptOverlay', typeof closePromptOverlay === 'function' ? closePromptOverlay : null)) return;
    if (tryClose('importOverlay', typeof closeImportOverlay === 'function' ? closeImportOverlay : null)) return;
    if (tryClose('exportOverlay', typeof closeExportOverlay === 'function' ? closeExportOverlay : null)) return;

    if (tryClose('ltSaveOverlay', typeof ltCloseSaveOverlay === 'function' ? ltCloseSaveOverlay : null)) return;

    if (tryClose('caSaveOverlay', typeof caCloseSaveOverlay === 'function' ? caCloseSaveOverlay : null)) return;

    if (tryClose('cfSaveOverlay',          typeof cfCloseSaveOverlay          === 'function' ? cfCloseSaveOverlay          : null)) return;
    if (tryClose('cfExportPwOverlay',      typeof cfCloseExportPwOverlay      === 'function' ? cfCloseExportPwOverlay      : null)) return;
    if (tryClose('cfImportConfirmOverlay', typeof cfCloseImportConfirm        === 'function' ? cfCloseImportConfirm        : null)) return;
    if (tryClose('cfImportOverlay',        typeof cfCloseImportBrowser        === 'function' ? cfCloseImportBrowser        : null)) return;

    if (tryClose('peExportOverlay', typeof __peCloseExport === 'function' ? __peCloseExport : null)) return;
    if (tryClose('peImportOverlay', typeof __peCloseImport === 'function' ? __peCloseImport : null)) return;

    if (tryClose('gaDashSaveOverlay', typeof closeGaDashSaveOverlay === 'function' ? closeGaDashSaveOverlay : null)) return;

    var visible = document.querySelector('.tl-overlay');
    if (visible && visible.style.display !== 'none' && visible.style.display !== '') {
        visible.style.display = 'none';
        document.body.style.overflow = '';
    }
});

