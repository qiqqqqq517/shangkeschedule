/* ============================================================================
 * 上课Shangke 设计包 · 页面共享行为
 * ----------------------------------------------------------------------------
 * 1) 深浅色主题切换 + localStorage 持久化 + 跟随系统
 * 2) 底部导航 / 侧栏导航的当前项高亮（按 body[data-page] 自动同步）
 * 3) 键盘可达性：方向键在 tab 组内移动焦点
 * 4) 视口高度修正（移动端浏览器地址栏抖动）
 * 设计稿原视觉值不变，本文件只补交互。
 * ========================================================================== */
(function () {
  'use strict';

  var STORAGE_KEY = 'shangke-claude-theme';
  var root = document.documentElement;

  /* ---------- 1. 主题 ---------- */
  function systemPrefersDark() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  function readStoredTheme() {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch (e) {
      return null;
    }
  }

  function storeTheme(value) {
    try {
      localStorage.setItem(STORAGE_KEY, value);
    } catch (e) {
      /* 隐私模式忽略 */
    }
  }

  function applyTheme(mode) {
    var resolved = mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode;
    root.setAttribute('data-theme', resolved);
    root.classList.toggle('dark', resolved === 'dark');
    root.style.colorScheme = resolved;
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      btn.setAttribute('aria-pressed', String(resolved === 'dark'));
      btn.setAttribute(
        'aria-label',
        resolved === 'dark' ? '切换到浅色模式' : '切换到深色模式'
      );
      var use = btn.querySelector('use');
      if (use) use.setAttribute('href', resolved === 'dark' ? '#i-eye' : '#i-eye');
    });
  }

  function currentMode() {
    var stored = readStoredTheme();
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
    return 'system';
  }

  function cycleTheme() {
    var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    storeTheme(next);
    applyTheme(next);
  }

  applyTheme(currentMode());

  if (window.matchMedia) {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    var onSystemChange = function () {
      if (currentMode() === 'system') applyTheme('system');
    };
    if (mq.addEventListener) mq.addEventListener('change', onSystemChange);
    else if (mq.addListener) mq.addListener(onSystemChange);
  }

  /* ---------- 2. 导航当前项 ---------- */
  function syncNav() {
    var page = document.body.getAttribute('data-page');
    if (!page) return;
    document.querySelectorAll('[data-nav-key]').forEach(function (el) {
      var active = el.getAttribute('data-nav-key') === page;
      el.classList.toggle('active', active);
      if (active) el.setAttribute('aria-current', 'page');
      else el.removeAttribute('aria-current');
    });
  }

  /* ---------- 3. tab 组方向键 ---------- */
  function bindRovingTabindex(container) {
    var items = Array.prototype.slice.call(
      container.querySelectorAll('[role="tab"], .tab, .ui-toggle')
    );
    if (!items.length) return;
    container.addEventListener('keydown', function (e) {
      var idx = items.indexOf(document.activeElement);
      if (idx === -1) return;
      var next = null;
      if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = items[(idx + 1) % items.length];
      else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp')
        next = items[(idx - 1 + items.length) % items.length];
      else if (e.key === 'Home') next = items[0];
      else if (e.key === 'End') next = items[items.length - 1];
      if (next) {
        e.preventDefault();
        next.focus();
      }
    });
  }

  /* ---------- 4. 视口高度 ---------- */
  function setVh() {
    root.style.setProperty('--vh', window.innerHeight * 0.01 + 'px');
  }

  document.addEventListener('DOMContentLoaded', function () {
    syncNav();
    setVh();
    window.addEventListener('resize', setVh);

    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      btn.addEventListener('click', cycleTheme);
    });

    document.querySelectorAll('[data-roving]').forEach(bindRovingTabindex);
  });

  /* 暴露给页面脚本复用 */
  window.ShangKeTheme = {
    apply: applyTheme,
    toggle: cycleTheme,
    current: currentMode,
  };
})();
