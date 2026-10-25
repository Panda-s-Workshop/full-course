// Full Course website: mobile menu, gallery filter and lightbox, dish filter, TOC highlight.
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

  const btn = $('.menu-btn'), nav = $('.nav');
  if (btn) btn.addEventListener('click', () => {
    const open = nav.classList.toggle('open');
    btn.setAttribute('aria-expanded', open);
  });

  // fade-in on scroll (content stays visible without JS, and a timer shows everything if the observer never fires)
  document.documentElement.classList.add('js');
  setTimeout(() => $$('.reveal').forEach(el => el.classList.add('in')), 2500);
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: 0.08 });
    $$('.reveal').forEach(el => io.observe(el));
  } else $$('.reveal').forEach(el => el.classList.add('in'));

  // gallery filter
  const chips = $$('.chip[data-f]');
  chips.forEach(c => c.addEventListener('click', () => {
    chips.forEach(o => o.setAttribute('aria-pressed', o === c));
    $$('.gallery .shot').forEach(s => { s.hidden = c.dataset.f !== 'all' && s.dataset.cat !== c.dataset.f; });
  }));

  // dish filter: one choice per group (tier, benefit, station); a dish shows when it matches every group
  const dchips = $$('.chip[data-g]');
  if (dchips.length) {
    const pick = {};
    const run = () => {
      let shown = 0;
      $$('.dishes .dish').forEach(d => {
        const ok = Object.entries(pick).every(([g, v]) => v === 'all' || (' ' + d.dataset[g] + ' ').includes(' ' + v + ' '));
        d.hidden = !ok;
        if (ok) shown++;
      });
      const n = $('#dish-count');
      if (n) n.textContent = shown + (shown === 1 ? ' dish' : ' dishes');
    };
    dchips.forEach(c => c.addEventListener('click', () => {
      $$('.chip[data-g="' + c.dataset.g + '"]').forEach(o => o.setAttribute('aria-pressed', o === c));
      pick[c.dataset.g] = c.dataset.v;
      run();
    }));
  }

  // lightbox
  const shots = $$('a.shot');
  if (shots.length) {
    const lb = document.createElement('div');
    lb.className = 'lb'; lb.setAttribute('role', 'dialog'); lb.setAttribute('aria-label', 'Image viewer');
    lb.innerHTML = '<button class="x" aria-label="Close">✕</button><button class="p" aria-label="Previous">‹</button><button class="n" aria-label="Next">›</button><img alt=""><div class="cap"></div>';
    document.body.appendChild(lb);
    const img = $('img', lb), cap = $('.cap', lb);
    let list = [], idx = 0;
    const show = i => {
      idx = (i + list.length) % list.length;
      const a = list[idx];
      img.src = a.href; img.alt = a.dataset.cap || '';
      cap.textContent = a.dataset.cap || '';
    };
    const close = () => lb.classList.remove('open');
    shots.forEach(a => a.addEventListener('click', e => {
      e.preventDefault();
      list = shots.filter(s => !s.hidden && s.closest('.gallery, .strip') === a.closest('.gallery, .strip'));
      show(list.indexOf(a));
      lb.classList.add('open');
    }));
    $('.x', lb).onclick = close;
    $('.p', lb).onclick = () => show(idx - 1);
    $('.n', lb).onclick = () => show(idx + 1);
    lb.addEventListener('click', e => { if (e.target === lb) close(); });
    document.addEventListener('keydown', e => {
      if (!lb.classList.contains('open')) return;
      if (e.key === 'Escape') close();
      if (e.key === 'ArrowLeft') show(idx - 1);
      if (e.key === 'ArrowRight') show(idx + 1);
    });
  }

  // TOC highlight
  const toc = $$('.toc a');
  if (toc.length && 'IntersectionObserver' in window) {
    const map = new Map(toc.map(a => [a.getAttribute('href').slice(1), a]));
    const io2 = new IntersectionObserver(es => es.forEach(e => {
      if (e.isIntersecting) { toc.forEach(a => a.classList.remove('on')); const a = map.get(e.target.id); if (a) a.classList.add('on'); }
    }), { rootMargin: '-20% 0px -70% 0px' });
    map.forEach((_, id) => { const el = document.getElementById(id); if (el) io2.observe(el); });
  }
})();
