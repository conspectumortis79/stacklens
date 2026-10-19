window.MrLinkBuilder = (function () {
  function isGithub(diffUrl) {
    return typeof diffUrl === 'string' && diffUrl.indexOf('github.com') !== -1;
  }

  function buildFileLineLink(opts) {
    var diffsUrl = opts && opts.diffsUrl ? String(opts.diffsUrl).replace(/\/+$/, '') : '';
    var filePath = opts && opts.filePath ? String(opts.filePath).trim() : '';
    var lineNumber = opts && Number.isInteger(opts.lineNumber) ? opts.lineNumber : parseInt(opts && opts.lineNumber, 10) || 0;
    if (!diffsUrl) {
      return { url: null, isGithub: false, filePath: filePath, lineNumber: lineNumber };
    }
    var base = diffsUrl;
    if (lineNumber > 0) {
      return {
        url: base + '#L' + lineNumber,
        isGithub: isGithub(diffsUrl),
        filePath: filePath,
        lineNumber: lineNumber,
      };
    }
    return {
      url: base,
      isGithub: isGithub(diffsUrl),
      filePath: filePath,
      lineNumber: 0,
    };
  }

  function linkifyResultContainer(container, diffsUrl) {
    if (!container || !diffsUrl) return 0;
    var normalised = String(diffsUrl).replace(/\/+$/, '');
    var isGh = isGithub(normalised);
    var textNodes = [];
    var walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
      acceptNode: function (node) {
        if (!node.nodeValue) return NodeFilter.FILTER_REJECT;
        if (node.parentNode && (node.parentNode.tagName === 'A' || node.parentNode.tagName === 'CODE')) {
          return NodeFilter.FILTER_REJECT;
        }
        if (/File:|Datei:|Line:|Zeile:/.test(node.nodeValue)) {
          return NodeFilter.FILTER_ACCEPT;
        }
        return NodeFilter.FILTER_REJECT;
      },
    });
    var n;
    while ((n = walker.nextNode())) textNodes.push(n);

    var linked = 0;

    var currentFile = null;
    var lastEl = null;
    for (var ti = 0; ti < textNodes.length; ti++) {
      var tn = textNodes[ti];
      var text = tn.nodeValue;
      if (lastEl !== tn.parentNode) {
        if (crossesBoundary(lastEl, tn.parentNode)) {
          currentFile = null;
        }
        lastEl = tn.parentNode;
      }
      var fragments = splitOnFileOrLine(text);
      if (fragments.length === 0) continue;
      var frag = document.createDocumentFragment();
      var localLinked = 0;
      for (var i = 0; i < fragments.length; i++) {
        var f = fragments[i];
        if (f.type === 'text') {
          frag.appendChild(document.createTextNode(f.value));
        } else if (f.type === 'file') {
          currentFile = f.value;
          var fileLink = document.createElement('a');
          fileLink.className = 'mr-file-link';
          fileLink.textContent = f.value;
          var fileUrl;
          if (typeof window.mrBuildBlobUrl === 'function') {
            fileUrl = window.mrBuildBlobUrl(normalised, f.value, 0, isGh);
          } else if (isGh) {
            fileUrl = normalised;
          } else {
            fileUrl = normalised + '#file-' + gitlabFileAnchor(f.value);
          }
          fileLink.setAttribute('href', fileUrl);
          fileLink.setAttribute('title', 'Open ' + f.value + ' in MR diff');
          fileLink.addEventListener('click', (function (fileUrlValue) {
            return function (e) {
              e.preventDefault();
              if (typeof window.mrOpenUrl === 'function') window.mrOpenUrl(fileUrlValue);
            };
          })(fileUrl));
          frag.appendChild(fileLink);
          frag.appendChild(document.createTextNode(' '));
          localLinked += 1;
        } else if (f.type === 'line') {
          var fileForLine = currentFile;
          if (!fileForLine) {
            var preceding = findPrecedingFile(tn);
            if (preceding) {
              fileForLine = preceding.path;
              currentFile = preceding.path;
            }
          }
          if (fileForLine) {
            if (f.label) {
              frag.appendChild(document.createTextNode(f.label));
            }
            var lineLink = document.createElement('a');
            lineLink.className = 'mr-file-link';
            lineLink.textContent = String(f.value);
            var url;
            if (typeof window.mrBuildBlobUrl === 'function') {
              url = window.mrBuildBlobUrl(normalised, fileForLine, f.value, isGh);
            } else if (isGh) {
              url = normalised + '#L' + f.value;
            } else {
              url = normalised + '#L' + gitlabFileAnchor(fileForLine) + '_' + f.value;
            }
            lineLink.setAttribute('href', url);
            lineLink.setAttribute('title', 'Open line ' + f.value + ' in MR diff');
            lineLink.addEventListener('click', (function (lineUrl) {
              return function (e) {
                e.preventDefault();
                if (typeof window.mrOpenUrl === 'function') window.mrOpenUrl(lineUrl);
              };
            })(url));
            frag.appendChild(lineLink);
            localLinked += 1;
          } else {
            frag.appendChild(document.createTextNode(f.display || String(f.value)));
          }
        }
      }
      if (localLinked > 0) {
        tn.parentNode.replaceChild(frag, tn);
        linked += localLinked;
      }
    }
    return linked;
  }

  function crossesBoundary(prevEl, nextEl) {
    if (!prevEl) return false;
    if (prevEl === nextEl) return false;
    var el = prevEl;
    var guard = 0;
    while (el && guard++ < 50) {
      var sib = el.nextElementSibling;
      while (sib) {
        if (isStructuralBreak(sib)) return true;
        if (sib === nextEl || sib.contains(nextEl)) return false;
        sib = sib.nextElementSibling;
      }
      el = el.parentNode;
      if (el === nextEl) return false;
    }
    return false;
  }

  function isStructuralBreak(el) {
    if (!el) return false;
    if (el.classList && el.classList.contains('mr-ri')) return true;
    var tag = el.tagName;
    return tag === 'H1' || tag === 'H2' || tag === 'H3' || tag === 'H4' ||
           tag === 'H5' || tag === 'H6' || tag === 'UL' || tag === 'OL' || tag === 'HR';
  }

  function findPrecedingFile(textNode) {
    var maxLookback = 6;
    var el = textNode.parentNode;
    if (!el) return null;
    var steps = 0;
    while (el && steps < maxLookback) {
      el = el.previousElementSibling;
      steps += 1;
      if (!el) break;
      if (el.classList && el.classList.contains('mr-ri')) break;
      var text = (el.textContent || '').trim();
      if (!text) continue;
      var tag = el.tagName;
      if (tag === 'H1' || tag === 'H2' || tag === 'H3' || tag === 'H4' ||
          tag === 'H5' || tag === 'H6' || tag === 'UL' || tag === 'OL' || tag === 'HR') {
        break;
      }
      var m = text.match(/(?:File|Datei):\s*`?([^`\n<]+?)`?\s*$/i);
      if (m && m[1]) {
        var candidate = m[1].trim();
        if (/^(File|Datei|Line|Zeile|Zeilen|Lines|Original|Originale|Originalblock|Suggestion|Vorschlag|Empfehlung|Ersatz)$/i.test(candidate)) {
          continue;
        }
        return { path: candidate, node: null };
      }
    }
    return null;
  }

  function splitOnFileOrLine(text) {
    if (!text) return [];
    var out = [];
    var re = /(File|Datei):\s*([^\s<][^\s<]*?)(?=\s+(?:Line|Zeile)|\s*$|<)|(Line|Zeile):\s*(\d+)/gi;
    var last = 0;
    var m;
    while ((m = re.exec(text)) !== null) {
      if (m.index > last) {
        out.push({ type: 'text', value: text.slice(last, m.index) });
      }
      if (m[1]) {
        out.push({ type: 'file', value: m[2].replace(/`/g, '').trim() });
      } else if (m[3]) {
        out.push({
          type: 'line',
          value: parseInt(m[4], 10),
          label: m[3] + ': ',
          display: m[3] + ': ' + m[4],
        });
      }
      last = re.lastIndex;
    }
    if (last < text.length) {
      out.push({ type: 'text', value: text.slice(last) });
    }
    return out;
  }

  function gitlabFileAnchor(filePath) {
    if (!filePath) return '';
    var p = String(filePath).replace(/`/g, '').trim();
    p = p.replace(/^\.\//, '');
    return p.replace(/[/.]/g, '-');
  }

  return {
    buildFileLineLink: buildFileLineLink,
    linkifyResultContainer: linkifyResultContainer,
    isGithub: isGithub,
  };
})();
