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

  // Before / after: a range input drives the split; the segmented button glides to either end.
  // Once it scrolls into view, the handle sweeps both ways so visitors see it can be dragged.
  var compare = document.querySelector('.compare');
  if (compare) {
    var range = compare.querySelector('input[type="range"]');
    var segGroup = document.querySelector('.segmented');
    var seg = document.querySelectorAll('[data-compare]');
    var calm = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var pos = Number(range.value), frame = 0, hintTimer = 0, heading = '';
    var set = function (v) {
      pos = v;
      compare.style.setProperty('--pos', v + '%');
      range.value = v;
      var active = '';
      seg.forEach(function (b) {
        var side = b.getAttribute('data-compare');
        var on = Math.abs(v - (side === 'before' ? 100 : 0)) < 1;
        b.setAttribute('aria-pressed', String(on));
        if (on) active = side;
      });
      // While a button glide runs, the pill already sits on the side it is heading to.
      if (segGroup) segGroup.setAttribute('data-active', heading || active);
    };
    // Eased glide from the current split to `to`; calls `done` when it lands.
    var glide = function (to, ms, done) {
      cancelAnimationFrame(frame);
      if (calm) { set(to); if (done) done(); return; }
      var from = pos, start = performance.now();
      var step = function (now) {
        var t = Math.min(1, (now - start) / ms);
        var e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
        set(from + (to - from) * e);
        if (t < 1) frame = requestAnimationFrame(step); else if (done) done();
      };
      frame = requestAnimationFrame(step);
    };
    var touched = function () {
      heading = '';
      compare.classList.remove('idle');
      clearTimeout(hintTimer);
    };
    range.addEventListener('pointerdown', function () { touched(); cancelAnimationFrame(frame); });
    range.addEventListener('input', function () { touched(); cancelAnimationFrame(frame); set(Number(range.value)); });
    seg.forEach(function (b) {
      b.addEventListener('click', function () {
        touched();
        heading = b.getAttribute('data-compare');
        glide(heading === 'before' ? 100 : 0, 650, function () { heading = ''; set(pos); });
      });
    });
    set(pos);

    if (!calm && 'IntersectionObserver' in window) {
      compare.classList.add('idle');
      var seen = new IntersectionObserver(function (entries) {
        if (!entries[0].isIntersecting) return;
        seen.disconnect();
        hintTimer = setTimeout(function () {
          if (!compare.classList.contains('idle')) return;
          glide(72, 700, function () {
            if (!compare.classList.contains('idle')) return;
            glide(28, 900, function () {
              if (compare.classList.contains('idle')) glide(50, 700);
            });
          });
        }, 500);
      }, { threshold: 0.6 });
      seen.observe(compare);
    }
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
