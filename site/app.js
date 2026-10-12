/* TapTL site: theme toggle, before/after slider, top bar tint, reveal on scroll. No dependencies. */
(function () {
  var root = document.documentElement;

  // Theme: follows the system until the visitor picks one, then remembers it.
  var toggle = document.getElementById('theme-toggle');
  function isDark() {
    var t = root.getAttribute('data-theme');
    if (t) return t === 'dark';
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
  function label() {
    if (toggle) toggle.setAttribute('aria-label', isDark() ? 'Switch to light theme' : 'Switch to dark theme');
  }
  if (toggle) {
    label();
    toggle.addEventListener('click', function () {
      var next = isDark() ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem('taptl-theme', next); } catch (e) {}
      label();
    });
  }

  // Top bar picks up a surface tint once the page scrolls.
  var bar = document.querySelector('.top-bar');
  if (bar) {
    var onScroll = function () { bar.classList.toggle('scrolled', window.scrollY > 8); };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  // Before / after: a range input drives the split; the segmented button jumps to either end.
  var compare = document.querySelector('.compare');
  if (compare) {
    var range = compare.querySelector('input[type="range"]');
    var seg = document.querySelectorAll('[data-compare]');
    var set = function (v) {
      compare.style.setProperty('--pos', v + '%');
      range.value = v;
      seg.forEach(function (b) {
        var target = b.getAttribute('data-compare') === 'before' ? 100 : 0;
        b.setAttribute('aria-pressed', String(Math.abs(v - target) < 1));
      });
    };
    range.addEventListener('input', function () { set(Number(range.value)); });
    seg.forEach(function (b) {
      b.addEventListener('click', function () {
        set(b.getAttribute('data-compare') === 'before' ? 100 : 0);
      });
    });
    set(Number(range.value));
  }

  // Sections slide in once, as they enter the viewport.
  var items = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && items.length) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
      });
    }, { rootMargin: '0px 0px -8% 0px' });
    items.forEach(function (el) { io.observe(el); });
  } else {
    items.forEach(function (el) { el.classList.add('in'); });
  }
})();
