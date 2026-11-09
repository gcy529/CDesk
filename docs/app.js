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
window.matchMedia('(min-width: 861px)').addEventListener('change', (event) => {
  if (event.matches) setNav(false);
});

const copyStatus = document.getElementById('copy-status');
let copyTimer;

document.querySelectorAll('[data-copy]').forEach((button) => {
  button.addEventListener('click', async () => {
    const value = button.dataset.copy;
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(value);
      } else {
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
      copyStatus.textContent = '群号已复制';
    } catch {
      copyStatus.textContent = '复制失败，请手动复制群号';
    }
    clearTimeout(copyTimer);
    copyTimer = setTimeout(() => { copyStatus.textContent = ''; }, 2400);
  });
});