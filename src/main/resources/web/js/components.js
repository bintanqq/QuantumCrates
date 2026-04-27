/* ══ SHARED COMPONENTS ══ */

/* Reward Card */
function RewardCard(reward, totalWeight, onClick, onRemove) {
  const pct   = Utils.chance(reward.weight, totalWeight);
  const color = Utils.rarityColor(reward.rarity);
  const icon  = reward.iconUrl
    ? `<img src="${reward.iconUrl}" alt=""/>`
    : `<span style="font-size:26px">${Utils.materialIcon(reward.material)}</span>`;

  const div = Utils.el('div', 'reward-card');
  div.dataset.id = reward.id;
  div.innerHTML = `
    <button class="rc-remove" title="Remove">✕</button>
    <div class="rc-icon">
      ${icon}
      <div class="rc-rarity-bar" style="background:${color}"></div>
    </div>
    <div class="rc-name">${Utils.strip(reward.displayName) || reward.id}</div>
    <div class="rc-weight">${Utils.fmtChance(pct)}</div>
  `;
  div.onclick = (e) => { if (!e.target.classList.contains('rc-remove')) onClick?.(reward); };
  div.querySelector('.rc-remove').onclick = (e) => { e.stopPropagation(); onRemove?.(reward); };
  return div;
}

/* Add Card button */
function AddCard(onClick) {
  const div = Utils.el('div', 'add-card');
  div.innerHTML = `
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
    <span>Add Reward</span>
  `;
  div.onclick = onClick;
  return div;
}

function SliderRow(reward, totalWeight, onChange) {
  const color = Utils.rarityColor(reward.rarity);
  const div = Utils.el('div', 'slider-row');
  div.dataset.id = reward.id;

  function updateSliderBg(input, val) {
    const pct = Math.min(val / 50 * 100, 100);
    input.style.background = `linear-gradient(to right,${color} ${pct}%,var(--bg2) ${pct}%)`;
  }

  div.innerHTML = `
    <div class="slider-name">
      <span class="slider-rarity-dot" style="background:${color};box-shadow:0 0 5px ${color}60"></span>
      ${Utils.strip(reward.displayName) || reward.id}
    </div>
    <input type="range" class="slider-range" min="0.1" max="50" step="0.5" value="${reward.weight}" style="cursor:grab;touch-action:none;"/>
    <div class="slider-controls">
      <button class="slider-btn" data-action="minus">−</button>
      <input class="slider-val" type="number" min="0.1" step="0.5" value="${reward.weight}" inputmode="decimal"/>
      <span class="slider-pct">%</span>
      <button class="slider-btn" data-action="plus">+</button>
    </div>
  `;

  const range  = div.querySelector('.slider-range');
  const numIn  = div.querySelector('.slider-val');

  updateSliderBg(range, reward.weight);

  const update = (val) => {
    val = Math.max(0.1, Math.min(50, parseFloat(val) || 0.1));
    reward.weight = parseFloat(val.toFixed(1));
    range.value = val;
    numIn.value = val.toFixed(1);
    updateSliderBg(range, val);
    onChange?.(reward);
  };

  // Pake 'input' bukan 'oninput' attribute supaya drag bener
  range.addEventListener('input', () => update(range.value));

  // Prevent parent scroll/drag intercept saat drag slider
  range.addEventListener('mousedown', (e) => e.stopPropagation());
  range.addEventListener('touchstart', (e) => e.stopPropagation(), { passive: true });

  numIn.addEventListener('change', () => update(numIn.value));
  // Update live saat ngetik juga
  numIn.addEventListener('input', () => {
    if (numIn.value !== '' && !isNaN(numIn.value)) update(numIn.value);
  });

  div.querySelector('[data-action="minus"]').addEventListener('click', () => update(reward.weight - 0.5));
  div.querySelector('[data-action="plus"]').addEventListener('click',  () => update(reward.weight + 0.5));

  return div;
}

/* Log Item */
function LogItem(log) {
  const crate  = State.crates[log.crateId];
  const reward = crate?.rewards?.find(r => r.id === log.rewardId);
  const rarity = (reward?.rarity || 'COMMON').toLowerCase();
  const icon   = reward?.iconUrl
    ? `<img src="${reward.iconUrl}" style="width:18px;height:18px;image-rendering:pixelated;" alt=""/>`
    : Utils.materialIcon(reward?.material);
  const isPity = log.pityAtOpen > 0 && crate?.pity?.enabled && log.pityAtOpen >= crate.pity.threshold - 1;

  const div = Utils.el('div', 'log-item');
  div.innerHTML = `
    <span class="log-time">${Utils.timeStr(log.timestamp)}</span>
    <div class="log-avatar">${icon}</div>
    <div class="log-body">
      <div class="log-player">${log.playerName} <span style="color:var(--text3);font-weight:400">opened</span> ${Utils.strip(crate?.displayName || log.crateId)}</div>
      <div class="log-reward ${rarity}">
        → ${Utils.strip(log.rewardDisplay)}
        ${isPity ? '<span class="log-pity-tag">PITY</span>' : ''}
      </div>
    </div>
    <span style="font-size:10px;color:var(--text3)">${Utils.timeAgo(log.timestamp)}</span>
  `;
  return div;
}

/* Pity Bar */
function PityBar(pity, max, soft) {
  const fillPct  = max > 0 ? (pity / max * 100) : 0;
  const softPct  = max > 0 ? (soft / max * 100) : 0;
  const remaining = max - pity;
  const div = Utils.el('div', 'pity-bar-card');
  div.innerHTML = `
    <div class="pity-bar-header">
      <div class="pity-bar-label">🏆 Pity Counter</div>
      <div class="pity-bar-val">${pity} / ${max}</div>
    </div>
    <div class="pity-track">
      <div class="pity-fill" style="width:${fillPct}%"></div>
      <div class="pity-soft-marker" style="left:${softPct}%" title="Soft pity start"></div>
    </div>
    <div class="pity-desc"><strong>${remaining} opens</strong> until next guaranteed rare!</div>
  `;
  return div;
}

/* Stat Card */
function StatCard(icon, val, label, delta, iconBg) {
  const div = Utils.el('div', 'stat-card');
  div.innerHTML = `
    <div class="stat-icon" style="background:${iconBg || 'var(--bg2)'}">${icon}</div>
    <div class="stat-val">${val}</div>
    <div class="stat-label">${label}</div>
    ${delta ? `<div class="stat-delta ${delta > 0 ? 'up' : 'down'}">${delta > 0 ? '↑' : '↓'} ${Math.abs(delta)}%</div>` : ''}
  `;
  return div;
}

/* Toggle Switch */
function ToggleSwitch(label, checked, onChange) {
  const id = 'tgl-' + Math.random().toString(36).slice(2);
  const div = Utils.el('div', 'toggle-wrap');
  div.innerHTML = `
    <span class="toggle-label-text">${label}</span>
    <label class="toggle-switch">
      <input type="checkbox" id="${id}" ${checked ? 'checked' : ''}/>
      <span class="toggle-slider"></span>
    </label>
  `;
  div.querySelector('input').onchange = e => onChange?.(e.target.checked);
  return div;
}

/* Badge */
function RarityBadge(rarity) {
  const span = Utils.el('span', `badge badge-${rarity.toLowerCase()}`);
  span.textContent = rarity;
  return span;
}
