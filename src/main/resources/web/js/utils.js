/* ══ UTILS ══ */
const Utils = {
  /* Color codes → HTML spans */
  mc(str) {
    if (!str) return '';
    const colors = {
      '0':'#000','1':'#0000aa','2':'#00aa00','3':'#00aaaa',
      '4':'#aa0000','5':'#aa00aa','6':'#ffaa00','7':'#aaaaaa',
      '8':'#555','9':'#5555ff','a':'#55ff55','b':'#55ffff',
      'c':'#ff5555','d':'#ff55ff','e':'#ffff55','f':'#fff',
    };
    const formats = { 'l':'font-weight:700', 'o':'font-style:italic', 'n':'text-decoration:underline', 'm':'text-decoration:line-through' };
    let html = '', style = '';
    const s = str.replace(/&/g, '§');
    let i = 0;
    while (i < s.length) {
      if ((s[i] === '§') && i + 1 < s.length) {
        const code = s[i+1].toLowerCase();
        if (colors[code]) { if (style) html += '</span>'; style = 'color:' + colors[code]; html += `<span style="${style}">`; }
        else if (formats[code]) { style += ';' + formats[code]; }
        else if (code === 'r') { if (style) html += '</span>'; style = ''; }
        i += 2;
      } else {
        html += s[i] === '<' ? '&lt;' : s[i] === '>' ? '&gt;' : s[i];
        i++;
      }
    }
    if (style) html += '</span>';
    return html;
  },

  /* Strip MC color codes → plain text */
  strip(str) {
    return str ? str.replace(/[&§][0-9a-fk-or]/gi, '') : '';
  },

  /* Format number */
  num(n) {
    if (n >= 1e6) return (n/1e6).toFixed(1) + 'M';
    if (n >= 1e3) return (n/1e3).toFixed(1) + 'K';
    return n?.toLocaleString() ?? '—';
  },

  /* Format duration ms → human */
  duration(ms) {
    if (!ms || ms <= 0) return 'None';
    const s = Math.floor(ms/1000), m = Math.floor(s/60), h = Math.floor(m/60), d = Math.floor(h/24);
    if (d > 0) return d + 'd ' + (h%24) + 'h';
    if (h > 0) return h + 'h ' + (m%60) + 'm';
    if (m > 0) return m + 'm ' + (s%60) + 's';
    return s + 's';
  },

  /* Format timestamp → "12:05" or "2h ago" */
  timeAgo(ts) {
    const diff = Date.now() - ts;
    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return Math.floor(diff/60000) + 'm ago';
    if (diff < 86400000) return Math.floor(diff/3600000) + 'h ago';
    return new Date(ts).toLocaleDateString();
  },

  timeStr(ts) {
    return new Date(ts).toLocaleTimeString('en-US', { hour:'2-digit', minute:'2-digit' });
  },

  /* Rarity helpers */
  rarityColor(r) {
    const map = { COMMON:'#aaa', UNCOMMON:'#00d97e', RARE:'#4488ff', EPIC:'#9b59f5', LEGENDARY:'#f5a623', MYTHIC:'#ff44aa' };
    return map[(r||'').toUpperCase()] || '#aaa';
  },
  rarityOrder(r) {
    const o = { COMMON:0, UNCOMMON:1, RARE:2, EPIC:3, LEGENDARY:4, MYTHIC:5 };
    return o[(r||'').toUpperCase()] ?? 0;
  },
  rarityIcon(r) {
    const map = { COMMON:'⬜', UNCOMMON:'🟩', RARE:'🔷', EPIC:'🟣', LEGENDARY:'🟡', MYTHIC:'🩷' };
    return map[(r||'').toUpperCase()] || '⬜';
  },
  materialIcon(mat) {
    const m = { DIAMOND_SWORD:'⚔️', BOW:'🏹', DIAMOND:'💎', EMERALD:'🟢', GOLD_NUGGET:'🪙',
      GOLD_BLOCK:'🟨', IRON_BLOCK:'⬜', NETHERITE_INGOT:'⚫', NETHER_STAR:'⭐',
      NETHERITE_CHESTPLATE:'🛡️', IRON_SWORD:'🗡️', TRIPWIRE_HOOK:'🔗', PAPER:'📄',
      BLAZE_ROD:'🔥', ENDER_EYE:'👁️', BEACON:'🔆', TOTEM_OF_UNDYING:'🏺', DEFAULT:'📦' };
    return m[(mat||'').toUpperCase()] || m.DEFAULT;
  },

  /* Weight → chance % */
  chance(weight, totalWeight) {
    if (!totalWeight) return 0;
    return (weight / totalWeight) * 100;
  },
  fmtChance(pct) {
    if (pct === 0) return '0%';
    if (pct < 0.01) return '<0.01%';
    if (pct < 1) return pct.toFixed(2) + '%';
    return pct.toFixed(2) + '%';
  },
  totalWeight(rewards) {
    return (rewards || []).reduce((s, r) => s + (r.weight || 0), 0);
  },

  /* DOM helpers */
  el(tag, cls, html) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html !== undefined) e.innerHTML = html;
    return e;
  },
  qs(sel, parent = document) { return parent.querySelector(sel); },
  qsa(sel, parent = document) { return [...parent.querySelectorAll(sel)]; },
  on(el, ev, fn) { el?.addEventListener(ev, fn); return el; },

  /* Debounce */
  debounce(fn, ms = 300) {
    let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
  },

  /* Deep clone */
  clone(obj) { return JSON.parse(JSON.stringify(obj)); },

  /* UUID basic check */
  isUUID(s) { return /^[0-9a-f-]{36}$/i.test(s); },
};

/* ══ TOAST ══ */
function toast(msg, type = 'success', duration = 3000) {
  const icons = { success:'✓', error:'✕', info:'ℹ', warning:'⚠' };
  const t = Utils.el('div', `toast toast-${type} animate-in`);
  t.innerHTML = `<span class="toast-icon">${icons[type] || '•'}</span><span>${msg}</span><button class="toast-close" onclick="this.parentNode.remove()">✕</button>`;
  document.getElementById('toastContainer').appendChild(t);
  setTimeout(() => { t.style.animation = 'toast-out .3s forwards'; setTimeout(() => t.remove(), 300); }, duration);
}

/* ══ MODAL SYSTEM ══ */
const Modal = {
  open(content, size = 'modal-md') {
    const c = document.getElementById('modalContainer');
    c.innerHTML = '';
    const m = Utils.el('div', `modal ${size} animate-in`);
    m.innerHTML = content;
    c.appendChild(m);
    document.getElementById('modalOverlay').classList.add('open');
    setTimeout(() => m.querySelector('[autofocus]')?.focus(), 100);
    return m;
  },
  close() { document.getElementById('modalOverlay').classList.remove('open'); },
};

function closeModalOnOverlay(e) {
  if (e.target === document.getElementById('modalOverlay')) Modal.close();
}
