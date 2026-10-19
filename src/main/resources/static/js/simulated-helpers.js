'use strict';

function esc(s) {
    if (s == null) return '';
    return String(s)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
        .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

function txt(val) {
    if (val == null) return '';
    if (typeof val === 'string') return val;
    if (typeof val === 'object' && val.content) {
        return val.content
            .flatMap(b => (b.content || []).map(c => c.text || ''))
            .join('\n');
    }
    return JSON.stringify(val, null, 2);
}

function shortSha() {
    return Math.random().toString(16).substring(2, 10);
}

function fmtDate(d) {
    return (d ? new Date(d) : new Date()).toLocaleDateString('de-DE', {
        year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
}
