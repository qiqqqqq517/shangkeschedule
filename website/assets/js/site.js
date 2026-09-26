/* ============================================================================
 * 上课 · ShangKeSchedule — 官网脚本
 * ----------------------------------------------------------------------------
 * 站点级配置集中在下方 SITE 常量：发新版本时只需改 version 一处，
 * 页面上所有带 data-* 标记的下载链接与版本号会自动同步。
 * ========================================================================== */

const SITE = {
  name: '上课',
  version: '3.70.0',
  versionCode: '282',
  /* 主分发渠道：夸克网盘分享。改分享链接只需改这里 */
  quark: 'https://pan.quark.cn/s/02947cbc1d4e',
  quarkCode: '~9a263aTFyF~:/',
  /* 备用渠道与仓库 */
  repo: 'https://github.com/qiqqqqq517/shangkeschedule',
  email: 'hhixingchen520@163.com'
};

(function () {
  'use strict';

  /* --------------------------------------------------- 下载链接 / 版本号 */
  const apkName = (abi) => 'shangke-v' + SITE.version + '-' + abi + '-release.apk';

  /* data-dl 默认指向夸克网盘；data-dl="release" 显式指定走 GitHub Releases 备用通道 */
  document.querySelectorAll('[data-dl]').forEach((el) => {
    el.href = el.getAttribute('data-dl') === 'release'
      ? SITE.repo + '/releases/latest'
      : SITE.quark;
    el.removeAttribute('download');
    el.setAttribute('rel', 'noopener');
  });

  /* 文件清单里的 APK 文件名随版本号自动生成 */
  document.querySelectorAll('[data-asset-name]').forEach((el) => {
    el.textContent = apkName(el.getAttribute('data-asset-name'));
  });

  /* 夸克口令文本 + 复制按钮 */
  document.querySelectorAll('[data-quark-code]').forEach((el) => {
    el.textContent = SITE.quarkCode;
  });

  document.querySelectorAll('[data-copy]').forEach((btn) => {
    const label = btn.textContent;
    btn.addEventListener('click', () => {
      const text = SITE.quarkCode;
      const done = (ok) => {
        btn.textContent = ok ? '已复制 ✓' : '请手动复制';
        setTimeout(() => { btn.textContent = label; }, 2200);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => done(true), () => done(false));
        return;
      }
      try {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.setAttribute('readonly', '');
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        done(document.execCommand('copy'));
        document.body.removeChild(ta);
      } catch (e) { done(false); }
    });
  });

  document.querySelectorAll('[data-repo]').forEach((el) => {
    el.href = SITE.repo;
    el.setAttribute('rel', 'noopener');
  });

  document.querySelectorAll('[data-email]').forEach((el) => {
    el.href = 'mailto:' + SITE.email;
  });

  document.querySelectorAll('[data-version]').forEach((el) => {
    el.textContent = 'v' + SITE.version;
  });

  document.querySelectorAll('[data-version-code]').forEach((el) => {
    el.textContent = SITE.versionCode;
  });

  document.querySelectorAll('[data-year]').forEach((el) => {
    el.textContent = String(new Date().getFullYear());
  });

  /* ------------------------------------------------------------- 导航 */
  const nav = document.querySelector('.nav');
  const onScroll = () => {
    if (nav) nav.classList.toggle('is-stuck', window.scrollY > 12);
    const top = document.querySelector('.to-top');
    if (top) top.classList.toggle('is-on', window.scrollY > 700);
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  const burger = document.querySelector('.nav-burger');
  const links = document.querySelector('.nav-links');
  if (burger && links) {
    burger.addEventListener('click', () => {
      const open = links.classList.toggle('is-open');
      burger.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    links.addEventListener('click', (e) => {
      if (e.target.closest('a')) {
        links.classList.remove('is-open');
        burger.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* -------------------------------------------------------------- 主题 */
  const toggle = document.querySelector('.theme-toggle');
  if (toggle) {
    toggle.addEventListener('click', () => {
      const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      try { localStorage.setItem('sk-theme', next); } catch (e) { /* 隐私模式忽略 */ }
    });
  }

  /* ------------------------------------------------------------ 回到顶部 */
  const toTop = document.querySelector('.to-top');
  if (toTop) {
    toTop.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  /* ------------------------------------------------------- 滚动入场动画 */
  const reveals = document.querySelectorAll('.reveal');
  if (reveals.length) {
    if (!('IntersectionObserver' in window)) {
      reveals.forEach((el) => el.classList.add('is-in'));
    } else {
      const io = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              entry.target.classList.add('is-in');
              io.unobserve(entry.target);
            }
          });
        },
        { rootMargin: '0px 0px -8% 0px', threshold: 0.08 }
      );
      reveals.forEach((el, i) => {
        if (!el.style.getPropertyValue('--d')) {
          el.style.setProperty('--d', (i % 4) * 70 + 'ms');
        }
        io.observe(el);
      });
    }
  }

  /* ------------------------------------------------------------ 主题演示 */
  const tabs = document.querySelectorAll('.theme-tabs button');
  const stage = document.querySelector('.theme-stage');
  if (tabs.length && stage) {
    tabs.forEach((btn) => {
      btn.addEventListener('click', () => {
        const key = btn.getAttribute('data-stage');
        tabs.forEach((b) => b.setAttribute('aria-selected', String(b === btn)));
        stage.setAttribute('data-stage', key);
        stage.querySelectorAll('.theme-panel').forEach((p) => {
          p.classList.toggle('is-active', p.getAttribute('data-panel') === key);
        });
      });
    });
  }

  /* ------------------------------------------------- 当前页导航高亮 */
  const here = location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav-links a').forEach((a) => {
    const target = a.getAttribute('href');
    if (!target) return;
    const file = target.split('#')[0];
    if (file && file === here) a.setAttribute('aria-current', 'page');
  });
})();
