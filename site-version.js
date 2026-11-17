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
  var MIRRORS = [
    'https://ghfast.top/',
    'https://gh-proxy.com/',
    'https://cdn.jsdelivr.net/gh/gcy529/CDesk@main/version.json'
  ];

  function fill(ver, url, sha) {
    if (!ver) return;
    var all = document.querySelectorAll('[data-ver]');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      // 只替换文本里的版本号，别把「其他线路 / 版本详情」这类字一起吃掉
      if (/^v?\d+\.\d+\.\d+$/.test(el.textContent.trim())) {
        el.textContent = 'v' + ver;
      } else {
        el.textContent = el.textContent.replace(/v?\d+\.\d+\.\d+/g, 'v' + ver);
      }
      if (el.hasAttribute('data-dl') && url) {
        el.setAttribute('href', 'https://ghfast.top/' + url);
      }
      if (el.hasAttribute('data-tag')) {
        el.setAttribute('href', 'https://github.com/gcy529/CDesk/releases/tag/v' + ver);
      }
    }
    var shaEl = document.querySelector('[data-sha]');
    if (shaEl && sha) shaEl.textContent = sha;

    var h2 = document.querySelector('#download h2[data-ver]');
    if (h2 && h2.textContent.indexOf('Current version') === -1) {
      // 中文版式里「· 稳定版」要保留
      if (h2.textContent.indexOf('·') === -1) h2.textContent = '当前版本 v' + ver;
    }
  }

  function parse(text) {
    try {
      var o = JSON.parse(text);
      if (!o || !o.version) return null;
      return { ver: o.version, url: o.url, sha: o.sha256 || '' };
    } catch (e) { return null; }
  }

  function tryOne(src) {
    return fetch(src, { cache: 'no-store' })
      .then(function (r) { return r.ok ? r.text() : Promise.reject(0); })
      .then(function (t) { return parse(t); });
  }

  function run() {
    if (!window.fetch) return;
    var chain = Promise.reject(0);
    var urls = [MIRRORS[0] + RAW, MIRRORS[1] + RAW, MIRRORS[2]];
    urls.forEach(function (u) {
      chain = chain.catch(function () { return tryOne(u); });
    });
    chain.then(function (d) {
      if (d) {
        fill(d.ver, d.url, d.sha);
        document.documentElement.setAttribute('data-app-version', d.ver);
      }
    }).catch(function () { /* 全挂就保留 HTML 里的占位版本，不影响阅读 */ });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
  else run();
})();