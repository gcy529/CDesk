const navToggle = document.querySelector('.nav-toggle');
const nav = document.getElementById('primary-nav');
const navLabel = navToggle.querySelector('.sr-only');

function setNav(open) {
  nav.classList.toggle('is-open', open);
  navToggle.setAttribute('aria-expanded', String(open));
  navLabel.textContent = open ? '关闭导航' : '打开导航';
}

navToggle.addEventListener('click', () => setNav(!nav.classList.contains('is-open')));
nav.addEventListener('click', (event) => {
  if (event.target.closest('a')) setNav(false);
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') setNav(false);
});
document.addEventListener('click', (event) => {
  if (!event.target.closest('.site-header')) setNav(false);
});
// 发版构建完成前，校验值还是占位符，先别把 __SIZE__ 直接摆给访客
document.querySelectorAll('.verify-hash, .verify-val').forEach((el) => {
  if (el.textContent.indexOf('__') !== -1) el.textContent = '发版后自动填入';
});
const wideQuery = window.matchMedia('(min-width: 861px)');
const closeOnWide = (event) => {
  if (event.matches) setNav(false);
};
if (wideQuery.addEventListener) wideQuery.addEventListener('change', closeOnWide);
else if (wideQuery.addListener) wideQuery.addListener(closeOnWide);

async function copyText(value) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const field = document.createElement('textarea');
  field.value = value;
  field.setAttribute('readonly', '');
  field.style.position = 'fixed';
  field.style.opacity = '0';
  document.body.appendChild(field);
  field.select();
  const copied = document.execCommand('copy');
  field.remove();
  if (!copied) throw new Error('copy failed');
}

const copyStatus = document.getElementById('copy-status');
let copyTimer;

document.querySelectorAll('[data-copy]').forEach((button) => {
  button.addEventListener('click', async () => {
    const value = button.dataset.copy;
    try {
      await copyText(value);
      copyStatus.textContent = '群号已复制';
    } catch {
      copyStatus.textContent = '复制失败，请手动复制群号';
    }
    clearTimeout(copyTimer);
    copyTimer = setTimeout(() => { copyStatus.textContent = ''; }, 2400);
  });
});

document.querySelectorAll('[data-copy-target]').forEach((button) => {
  const original = button.textContent;
  let timer;
  button.addEventListener('click', async () => {
    const target = document.getElementById(button.dataset.copyTarget);
    if (!target) return;
    try {
      await copyText(target.textContent.trim());
      button.textContent = '已复制';
    } catch {
      button.textContent = '复制失败';
    }
    clearTimeout(timer);
    timer = setTimeout(() => { button.textContent = original; }, 1800);
  });
});

// 并发测各线路的首字节时间，最快的排到最前，失败/超时的沉到最后
(function () {
  const groups = document.querySelectorAll('[data-mirror-group]');
  if (!groups.length || !window.fetch) {
    document.querySelectorAll('[data-mirror-status]').forEach((status) => {
      status.textContent = '任选一条线路都能下载。';
    });
    return;
  }

  const TIMEOUT = 4000;
  const supportsAbort = 'AbortController' in window;

  function probe(link) {
    return new Promise((resolve) => {
      const start = performance.now();
      const controller = supportsAbort ? new AbortController() : null;
      let settled = false;
      const timer = setTimeout(() => {
        if (settled) return;
        settled = true;
        if (controller) controller.abort();
        resolve({ link, ms: null });
      }, TIMEOUT);

      fetch(link.href, {
        mode: 'no-cors',
        cache: 'no-store',
        signal: controller ? controller.signal : undefined,
      })
        .then(() => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve({ link, ms: performance.now() - start });
          if (supportsAbort) controller.abort();
        })
        .catch(() => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve({ link, ms: null });
        });
    });
  }

  groups.forEach((group) => {
    const links = Array.from(group.querySelectorAll('a[data-mirror]'));
    if (!links.length) return;
    const status = group.parentElement.querySelector('[data-mirror-status]');

    Promise.all(links.map(probe)).then((list) => {
      const ok = list.filter((item) => item.ms !== null).sort((a, b) => a.ms - b.ms);
      const bad = list.filter((item) => item.ms === null);

      ok.forEach((item) => group.appendChild(item.link));
      bad.forEach((item) => group.appendChild(item.link));

      if (!ok.length) {
        if (status) status.textContent = '测速没有结果，任选一条线路都能下载。';
        return;
      }

      const fastest = ok[0].link;
      const name = fastest.dataset.mirror || fastest.textContent.trim();
      fastest.classList.add('is-fastest');
      const badge = document.createElement('span');
      badge.className = 'mirror-badge';
      badge.textContent = '最快';
      fastest.appendChild(badge);

      if (status) {
        status.textContent =
          '测速完成：' + name + ' 当前最快（约 ' + (ok[0].ms / 1000).toFixed(1) +
          ' 秒响应），已排到最前；其余线路仍可手动选择。';
      }
    });
  });
})();
