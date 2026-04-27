/* ══ MODAL: CRATE PREVIEW ══ */
const PreviewModal = {
  open(crate) {
    if (!crate) { toast('No crate selected', 'error'); return; }
    const rewards  = crate.rewards || [];
    const tw       = Utils.totalWeight(rewards);
    const cfg      = crate.preview || {};
    const sortOrder= cfg.sortOrder || 'RARITY_DESC';

    const sorted = [...rewards].sort((a, b) => {
      switch (sortOrder) {
        case 'RARITY_DESC':  return Utils.rarityOrder(b.rarity) - Utils.rarityOrder(a.rarity) || b.weight - a.weight;
        case 'RARITY_ASC':   return Utils.rarityOrder(a.rarity) - Utils.rarityOrder(b.rarity) || a.weight - b.weight;
        case 'WEIGHT_DESC':  return b.weight - a.weight;
        case 'WEIGHT_ASC':   return a.weight - b.weight;
        default:             return 0;
      }
    });

    // Border color from config or auto from highest rarity
    const highestRarity = rewards.reduce((h, r) =>
      Utils.rarityOrder(r.rarity) > Utils.rarityOrder(h) ? r.rarity : h, 'COMMON');
    const borderColor = Utils.rarityColor(highestRarity);

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            Crate Preview
            <span style="color:var(--cyan);margin-left:4px">${Utils.mc(crate.displayName || crate.id)}</span>
          </div>
          <div class="modal-subtitle">${sorted.length} rewards · Total weight: ${tw.toFixed(1)} · Sort: ${sortOrder.replace('_',' ')}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>

      <div class="mc-inv-wrap">
        <div style="font-size:10px;font-weight:700;color:var(--text3);letter-spacing:.8px;text-transform:uppercase;margin-bottom:8px">Reward Pool</div>
        <div class="mc-grid" id="mcGrid" style="border-color:${borderColor}30"></div>
      </div>

      <div class="preview-footer">
        <div class="preview-stat">
          <div class="preview-stat-val" style="color:var(--cyan)">${sorted.length}</div>
          <div class="preview-stat-label">Rewards</div>
        </div>
        <div class="preview-stat-label">Rewards</div>
        </div>
        <div class="preview-stat">
          <div class="preview-stat-val" style="color:var(--gold)">
            ${
              sorted.filter(r =>
                State.rarityOrder(r.rarity) >= State.rarityOrder(State.rarities[State.rarities.length - 1]?.id || 'LEGENDARY')
              ).length
            }
          </div>
          <div class="preview-stat-label">Legendary+</div>
        </div>
        <div class="preview-stat">
          <div class="preview-stat-val" style="color:var(--text)">${Utils.fmtChance(Utils.chance(sorted[sorted.length-1]?.weight||0,tw))}</div>
          <div class="preview-stat-label">Most Common</div>
        </div>
      </div>
    `, 'modal-xl');

    this._buildGrid(sorted, tw, cfg);
  },

  _buildGrid(sorted, tw, cfg) {
    const grid = Utils.qs('#mcGrid'); if (!grid) return;
    grid.innerHTML = '';

    // 54 slots: border slots + reward slots + nav row
    const TOTAL = 54;
    const borderSet = new Set([
      0,1,2,3,4,5,6,7,8,       // row 0
      9,17,18,26,27,35,36,44,  // col 0, col 8
      45,46,47,48,49,50,51,52,53 // row 5
    ]);

    let rIdx = 0;
    for (let s = 0; s < TOTAL; s++) {
      const slot = document.createElement('div');

      if (borderSet.has(s)) {
        slot.className = 'mc-slot border-sl';
        // Nav buttons on row 5
        if (s === 49) { slot.textContent = '📖'; slot.title = 'Reward Info'; }
        if (s === 48) {
          slot.className += ' close-sl interactive';
          slot.textContent = '✕';
          slot.onclick = () => Modal.close();
          slot.title = 'Close Preview';
        }
      } else {
        const r = sorted[rIdx++];
        if (r) {
          const pct   = Utils.chance(r.weight, tw);

          const color = State.rarityColor(r.rarity);
          const rName = State.rarityName(r.rarity);
          const rIcon = State.rarityIcon(r.rarity);

          slot.className = 'mc-slot interactive';
          slot.style.borderBottom = `2px solid ${color}`;
          slot.innerHTML = `
            <div class="slot-rarity-dot" style="background:${color};box-shadow:0 0 4px ${color}80"></div>
            ${icon}
            ${cfg.showChance !== false ? `<div class="slot-chance">${pct < 0.01 ? '<0.01' : pct.toFixed(2)}%</div>` : ''}
            <div class="slot-tooltip">
              <div class="tt-name">${Utils.strip(r.displayName)}</div>

              <div class="tt-rarity" style="color:${color}">${rIcon} ${rName}</div>

              <div class="tt-sep"></div>
              ${cfg.showChance !== false ? `<div class="tt-chance">Chance: ${pct.toFixed(4)}%</div>` : ''}
              <div class="tt-extra">Amount: x${r.amount || 1}</div>
            </div>
          `;
        }

          slot.className = 'mc-slot interactive';
          slot.style.borderBottom = `2px solid ${color}`;
          slot.innerHTML = `
            <div class="slot-rarity-dot" style="background:${color};box-shadow:0 0 4px ${color}80"></div>
            ${icon}
            ${cfg.showChance !== false ? `<div class="slot-chance">${pct < 0.01 ? '<0.01' : pct.toFixed(2)}%</div>` : ''}
            <div class="slot-tooltip">
              <div class="tt-name">${Utils.strip(r.displayName)}</div>
              <div class="tt-rarity" style="color:${color}">${Utils.rarityIcon(r.rarity)} ${r.rarity}</div>
              <div class="tt-sep"></div>
              ${cfg.showChance !== false ? `<div class="tt-chance">Chance: ${pct.toFixed(4)}%</div>` : ''}
              ${cfg.showWeight ? `<div class="tt-extra">Weight: ${r.weight}</div>` : ''}
              <div class="tt-extra">Amount: x${r.amount || 1}</div>
              ${r.commands?.length ? `<div class="tt-extra" style="color:var(--green)">+ Command reward</div>` : ''}
              ${r.broadcast ? `<div class="tt-extra" style="color:var(--gold)">✦ Broadcast</div>` : ''}
            </div>
          `;
        } else {
          slot.className = 'mc-slot empty-sl';
        }
      }
      grid.appendChild(slot);
    }
  },
};
