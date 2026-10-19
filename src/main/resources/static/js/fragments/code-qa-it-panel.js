window.MrItPanel = (function () {
  function q(id) { return document.getElementById(id); }
  function mrT(key, fallback) { return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fallback) : fallback; }

  var MR_IT_STEPS = [
    function () { return mrT('mr_analysis.it_step1', 'Fetching clone URL'); },
    function () { return mrT('mr_analysis.it_step2', 'Cloning MR branch'); },
    function () { return mrT('mr_analysis.it_step3', 'Searching for docker-compose.yml'); },
    function () { return mrT('mr_analysis.it_step4', 'AI analysing affected services'); },
    function () { return mrT('mr_analysis.it_step5', 'Starting Docker services'); },
    function () { return mrT('mr_analysis.it_step6', 'Running tests'); },
    function () { return mrT('mr_analysis.it_step7', 'AI summarising results'); },
  ];

  function mrItResetStatusPanel() {
    var panel = q('mrItStatusPanel');
    if (panel) panel.hidden = false;
    var stepper = q('mrItStepper');
    if (stepper && stepper.children.length !== MR_IT_STEPS.length) {
      stepper.innerHTML = MR_IT_STEPS.map(function (nameFn, idx) { var name = nameFn();
        return '<li class="mr-it-step" data-step="' + (idx + 1) + '">' +
          '<span class="mr-it-step-dot">' + (idx + 1) + '</span>' +
          '<span class="mr-it-step-name">' + escHtml(name) + '</span>' +
          '<span class="mr-it-step-detail"></span>' +
          '</li>';
      }).join('');
    } else if (stepper) {
      Array.prototype.forEach.call(stepper.children, function (li) {
        li.className = 'mr-it-step';
        var d = li.querySelector('.mr-it-step-detail');
        if (d) d.textContent = '';
        var dot = li.querySelector('.mr-it-step-dot');
        if (dot) dot.textContent = li.dataset.step;
      });
    }
    var sb = q('mrItServicesBlock'); if (sb) sb.hidden = true;
    var tb = q('mrItTestsBlock');    if (tb) tb.hidden = true;
    var wb = q('mrItWaitBar');       if (wb) wb.hidden = true;
    var sl = q('mrItServices');      if (sl) sl.innerHTML = '';
    var tl = q('mrItTests');         if (tl) tl.innerHTML = '';
    var sc = q('mrItServicesCount'); if (sc) sc.textContent = '';
    var tc = q('mrItTestsCount');    if (tc) tc.textContent = '';
    _mrItStartedAt = Date.now();
    mrItTickElapsed();
    if (!_mrItElapsedTimer) {
      _mrItElapsedTimer = setInterval(mrItTickElapsed, 1000);
    }
  }

  var _mrItElapsedTimer = null;
  var _mrItStartedAt = 0;
  var _mrItWaitTimeout = null;

  function mrItTickElapsed() {
    var el = q('mrItElapsed');
    if (!el || !_mrItStartedAt) return;
    var s = Math.floor((Date.now() - _mrItStartedAt) / 1000);
    var mm = String(Math.floor(s / 60)).padStart(2, '0');
    var ss = String(s % 60).padStart(2, '0');
    el.textContent = mm + ':' + ss;
  }

  function mrItStopElapsed() {
    if (_mrItElapsedTimer) { clearInterval(_mrItElapsedTimer); _mrItElapsedTimer = null; }
  }

  function mrItStatusFromStream(chunk) {
    if (!chunk) return;
    var re = /\[(?:STATUS|STEP)\]([\s\S]*?)\[\/(?:STATUS|STEP)\]/g;
    var m;
    while ((m = re.exec(chunk)) !== null) {
      var payload = null;
      try { payload = JSON.parse(m[1]); } catch (_) { continue; }
      if (!payload || typeof payload !== 'object') continue;
      mrItApplyStatus(payload);
    }
  }

  function mrItApplyStatus(p) {
    if (p.type === 'step') {
      mrItSetStep(p.index, p.state, p.detail);
    } else if (p.type === 'services') {
      mrItRenderServices(p.services || [], p.testCommands || [], p.waitSeconds);
    } else if (p.type === 'service') {
      mrItUpdateServiceState(p.name, p.state);
    } else if (p.type === 'test') {
      mrItUpdateTestState(p.index, p.state, p.command, p.exitCode);
    } else if (p.type === 'tests') {
      if (p.state === 'running') {
        mrItRenderTestStates(p.commands || []);
      } else if (p.state === 'done') {
        mrItRenderTestResults(p.results || [], p.passed || 0, p.total || 0);
      }
    } else if (p.type === 'waiting') {
      mrItShowWaitBar(p.seconds || 20, p.services || []);
    } else if (p.type === 'error') {
      mrItMarkFailed();
    }
  }

  function mrItUpdateTestState(index, state, command, exitCode) {
    if (typeof index !== 'number' || index < 0) return;
    var tl = q('mrItTests');
    if (!tl) return;
    var li = tl.querySelector('[data-test-idx="' + index + '"]');
    if (!li) return;
    li.className = 'mr-it-item ' + state;
    var meta = li.querySelector('.mr-it-item-meta');
    if (meta) {
      if (state === 'running') meta.textContent = 'running\u2026';
      else if (state === 'passed') meta.textContent = '\u2713 pass';
      else if (state === 'failed') meta.textContent = '\u2717 fail';
      else meta.textContent = state;
    }
    if (state === 'failed' && typeof exitCode === 'number') {
      li.title = (command || li.title || '') + ' (exit ' + exitCode + ')';
    }
  }

  function mrItUpdateServiceState(name, state) {
    if (!name) return;
    var list = q('mrItServices');
    if (!list) return;
    var items = list.querySelectorAll('.mr-it-item');
    for (var i = 0; i < items.length; i++) {
      if (items[i].getAttribute('data-service') === name) {
        items[i].className = 'mr-it-item ' + state;
        var meta = items[i].querySelector('.mr-it-item-meta');
        if (meta) {
          if (state === 'ready') meta.textContent = 'ready';
          else if (state === 'healthy') meta.textContent = 'healthy';
          else if (state === 'errors') meta.textContent = 'errors';
          else meta.textContent = state;
        }
        break;
      }
    }
  }

  function mrItSetStep(index, state, detail) {
    if (!index || index < 1 || index > MR_IT_STEPS.length) return;
    var li = q('mrItStepper') && q('mrItStepper').querySelector('[data-step="' + index + '"]');
    if (!li) return;
    li.className = 'mr-it-step ' + (state || 'pending');
    var d = li.querySelector('.mr-it-step-detail');
    if (d && detail) d.textContent = detail;
    var dot = li.querySelector('.mr-it-step-dot');
    if (!dot) return;
    if (state === 'done')         dot.textContent = '\u2713';
    else if (state === 'failed')  dot.textContent = '\u2717';
    else if (state === 'skipped') dot.textContent = '\u2014';
    else if (state === 'waiting') dot.innerHTML = '<span class="mr-spinner" style="width:12px;height:12px;border-width:2px;"></span>';
    else                          dot.textContent = String(index);
  }

  function mrItRenderServices(services, commands, waitSeconds) {
    var block = q('mrItServicesBlock'); if (block) block.hidden = false;
    var list = q('mrItServices'); if (!list) return;
    list.innerHTML = services.map(function (s) {
      return '<li class="mr-it-item running" data-service="' + escHtml(s) + '">' +
        '<span class="mr-it-item-dot"></span>' +
        '<span class="mr-it-item-label">' + escHtml(s) + '</span>' +
        '<span class="mr-it-item-meta">starting\u2026</span>' +
        '</li>';
    }).join('');
    var count = q('mrItServicesCount');
    if (count) count.textContent = services.length + (services.length === 1 ? ' service' : ' services');

    if (commands && commands.length) {
      var tb = q('mrItTestsBlock'); if (tb) tb.hidden = false;
      var tl = q('mrItTests'); if (!tl) return;
      tl.innerHTML = commands.map(function (cmd, i) {
        return '<li class="mr-it-item pending" data-test-idx="' + i + '">' +
          '<span class="mr-it-item-dot"></span>' +
          '<span class="mr-it-item-label" title="' + escHtml(cmd) + '">' + escHtml(cmd) + '</span>' +
          '<span class="mr-it-item-meta">queued</span>' +
          '</li>';
      }).join('');
      var tc = q('mrItTestsCount');
      if (tc) tc.textContent = commands.length + ' cmd';
    }
  }

  function mrItRenderTestStates(commands) {
    var tb = q('mrItTestsBlock'); if (tb) tb.hidden = false;
    var tl = q('mrItTests'); if (!tl) return;
    tl.innerHTML = commands.map(function (c, i) {
      var cmd = typeof c === 'object' && c ? c.command : c;
      var st  = typeof c === 'object' && c ? (c.state || 'running') : 'running';
      return '<li class="mr-it-item ' + st + '" data-test-idx="' + i + '">' +
        '<span class="mr-it-item-dot"></span>' +
        '<span class="mr-it-item-label" title="' + escHtml(cmd) + '">' + escHtml(cmd) + '</span>' +
        '<span class="mr-it-item-meta">running\u2026</span>' +
        '</li>';
    }).join('');
  }

  function mrItRenderTestResults(results, passed, total) {
    var tl = q('mrItTests'); if (!tl) return;
    tl.innerHTML = results.map(function (r, i) {
      var cmd, ok;
      if (Array.isArray(r)) { cmd = r[0]; ok = !!r[1]; }
      else if (r && typeof r === 'object') { cmd = r.command; ok = !!r.passed; }
      else { cmd = String(r); ok = false; }
      return '<li class="mr-it-item ' + (ok ? 'passed' : 'failed') + '" data-test-idx="' + i + '">' +
        '<span class="mr-it-item-dot"></span>' +
        '<span class="mr-it-item-label" title="' + escHtml(cmd) + '">' + escHtml(cmd) + '</span>' +
        '<span class="mr-it-item-meta">' + (ok ? '\u2713 pass' : '\u2717 fail') + '</span>' +
        '</li>';
    }).join('');
    var tc = q('mrItTestsCount');
    if (tc) tc.textContent = passed + '/' + total + ' pass';
    var svcItems = document.querySelectorAll('#mrItServices .mr-it-item');
    Array.prototype.forEach.call(svcItems, function (li) {
      li.classList.remove('running');
      li.classList.add(passed === total ? 'passed' : 'failed');
      var m = li.querySelector('.mr-it-item-meta');
      if (m) m.textContent = (passed === total) ? 'healthy' : 'errors';
    });
  }

  function mrItShowWaitBar(seconds, services) {
    var wb = q('mrItWaitBar'); if (!wb) return;
    wb.hidden = false;
    var txt = q('mrItWaitText');
    if (txt) {
      var svcList = (services && services.length) ? services.join(', ') : 'docker services';
      txt.textContent = 'Waiting ' + seconds + 's for ' + svcList + ' to become healthy\u2026';
    }
    var fill = q('mrItProgressFill');
    if (fill) {
      fill.style.transition = 'none';
      fill.style.width = '0%';
      void fill.offsetWidth;
      fill.style.transition = 'width ' + seconds + 's linear';
      fill.style.width = '100%';
    }
    if (_mrItWaitTimeout) clearTimeout(_mrItWaitTimeout);
    _mrItWaitTimeout = setTimeout(function () {
      wb.hidden = true;
      var f = q('mrItProgressFill');
      if (f) f.style.width = '0%';
    }, seconds * 1000);
  }

  function mrItMarkFailed() {
    var stepper = q('mrItStepper');
    if (stepper) {
      Array.prototype.forEach.call(stepper.querySelectorAll('.mr-it-step.running'), function (li) {
        li.classList.remove('running');
        li.classList.add('failed');
        var dot = li.querySelector('.mr-it-step-dot');
        if (dot) dot.textContent = '\u2717';
      });
    }
    var items = document.querySelectorAll('#mrItServices .mr-it-item.running, #mrItTests .mr-it-item.running, #mrItTests .mr-it-item.pending');
    Array.prototype.forEach.call(items, function (li) {
      li.classList.remove('running', 'pending');
      li.classList.add('failed');
      var m = li.querySelector('.mr-it-item-meta');
      if (m) m.textContent = 'aborted';
    });
  }


  return {
    mrItResetStatusPanel: mrItResetStatusPanel,
    mrItTickElapsed: mrItTickElapsed,
    mrItStopElapsed: mrItStopElapsed,
    mrItStatusFromStream: mrItStatusFromStream,
    mrItApplyStatus: mrItApplyStatus,
    mrItUpdateTestState: mrItUpdateTestState,
    mrItUpdateServiceState: mrItUpdateServiceState,
    mrItSetStep: mrItSetStep,
    mrItRenderServices: mrItRenderServices,
    mrItRenderTestStates: mrItRenderTestStates,
    mrItRenderTestResults: mrItRenderTestResults,
    mrItShowWaitBar: mrItShowWaitBar,
    mrItMarkFailed: mrItMarkFailed,
  };
})();
