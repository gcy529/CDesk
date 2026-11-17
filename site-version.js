/* 站点版本号运行时注入。
 *
 * 官网不再写死版本号：页面里的 data-ver 元素在加载后从 main 分支的
 * version.json 取值填充。这样改官网 UI 不需要动 APK、不需要升版本，
 * 也不会再出现「网页版本号和应用版本号对不上」。
 *
 * version.json 必须留在 main：应用的更新检查与公告都读它。
 */
(function () {
  var RAW = 'https://raw.githubusercontent.com/gcy529/CDesk/main/version.json';
  var CANDIDATES = [
    'https://ghfast.top/' + RAW,
    'https://gh-proxy.com/' + RAW,
    'https://cdn.jsdelivr.net/gh/gcy529/CDesk@main/version.json'
  ];

  var DATA = null;    // 取到就缓存，切语言时不用重新请求
  var PENDING = null; // 进行中的请求，避免同时触发两次

  function fill(d) {
    if (!d || !d.ver) return;
    var ver = d.ver;

    var all = document.querySelectorAll('[data-ver]');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var text = el.textContent.trim();
      if (/^v?\d+\.\d+\.\d+$/.test(text)) {
        el.textContent = 'v' + ver;
      } else {
        el.textContent = el.textContent.replace(/v?\d+\.\d+\.\d+/g, 'v' + ver);
      }
      if (el.hasAttribute('data-dl') && d.url) {
        el.setAttribute('href', 'https://ghfast.top/' + d.url);
      }
      if (el.hasAttribute('data-tag')) {
        el.setAttribute('href', 'https://github.com/gcy529/CDesk/releases/tag/v' + ver);
      }
    }

    var shaEl = document.querySelector('.verify-val');
    if (shaEl) {
      // version.json 没写 sha256 时别把占位符露给用户
      var en = document.documentElement.lang === 'en';
      shaEl.textContent = d.sha || (en ? 'see release page' : '见 Release 页');
    }

    document.documentElement.setAttribute('data-app-version', ver);
  }

  function parse(text) {
    try {
      var o = JSON.parse(text);
      if (!o || !o.version) return null;
      return { ver: o.version, url: o.url, sha: o.sha256 || '' };
    } catch (e) { return null; }
  }

  function fetchOne(url) {
    return fetch(url, { cache: 'no-store' })
      .then(function (r) { return r.ok ? r.text() : Promise.reject(0); })
      .then(parse);
  }

  function run() {
    if (!window.fetch) return;
    if (DATA) { fill(DATA); return; }
    if (PENDING) {
      // 已有请求在飞，等它回来重填即可，不重复发请求
      PENDING.then(function (d) { if (d) fill(d); });
      return;
    }
    var chain = Promise.reject(0);
    for (var i = 0; i < CANDIDATES.length; i++) {
      (function (u) {
        chain = chain.catch(function () { return fetchOne(u); });
      })(CANDIDATES[i]);
    }
    PENDING = chain.then(function (d) {
      if (d) DATA = d;
      return d;
    }).catch(function () {
      PENDING = null;  // 全挂就允许下次重试
      return null;
    });
    PENDING.then(function (d) {
      if (d) fill(d); /* 都失败就保留 HTML 占位版本，不影响阅读 */
    });
  }

  // 切语言时 i18n 会整体重写 innerHTML，把注入的版本号冲掉，这里补一次
  document.addEventListener('cdesk:lang', run);

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
  else run();
})();