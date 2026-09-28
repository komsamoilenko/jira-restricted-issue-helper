// render/client.js -- the script every browser receives with a card.
// Full-line comments like this one are removed by build/assemble.py and must
// not survive into dist/ (build/strip_client_comments.py --check enforces
// it): anything left here reaches every browser that gets a card. Inline
// comments (code and comment on one line) are NOT removed: do not write them.
// Two interpolations are allowed, $dataJson and $textJson; no other dollar
// sign may appear anywhere in this file.
//
// Runs once per page (window flag). d = payload, T = TEXT. fmt() fills
// {placeholders} in a single pass, so server values are never re-read as
// templates. The DOM is the gate: it draws only into .issue-error (/browse/)
// or #unlicensed-project-type (agent view), hiding the stock children; on a
// page that renders normally neither node exists. The agent view is
// client-rendered, so it polls for about 10 s and watches DOM mutations for
// 15 s. Focus rings use box-shadow because Jira's global CSS removes
// outlines; the copy button falls back to execCommand, then to "copy it from
// the box above". The greeting uses a first name only when the first word
// needed no cleaning, has 3+ Latin letters (U+00C0 to U+024F included) and
// is not all caps.
(function () {
  if (window.__jsmBrowseErrorHelper) { return; }
  window.__jsmBrowseErrorHelper = true;

  var d = $dataJson;
  var T = $textJson;
  var COPY_LABEL = T.copyLabel;
  var AGENT = (d.page === 'agent');

  var CSS = [
    '.jbh-wrap{max-width:520px;margin:8px auto 0;padding:0 16px;box-sizing:border-box;text-align:center;',
    'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;}',
    '.jbh-card{position:relative;padding:28px 28px 24px;border-radius:8px;background:#FFFFFF;',
    'border:1px solid #DFE1E6;box-shadow:0 1px 1px rgba(9,30,66,.10),0 0 1px rgba(9,30,66,.13);',
    'opacity:0;transform:translateY(10px);animation:jbh-in .36s cubic-bezier(.2,0,0,1) .05s forwards;}',
    '@keyframes jbh-in{to{opacity:1;transform:none}}',
    '.jbh-badge{width:52px;height:52px;margin:0 auto 16px;border-radius:50%;display:flex;',
    'align-items:center;justify-content:center;color:#0052CC;background:#E9F2FF;',
    'box-shadow:0 0 0 7px rgba(233,242,255,.5);transform:scale(.88);',
    'animation:jbh-pop .42s cubic-bezier(.2,0,0,1) .14s forwards;}',
    '@keyframes jbh-pop{to{transform:scale(1)}}',
    '.jbh-badge svg{display:block}',
    '.jbh-title{margin:0 0 8px;font-size:16px;line-height:1.32;font-weight:600;color:#172B4D;letter-spacing:-.003em}',
    '.jbh-text{margin:0 0 20px;font-size:13.5px;line-height:1.55;color:#5E6C84}',
    '.jbh-who{display:inline-flex;align-items:center;gap:8px;margin:0 0 16px;padding:6px 14px 6px 6px;',
    'border-radius:20px;background:#F4F5F7;font-size:13px;color:#172B4D;max-width:100%;overflow-wrap:anywhere}',
    '.jbh-av{flex:0 0 24px;width:24px;height:24px;border-radius:50%;background:#0052CC;color:#FFFFFF;display:flex;',
    'align-items:center;justify-content:center;font-size:11px;font-weight:700;letter-spacing:.2px}',
    '.jbh-quote{position:relative;margin:0 0 16px;padding:14px 16px;border-radius:6px;',
    'background:#F7F8F9;border:1px solid #DFE1E6;text-align:left;font-size:12.5px;line-height:1.6;',
    'color:#42526E;word-break:break-word}',
    '.jbh-quote b{display:block;margin-bottom:4px;color:#172B4D;font-weight:600}',
    '.jbh-people{display:flex;flex-wrap:wrap;justify-content:center;gap:8px;margin:0 0 16px}',
    '.jbh-people .jbh-who{margin:0}',
    '.jbh-role{color:#5E6C84;font-size:12px}',
    '.jbh-actions{display:flex;flex-direction:column;align-items:center;gap:12px}',
    '.jbh-btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;height:36px;',
    'padding:0 18px;border-radius:4px;font-size:14px;font-weight:500;text-decoration:none;',
    'border:none;cursor:pointer;font-family:inherit;',
    'transition:background .15s ease,box-shadow .15s ease,transform .15s ease}',
    '.jbh-btn-primary{background:#0052CC;color:#FFFFFF !important;box-shadow:0 1px 2px rgba(9,30,66,.2)}',
    '.jbh-btn-primary:hover{background:#0065FF;transform:translateY(-1px);box-shadow:0 4px 8px rgba(9,30,66,.16)}',
    '.jbh-btn-primary:active{background:#0747A6;transform:none;box-shadow:none}',
    '.jbh-btn-done{background:#216E4E !important;box-shadow:none !important;transform:none !important}',
    '.jbh-btn-warn{background:#974F0C !important}',
    '.jbh-btn svg{opacity:.9}',
    '.jbh-link{font-size:13px;color:#42526E;text-decoration:none;border-bottom:1px solid transparent;',
    'transition:color .15s ease,border-color .15s ease}',
    '.jbh-link:hover{color:#0052CC;border-bottom-color:#0052CC}',
    '.jbh-btn:focus-visible,.jbh-link:focus-visible{box-shadow:0 0 0 2px #FFFFFF,0 0 0 4px #0052CC}',
    '@media (prefers-reduced-motion:reduce){.jbh-card,.jbh-badge{animation:none;opacity:1;transform:none}}'
  ].join('');

  var ICON_PORTAL = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><path d='M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6'></path><polyline points='15 3 21 3 21 9'></polyline><line x1='10' y1='14' x2='21' y2='3'></line></svg>";
  var ICON_HELP   = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><circle cx='12' cy='12' r='10'></circle><path d='M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3'></path><line x1='12' y1='17' x2='12.02' y2='17'></line></svg>";
  var ICON_SHARE  = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><path d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'></path><circle cx='9' cy='7' r='4'></circle><line x1='19' y1='8' x2='19' y2='14'></line><line x1='22' y1='11' x2='16' y2='11'></line></svg>";
  var ICON_ARROW  = "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><line x1='5' y1='12' x2='19' y2='12'></line><polyline points='12 5 19 12 12 19'></polyline></svg>";
  var ICON_LOCK   = "<svg width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><rect x='3' y='11' width='18' height='11' rx='2' ry='2'></rect><path d='M7 11V7a5 5 0 0 1 10 0v4'></path></svg>";
  var ICON_COPY   = "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true' focusable='false'><rect x='9' y='9' width='13' height='13' rx='2'></rect><path d='M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'></path></svg>";

  function fmt(template, vars) {
    return String(template).replace(/\{([A-Za-z]+)\}/g, function (all, name) {
      return (vars && Object.prototype.hasOwnProperty.call(vars, name)) ? String(vars[name]) : all;
    });
  }

  function style() {
    if (document.getElementById('jbh-style')) { return; }
    var s = document.createElement('style');
    s.id = 'jbh-style';
    s.appendChild(document.createTextNode(CSS));
    (document.head || document.documentElement).appendChild(s);
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) { n.className = cls; }
    if (text) { n.appendChild(document.createTextNode(text)); }
    return n;
  }

  function withIcon(node, svg) {
    var s = document.createElement('span');
    s.innerHTML = svg;
    node.appendChild(s);
    return node;
  }

  function tokens(name) {
    var out = [];
    var parts = (name || '').split(' ');
    for (var i = 0; i < parts.length; i++) {
      var t = parts[i].replace(/[^A-Za-z\u00C0-\u024F'-]/g, '');
      if (t) { out.push(t); }
    }
    return out;
  }

  function greetingFor(name) {
    var t = tokens(name);
    var first = t.length ? t[0] : '';
    var rawFirst = (name || '').split(' ')[0];
    var usable = first.length > 2 && first === rawFirst && first !== first.toUpperCase();
    return usable ? fmt(T.greetingNamed, { first: first }) : T.greetingPlain;
  }

  function greeting() { return greetingFor(d.reporterName); }

  function initialsOf(name) {
    var t = tokens(name);
    if (!t.length) { return '?'; }
    var a = t[0].charAt(0);
    var b = t.length > 1 ? t[t.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  function initials() { return initialsOf(d.reporterName); }

  function origin() {
    return window.location.protocol + '//' + window.location.host;
  }

  function shareMessage() {
    return greeting() + ' ' + fmt(T.shareMessage,
      { key: d.issueKey, url: origin() + d.portalUrl, mail: d.myMail });
  }

  function levelPhrase() {
    return d.levelName ? fmt(T.levelNamed, { level: d.levelName }) : T.levelUnnamed;
  }

  function hasField() {
    return !!(d.fieldName || d.hasField);
  }

  function securedLead() {
    var n = (d.people || []).length;
    var s = fmt(T.securedLead, { key: d.issueKey, levelPhrase: levelPhrase() });
    if (hasField()) {
      s += ' ' + (d.fieldName ? fmt(T.securedLeadField, { field: d.fieldName }) : T.securedLeadFieldUnnamed);
      if (n > 1) {
        s += ' ' + T.securedLeadMany;
      } else if (n === 1) {
        s += ' ' + T.securedLeadOne;
      } else {
        s += ' ' + T.securedLeadNobody;
      }
      if (AGENT) {
        s += ' ' + T.securedLeadAgent;
      }
    } else {
      s += ' ' + T.securedLeadNoField;
    }
    return s;
  }

  function securedMessage() {
    var ppl = d.people || [];
    var body = d.fieldName
      ? fmt(T.securedMessage, { url: origin() + d.issueUrl, mail: d.myMail, field: d.fieldName })
      : fmt(T.securedMessageFieldUnnamed, { url: origin() + d.issueUrl, mail: d.myMail });
    return (ppl.length === 1 ? greetingFor(ppl[0].name) : T.greetingPlain) + ' ' + body;
  }

  function copyText(text, btn) {
    if (btn.getAttribute('data-jbh-busy') === '1') { return; }

    function settle(label, cls) {
      btn.setAttribute('data-jbh-busy', '1');
      btn.textContent = '';
      btn.appendChild(document.createTextNode(label));
      btn.className = 'jbh-btn jbh-btn-primary ' + cls;
      setTimeout(function () {
        btn.textContent = '';
        btn.appendChild(document.createTextNode(COPY_LABEL));
        withIcon(btn, ICON_COPY);
        btn.className = 'jbh-btn jbh-btn-primary';
        btn.removeAttribute('data-jbh-busy');
      }, 2200);
    }
    function ok() { settle(T.copied, 'jbh-btn-done'); }
    function fail() { settle(T.copyFailed, 'jbh-btn-warn'); }

    function legacy() {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', 'readonly');
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      var done = false;
      try { done = document.execCommand('copy'); } catch (e) { done = false; }
      document.body.removeChild(ta);
      if (done) { ok(); } else { fail(); }
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(ok, legacy);
    } else {
      legacy();
    }
  }

  function card() {
    var wrap = el('div', 'jbh-wrap');
    wrap.id = 'jsm-browse-error-helper';
    var box = el('div', 'jbh-card');
    var badge = el('div', 'jbh-badge');
    var actions = el('div', 'jbh-actions');

    if (d.mode === 'portal') {
      badge.innerHTML = ICON_PORTAL;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.portalTitle));
      box.appendChild(el('p', 'jbh-text', AGENT ? T.portalTextAgent : T.portalText));

      var go = el('a', 'jbh-btn jbh-btn-primary');
      go.href = d.portalUrl;
      go.appendChild(document.createTextNode(T.portalOpen));
      withIcon(go, ICON_ARROW);
      actions.appendChild(go);

      var all = el('a', 'jbh-link', T.portalMyRequests);
      all.href = d.myRequests;
      actions.appendChild(all);

    } else if (d.mode === 'moved') {
      badge.innerHTML = ICON_PORTAL;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.movedTitle));

      var p1 = el('p', 'jbh-text');
      p1.appendChild(document.createTextNode(
        (d.oldKey ? fmt(T.movedFrom, { oldKey: d.oldKey, project: d.projectName })
                  : fmt(T.movedFromUnknown, { project: d.projectName })) + ' '));
      var keyLink = el('a', 'jbh-link');
      keyLink.href = d.issueUrl;
      keyLink.appendChild(document.createTextNode(d.issueKey));
      p1.appendChild(keyLink);
      p1.appendChild(document.createTextNode(T.movedAfterLink));
      box.appendChild(p1);

      box.appendChild(el('p', 'jbh-text',
        fmt(T.movedNoAccess, { project: d.projectName, key: d.issueKey })));

      var ask = el('a', 'jbh-btn jbh-btn-primary');
      ask.href = d.fallbackUrl;
      ask.appendChild(document.createTextNode(T.movedAsk));
      withIcon(ask, ICON_ARROW);
      actions.appendChild(ask);

      var open = el('a', 'jbh-link');
      open.href = d.issueUrl;
      open.appendChild(document.createTextNode(fmt(T.openKey, { key: d.issueKey })));
      actions.appendChild(open);

    } else if (d.mode === 'share') {
      badge.innerHTML = ICON_SHARE;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.shareTitle));
      box.appendChild(el('p', 'jbh-text',
        fmt(AGENT ? T.shareTextAgent : T.shareText, { key: d.issueKey })));

      var who = el('div', 'jbh-who');
      who.appendChild(el('div', 'jbh-av', initials()));
      who.appendChild(document.createTextNode(d.reporterName));
      box.appendChild(who);

      var quote = el('div', 'jbh-quote');
      quote.appendChild(el('b', null, T.messageHeadingThem));
      quote.appendChild(document.createTextNode(shareMessage()));
      box.appendChild(quote);

      var copy = el('button', 'jbh-btn jbh-btn-primary');
      copy.type = 'button';
      copy.setAttribute('aria-live', 'polite');
      copy.appendChild(document.createTextNode(COPY_LABEL));
      withIcon(copy, ICON_COPY);
      copy.addEventListener('click', function () { copyText(shareMessage(), copy); });
      actions.appendChild(copy);

      var esc = el('a', 'jbh-link', T.shareEscalate);
      esc.href = d.fallbackUrl;
      actions.appendChild(esc);

    } else if (d.mode === 'secured') {
      var ppl = d.people || [];
      badge.innerHTML = ICON_LOCK;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.securedTitle));
      box.appendChild(el('p', 'jbh-text', securedLead()));

      if (ppl.length) {
        var row = el('div', 'jbh-people');
        for (var k = 0; k < ppl.length; k++) {
          var chip = el('div', 'jbh-who');
          chip.appendChild(el('div', 'jbh-av', initialsOf(ppl[k].name)));
          chip.appendChild(document.createTextNode(ppl[k].name));
          chip.appendChild(el('span', 'jbh-role', ppl[k].role));
          row.appendChild(chip);
        }
        box.appendChild(row);
      }

      if (hasField()) {
        var sq = el('div', 'jbh-quote');
        sq.appendChild(el('b', null, ppl.length ? T.messageHeadingThem : T.messageHeading));
        sq.appendChild(document.createTextNode(securedMessage()));
        box.appendChild(sq);

        var sc = el('button', 'jbh-btn jbh-btn-primary');
        sc.type = 'button';
        sc.setAttribute('aria-live', 'polite');
        sc.appendChild(document.createTextNode(COPY_LABEL));
        withIcon(sc, ICON_COPY);
        sc.addEventListener('click', function () { copyText(securedMessage(), sc); });
        actions.appendChild(sc);

        var again = el('a', 'jbh-link', fmt(T.securedOpenAgain, { key: d.issueKey }));
        again.href = d.issueUrl;
        actions.appendChild(again);
      }

      var sEsc = el('a', 'jbh-link',
        hasField() ? T.securedEscalateField : T.securedEscalateNoField);
      sEsc.href = d.fallbackUrl;
      actions.appendChild(sEsc);

    } else if (d.mode === 'restricted') {
      badge.innerHTML = ICON_LOCK;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.restrictedTitle));
      box.appendChild(el('p', 'jbh-text',
        fmt(AGENT ? T.restrictedTextAgent : T.restrictedText, { key: d.issueKey })));

      var rAsk = el('a', 'jbh-btn jbh-btn-primary');
      rAsk.href = d.fallbackUrl;
      rAsk.appendChild(document.createTextNode(T.raiseRequest));
      withIcon(rAsk, ICON_ARROW);
      actions.appendChild(rAsk);

      var rOpen = el('a', 'jbh-link', fmt(T.restrictedOpenAgain, { key: d.issueKey }));
      rOpen.href = d.issueUrl;
      actions.appendChild(rOpen);

    } else if (d.mode === 'missing') {
      badge.innerHTML = ICON_HELP;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', T.missingTitle));
      box.appendChild(el('p', 'jbh-text', fmt(T.missingText, { key: d.issueKey })));

      var mHc = el('a', 'jbh-btn jbh-btn-primary');
      mHc.href = d.helpCenter;
      mHc.appendChild(document.createTextNode(T.openHelpCenter));
      withIcon(mHc, ICON_ARROW);
      actions.appendChild(mHc);

      var mRaise = el('a', 'jbh-link', T.genericEscalate);
      mRaise.href = d.fallbackUrl;
      actions.appendChild(mRaise);

    } else {
      badge.innerHTML = ICON_HELP;
      box.appendChild(badge);
      box.appendChild(el('h1', 'jbh-title', AGENT ? T.genericTitleAgent : T.genericTitle));
      box.appendChild(el('p', 'jbh-text', AGENT ? T.genericTextAgent : T.genericText));

      if (AGENT) {
        var askUs = el('a', 'jbh-btn jbh-btn-primary');
        askUs.href = d.fallbackUrl;
        askUs.appendChild(document.createTextNode(T.raiseRequest));
        withIcon(askUs, ICON_ARROW);
        actions.appendChild(askUs);

        var hcLink = el('a', 'jbh-link', T.openHelpCenter);
        hcLink.href = d.helpCenter;
        actions.appendChild(hcLink);
      } else {
        var hc = el('a', 'jbh-btn jbh-btn-primary');
        hc.href = d.helpCenter;
        hc.appendChild(document.createTextNode(T.openHelpCenter));
        withIcon(hc, ICON_ARROW);
        actions.appendChild(hc);

        var raise = el('a', 'jbh-link', T.genericEscalate);
        raise.href = d.fallbackUrl;
        actions.appendChild(raise);
      }
    }

    box.appendChild(actions);
    wrap.appendChild(box);
    return wrap;
  }

  function inject() {
    var host = document.querySelector('.issue-error') ||
               document.getElementById('unlicensed-project-type');
    if (!host) { return false; }
    if (document.getElementById('jsm-browse-error-helper')) { return true; }
    style();
    var kids = [].slice.call(host.children);
    for (var i = 0; i < kids.length; i++) { kids[i].style.display = 'none'; }
    host.appendChild(card());
    return true;
  }

  var obs = null;
  function stopObserver() {
    if (obs) { obs.disconnect(); obs = null; }
  }

  function start() {
    if (inject()) { return; }
    var tries = 0;
    var timer = setInterval(function () {
      tries = tries + 1;
      if (inject() || tries > 40) { clearInterval(timer); stopObserver(); }
    }, 250);
    if (window.MutationObserver) {
      obs = new MutationObserver(function () {
        if (inject()) { clearInterval(timer); stopObserver(); }
      });
      obs.observe(document.body || document.documentElement, { childList: true, subtree: true });
      setTimeout(stopObserver, 15000);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
