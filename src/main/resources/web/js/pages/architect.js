/* ══ PAGE: CRATE ARCHITECT ══ */
const Architect = {
  dirty: false,

  render(container) {
    container.innerHTML = `
      <div class="page-header">
        <div>
          <div class="page-title">Crate Architect</div>
          <div class="page-sub">Design and configure the ultimate loot experience.</div>
        </div>
        <div class="page-actions">
          <button class="btn btn-ghost btn-sm" id="btnRarityEditor">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            Rarities
          </button>
          <button class="btn btn-danger btn-sm" id="btnDiscardCrate">↩ Discard</button>
          <button class="btn btn-primary btn-sm" id="btnSaveCrate">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
            Save Crate
          </button>
        </div>
      </div>

      <div class="crate-selector-bar" id="crateTabs"></div>

      <div style="display:flex;flex-direction:column;gap:14px" id="architectMain">

        <!-- Rewards -->
        <div class="card" id="rewardsCard">
          <div class="card-header">
            <div class="card-title"><span class="card-accent"></span>REWARDS <span class="card-sub">Loot Table</span></div>
            <button class="btn btn-ghost btn-sm" id="btnAddReward">+ Add Reward</button>
          </div>
          <div class="rewards-grid" id="rewardsGrid"></div>
        </div>

        <!-- Chance Management — as a card button -->
        <div class="card arch-config-card" id="weightCard" onclick="Architect.openWeightModal()" style="cursor:pointer">
          <div style="display:flex;align-items:center;gap:14px">
            <div style="width:40px;height:40px;border-radius:10px;background:var(--cyan-dim2);border:1px solid rgba(0,74,173,.3);display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">⚖️</div>
            <div style="flex:1;min-width:0">
              <div style="font-size:13px;font-weight:700;color:var(--text)">Chance Management</div>
              <div style="font-size:11px;color:var(--text3);margin-top:2px" id="weightSummary">Configure reward weights and drop chances</div>
            </div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--text3)" stroke-width="2" style="flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <!-- Configuration cards grid -->
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:10px" id="crateConfigCards"></div>

      </div>
    `;

    this._bindTopActions();
    this.renderCrateTabs();
    if (State.currentCrateId) this.loadCrate(State.currentCrateId);
  },

  _bindTopActions() {
    Utils.on(Utils.qs('#btnSaveCrate'),    'click', () => this.save());
    Utils.on(Utils.qs('#btnDiscardCrate'), 'click', () => this.discard());
    Utils.on(Utils.qs('#btnAddReward'),    'click', () => RewardModal.open(null, r => this.addReward(r)));
    Utils.on(Utils.qs('#btnRarityEditor'), 'click', () => RarityEditor.open());
  },

  renderCrateTabs() {
    const bar = Utils.qs('#crateTabs');
    if (!bar) return;
    bar.innerHTML = '';
    State.crateOrder.forEach(id => {
      const c   = State.crates[id];
      const tab = Utils.el('div', `crate-tab${id === State.currentCrateId ? ' active' : ''}`);
      const color = Utils.rarityColor(this._highestRarity(c.rewards));
      tab.innerHTML = `<span class="crate-tab-dot" style="background:${color}"></span>${Utils.strip(c.displayName || id)}`;
      tab.onclick = () => this.loadCrate(id);
      bar.appendChild(tab);
    });
    const add = Utils.el('div', 'crate-tab crate-tab-add');
    add.innerHTML = '+ New Crate';
    add.onclick = () => this.newCrate();
    bar.appendChild(add);
  },

  loadCrate(id) {
    State.currentCrateId = id;
    this.renderCrateTabs();
    this.renderRewards();
    this.renderWeightSummary();
    this.renderConfigCards();
  },

  /* ── Rewards ── */
  renderRewards() {
    const grid  = Utils.qs('#rewardsGrid'); if (!grid) return;
    const crate = State.currentCrate;       if (!crate) return;
    grid.innerHTML = '';
    const tw = Utils.totalWeight(crate.rewards);
    const sorted = this._sortedRewards(crate.rewards);
    sorted.forEach(r => {
      grid.appendChild(RewardCard(r, tw,
        () => RewardModal.open(r, updated => this.updateReward(r.id, updated)),
        () => this.removeReward(r.id)
      ));
    });
    grid.appendChild(AddCard(() => RewardModal.open(null, r => this.addReward(r))));
  },

  /* ── Weight summary text ── */
  renderWeightSummary() {
    const el    = Utils.qs('#weightSummary'); if (!el) return;
    const crate = State.currentCrate;         if (!crate) return;
    const count = crate.rewards?.length || 0;
    const tw    = Utils.totalWeight(crate.rewards);
    if (!count) { el.textContent = 'No rewards yet — add one above'; return; }
    el.textContent = `${count} rewards · Total weight ${tw.toFixed(1)}`;
  },

  /* ── Config Cards ── */
  renderConfigCards() {
    const grid  = Utils.qs('#crateConfigCards'); if (!grid) return;
    const crate = State.currentCrate;            if (!crate) return;
    grid.innerHTML = '';

    const pity     = crate.pity     || {};
    const sch      = crate.schedule || null;
    const keyCount = crate.requiredKeys?.length || 0;

    const schedDesc = () => {
          if (!sch || sch.mode === 'ALWAYS' || !sch.mode) return 'Always open';
          if (sch.mode === 'TIME_WINDOW')  return `Daily ${sch.startTime||'?'}–${sch.endTime||'?'}`;
          if (sch.mode === 'DAYS_OF_WEEK') {
            const dayNames = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
            const days = (sch.daysOfWeek||[]).map(d => dayNames[d-1]).join(', ');
            return days || 'Select days';
          }
          if (sch.mode === 'EVENT') {
            const now = Date.now();
            if (sch.eventEnd && now > sch.eventEnd) return 'Event ended';
            if (sch.eventStart && now < sch.eventStart) return 'Event not started';
            return 'Event LIVE 🟢';
          }
          return sch.mode;
        };

    const hasSchedule = sch && sch.mode && sch.mode !== 'ALWAYS';

    const cards = [
      {
        emoji: '⚙️',
        label: 'Crate Settings',
        sub: `${crate.enabled !== false ? 'Enabled' : 'Disabled'} · ${crate.cooldownMs ? Utils.duration(crate.cooldownMs) + ' cooldown' : 'No cooldown'}`,
        color: 'var(--cyan)',
        bg: 'var(--cyan-dim2)', border: 'rgba(0,74,173,.3)',
        onclick: () => CrateSettingsModal.open(crate, () => { Architect.dirty = true; Architect.renderCrateTabs(); Architect.renderConfigCards(); })
      },
      {
        emoji: '🎯',
        label: 'Pity System',
        sub: pity.enabled ? `Active · ${pity.threshold || 100} opens hard cap` : 'Disabled',
        color: pity.enabled ? 'var(--gold)' : 'var(--text3)',
        bg: pity.enabled ? 'var(--gold-dim)' : 'var(--bg3)',
        border: pity.enabled ? 'rgba(245,166,35,.3)' : 'var(--border)',
        onclick: () => PityModal.open(crate, () => { Architect.dirty = true; Architect.renderConfigCards(); })
      },
      {
        emoji: '📅',
        label: 'Schedule',
        sub: schedDesc(),
        color: hasSchedule ? 'var(--purple)' : 'var(--text3)',
        bg: hasSchedule ? 'var(--purple-dim)' : 'var(--bg3)',
        border: hasSchedule ? 'rgba(139,92,246,.3)' : 'var(--border)',
        onclick: () => ScheduleModal.open(crate, () => { Architect.dirty = true; Architect.renderConfigCards(); })
      },
      {
        emoji: '🔑',
        label: 'Key Requirements',
        sub: keyCount ? `${keyCount} key type${keyCount > 1 ? 's' : ''} required` : 'No keys configured',
        color: 'var(--blue)',
        bg: 'var(--blue-dim)', border: 'rgba(59,130,246,.3)',
        onclick: () => KeyReqModal.open(crate, () => { Architect.dirty = true; Architect.renderConfigCards(); })
      },
      {
        emoji: '📝',
        label: 'Edit Hologram',
        sub: (crate.hologramLines?.length || 0) + ' lines configured',
        color: 'var(--green)',
        bg: 'var(--green-dim)', border: 'rgba(34,217,138,.3)',
        onclick: () => HologramModal.open()
      },
      {
        emoji: '🗑️',
        label: 'Delete Crate',
        sub: 'Permanently remove this crate',
        color: 'var(--red)',
        bg: 'var(--red-dim)', border: 'rgba(255,77,109,.3)',
        onclick: () => Architect.confirmDeleteCrate(crate.id)
      },
    ];

    cards.forEach(({ emoji, label, sub, color, bg, border, onclick }) => {
      const card = Utils.el('div', 'arch-config-card');
      card.style.cssText = `cursor:pointer;background:var(--surface);border:1px solid var(--border);
        border-radius:var(--radius);padding:16px;transition:all .2s var(--ease)`;
      card.innerHTML = `
        <div style="width:38px;height:38px;border-radius:10px;background:${bg};border:1px solid ${border};
          display:flex;align-items:center;justify-content:center;font-size:18px;margin-bottom:12px">${emoji}</div>
        <div style="font-size:12.5px;font-weight:700;color:var(--text);margin-bottom:4px">${label}</div>
        <div style="font-size:10.5px;color:${color};line-height:1.4">${sub}</div>
      `;
      card.onmouseenter = () => {
        card.style.borderColor = border;
        card.style.transform   = 'translateY(-2px)';
        card.style.boxShadow   = '0 4px 20px rgba(0,0,0,.4)';
      };
      card.onmouseleave = () => {
        card.style.borderColor = 'var(--border)';
        card.style.transform   = '';
        card.style.boxShadow   = '';
      };
      card.onclick = onclick;
      grid.appendChild(card);
    });
  },


  /* ── Weight Modal ── */
  openWeightModal() {
    const crate = State.currentCrate; if (!crate) return;
    if (!crate.rewards?.length) { toast('No rewards to configure — add rewards first', 'info'); return; }

    const renderRows = () => {
      const list  = Utils.qs('#wmList'); if (!list) return;
      const sorted = this._sortedRewards(crate.rewards);
      const tw = Utils.totalWeight(crate.rewards);
      list.innerHTML = '';

      // Header
      const hdr = Utils.el('div');
      hdr.style.cssText = 'display:grid;grid-template-columns:1fr 120px 80px 80px;gap:8px;padding:6px 4px;font-size:9.5px;font-weight:700;color:var(--text3);letter-spacing:.7px;text-transform:uppercase;border-bottom:1px solid var(--border);margin-bottom:6px';
      hdr.innerHTML = '<span>Reward</span><span>Weight</span><span>Chance</span><span>Rarity</span>';
      list.appendChild(hdr);

      sorted.forEach(r => {
        const pct   = Utils.chance(r.weight, tw);
        const color = Utils.rarityColor(r.rarity);
        const row   = Utils.el('div');
        row.style.cssText = 'display:grid;grid-template-columns:1fr 120px 80px 80px;gap:8px;align-items:center;padding:7px 4px;border-radius:6px;transition:background .15s';
        row.onmouseenter = () => row.style.background = 'var(--surface)';
        row.onmouseleave = () => row.style.background = '';

        const nameEl = Utils.el('div');
        nameEl.style.cssText = 'display:flex;align-items:center;gap:7px;font-size:12px;font-weight:500;overflow:hidden';
        nameEl.innerHTML = `<span style="width:8px;height:8px;border-radius:50%;background:${color};flex-shrink:0"></span><span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${Utils.strip(r.displayName) || r.id}</span>`;

        const wtWrap = Utils.el('div');
        wtWrap.style.cssText = 'display:flex;align-items:center;gap:4px';
        const minus = Utils.el('button', 'wt-btn'); minus.textContent = '−';
        const inp   = document.createElement('input');
        inp.type = 'number'; inp.className = 'field-input'; inp.value = r.weight.toFixed(1);
        inp.style.cssText = 'width:54px;padding:4px 6px;font-size:12px;font-weight:600;text-align:center';
        const plus  = Utils.el('button', 'wt-btn'); plus.textContent = '+';

        const chanceEl = Utils.el('div');
        chanceEl.style.cssText = 'font-size:12px;font-weight:700;color:var(--cyan)';
        chanceEl.textContent = Utils.fmtChance(pct);

        const applyW = (val) => {
          r.weight = parseFloat(Math.max(0.1, Math.min(9999, val)).toFixed(1));
          inp.value = r.weight.toFixed(1);
          this.dirty = true;
          const newTw = Utils.totalWeight(crate.rewards);
          chanceEl.textContent = Utils.fmtChance(Utils.chance(r.weight, newTw));
          this.renderWeightSummary();
        };
        minus.onclick = () => applyW(r.weight - 0.5);
        plus.onclick  = () => applyW(r.weight + 0.5);
        inp.onchange  = () => applyW(parseFloat(inp.value) || 0.1);

        wtWrap.appendChild(minus); wtWrap.appendChild(inp); wtWrap.appendChild(plus);

        const rarityEl = Utils.el('div');
        rarityEl.style.cssText = `font-size:10px;font-weight:700;color:${color}`;
        rarityEl.textContent = r.rarity;

        row.appendChild(nameEl); row.appendChild(wtWrap); row.appendChild(chanceEl); row.appendChild(rarityEl);
        list.appendChild(row);
      });

      const tw2 = Utils.totalWeight(crate.rewards);
      const totRow = Utils.el('div');
      totRow.style.cssText = 'display:flex;justify-content:space-between;padding:8px 4px 0;border-top:1px solid var(--border);margin-top:4px;font-size:11px';
      totRow.innerHTML = `<span style="color:var(--text3)">Total Weight</span><span style="font-weight:700;color:var(--cyan)">${tw2.toFixed(1)}</span>`;
      list.appendChild(totRow);
    };

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">⚖️ Chance Management</div>
          <div class="modal-subtitle">Adjust reward weights — higher weight = more common</div>
        </div>
        <div style="display:flex;align-items:center;gap:8px">
          <select class="field-input" id="wmSort" style="padding:5px 22px 5px 8px;font-size:11px;width:auto" onchange="Architect._wmSort()">
            <option value="RARITY_DESC">Rarity ↓</option>
            <option value="RARITY_ASC">Rarity ↑</option>
            <option value="WEIGHT_DESC">Weight ↓</option>
            <option value="WEIGHT_ASC">Weight ↑</option>
          </select>
          <button class="modal-close" onclick="Modal.close()">✕</button>
        </div>
      </div>
      <div class="modal-body">
        <div id="wmList"></div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Close</button>
      </div>
    `, 'modal-lg');

    renderRows();
    this._wmRenderFn = renderRows;
  },

  _wmSort() {
    const sel = Utils.qs('#wmSort');
    if (sel) {
      // update current sort pref
      const crate = State.currentCrate;
      if (crate?.preview) crate.preview.sortOrder = sel.value;
    }
    this._wmRenderFn?.();
  },

  /* ── Delete confirm modal ── */
  confirmDeleteCrate(id) {
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title" style="color:var(--red)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
            Delete Crate
          </div>
          <div class="modal-subtitle">This action cannot be undone.</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div style="padding:16px;background:var(--red-dim);border:1px solid rgba(255,77,109,.2);border-radius:var(--radius-sm);font-size:13px;line-height:1.7">
          <div style="font-weight:700;color:var(--red);margin-bottom:6px">⚠ Delete this crate?</div>
          <div style="color:var(--text2)">Crate <code style="color:var(--cyan);background:var(--bg3);padding:1px 6px;border-radius:4px">${id}</code> will be permanently removed from the server. All rewards, pity data, and configuration will be lost.</div>
        </div>
        <div style="margin-top:14px">
          <label class="field-label" style="margin-bottom:6px;display:block">Type the crate ID to confirm:</label>
          <input class="field-input" id="deleteConfirmInput" placeholder="${id}"/>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-danger" id="btnConfirmDelete" disabled onclick="Architect._doDelete('${id}')">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/></svg>
          Delete Permanently
        </button>
      </div>
    `, 'modal-md');

    Utils.qs('#deleteConfirmInput').oninput = function() {
      Utils.qs('#btnConfirmDelete').disabled = this.value !== id;
    };
  },

  async _doDelete(id) {
    try {
      await API.deleteCrate(id);
      State.deleteCrate(id);
      Modal.close();
      toast(`Crate "${id}" deleted.`, 'success');
      if (State.crateOrder.length > 0) {
        this.loadCrate(State.crateOrder[0]);
      } else {
        State.currentCrateId = null;
        this.renderCrateTabs();
        const main = Utils.qs('#architectMain');
        if (main) main.innerHTML = '<div class="empty-state" style="padding:60px"><p>No crates yet. Create one to get started.</p></div>';
      }
    } catch (e) { toast(e.message, 'error'); }
  },

  /* ── Reward CRUD ── */
  addReward(reward) {
    const crate = State.currentCrate; if (!crate) return;
    if (!crate.rewards) crate.rewards = [];
    crate.rewards.push(reward);
    this.dirty = true;
    this.renderRewards();
    this.renderWeightSummary();
  },

  updateReward(id, updated) {
    const crate = State.currentCrate; if (!crate) return;
    const idx   = crate.rewards.findIndex(r => r.id === id);
    if (idx !== -1) crate.rewards[idx] = updated;
    this.dirty = true;
    this.renderRewards();
    this.renderWeightSummary();
  },

  removeReward(id) {
    const crate = State.currentCrate; if (!crate) return;
    crate.rewards = crate.rewards.filter(r => r.id !== id);
    this.dirty = true;
    this.renderRewards();
    this.renderWeightSummary();
    toast('Reward removed', 'info');
  },

  /* ── Save / Discard ── */
  async save() {
    const crate = State.currentCrate; if (!crate) return;
    const btn   = Utils.qs('#btnSaveCrate');
    btn.disabled = true;
    btn.innerHTML = '<svg class="spin" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 00-9-9"/></svg> Saving...';
    try {
      await API.saveCrate(crate.id, crate);
      State.setCrate(crate);
      this.dirty = false;
      toast('Crate saved ✓', 'success');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.innerHTML = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> Save Crate';
    }
  },

  discard() {
    if (!this.dirty) { toast('Nothing to discard', 'info'); return; }
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">Discard Changes?</div>
          <div class="modal-subtitle">All unsaved changes will be lost.</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div style="padding:14px;background:var(--gold-dim);border:1px solid rgba(245,166,35,.2);border-radius:var(--radius-sm);font-size:13px;color:var(--text2)">
          Are you sure you want to discard all unsaved changes?
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="Architect._doDiscard()">↩ Discard</button>
      </div>
    `, 'modal-sm');
  },

  _doDiscard() {
    Modal.close();
    this.dirty = false;
    this.loadCrate(State.currentCrateId);
    toast('Changes discarded', 'info');
  },

  newCrate() {
    const id    = 'new_crate_' + Date.now();
    const midId = State.rarities[Math.floor(State.rarities.length / 2)]?.id || 'RARE';
    const crate = {
      id, displayName: '&fNew Crate', enabled: true,
      cooldownMs: 0, massOpenEnabled: true, massOpenLimit: 64,
      requiredKeys: [{ keyId: 'example_key', amount: 1, type: 'VIRTUAL' }],
      rewards: [],
      pity: { enabled: false, threshold: 100, softPityStart: 80, rareRarityMinimum: midId, bonusChancePerOpen: 2 },
      preview: { sortOrder: 'RARITY_DESC', showChance: true, showPity: true, showKeyBalance: true, showActualItem: true },
    };
    State.setCrate(crate);
    State.currentCrateId = id;
    this.dirty = true;
    this.renderCrateTabs();
    this.loadCrate(id);
    toast('New crate created — configure and save!', 'info');
  },

  _sortedRewards(rewards) {
    if (!rewards) return [];
    const order = Utils.qs('#wmSort')?.value || State.currentCrate?.preview?.sortOrder || 'RARITY_DESC';
    const arr = [...rewards];
    switch (order) {
      case 'RARITY_DESC':  return arr.sort((a,b) => Utils.rarityOrder(b.rarity) - Utils.rarityOrder(a.rarity) || b.weight - a.weight);
      case 'RARITY_ASC':   return arr.sort((a,b) => Utils.rarityOrder(a.rarity) - Utils.rarityOrder(b.rarity) || a.weight - b.weight);
      case 'WEIGHT_DESC':  return arr.sort((a,b) => b.weight - a.weight);
      case 'WEIGHT_ASC':   return arr.sort((a,b) => a.weight - b.weight);
      default:             return arr;
    }
  },

  _highestRarity(rewards) {
    if (!rewards?.length) return State.rarities[0]?.id || 'COMMON';
    return rewards.reduce((h, r) =>
      Utils.rarityOrder(r.rarity) > Utils.rarityOrder(h) ? r.rarity : h,
      State.rarities[0]?.id || 'COMMON'
    );
  },
};

/* ══════════════════════════════════════════════════════
   CRATE SETTINGS MODAL
══════════════════════════════════════════════════════ */
const CrateSettingsModal = {
  open(crate, onSave) {
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
            Crate Settings
          </div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName || crate.id)}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div style="display:flex;flex-direction:column;gap:12px">
          <div class="field-row">
            <div class="field-group">
              <label class="field-label">Crate ID</label>
              <input class="field-input" id="csId" value="${crate.id || ''}" placeholder="legendary_crate"/>
            </div>
            <div class="field-group">
              <label class="field-label">Display Name</label>
              <input class="field-input" id="csName" value="${crate.displayName || ''}" placeholder="&6&lLegendary Crate"/>
            </div>
          </div>
          <div class="field-row">
            <div class="field-group">
              <label class="field-label">Cooldown</label>
              <select class="field-input" id="csCooldown">
                <option value="0" ${!crate.cooldownMs?'selected':''}>No Cooldown</option>
                <option value="300000"   ${crate.cooldownMs===300000?'selected':''}>5 Minutes</option>
                <option value="1800000"  ${crate.cooldownMs===1800000?'selected':''}>30 Minutes</option>
                <option value="3600000"  ${crate.cooldownMs===3600000?'selected':''}>1 Hour</option>
                <option value="86400000" ${crate.cooldownMs===86400000?'selected':''}>24 Hours</option>
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Mass Open Limit</label>
              <input class="field-input" type="number" id="csMassLimit" value="${crate.massOpenLimit ?? 64}" min="-1"/>
            </div>
          </div>
          <div style="display:flex;gap:12px">
            <div style="flex:1" id="csEnabledToggle"></div>
            <div style="flex:1" id="csMassToggle"></div>
          </div>
          <div style="border-top:1px solid var(--border);padding-top:12px">
            <div class="section-label" style="margin-bottom:8px">IDLE ANIMATION</div>
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">Type</label>
                <select class="field-input" id="csIdleType">
                  ${['RING','HELIX','SPHERE','SPIRAL','RAIN','NONE'].map(t => `<option value="${t}" ${(crate.idleAnimation?.type||'RING')===t?'selected':''}>${t}</option>`).join('')}
                </select>
              </div>
              <div class="field-group">
                <label class="field-label">Particle</label>
                <select class="field-input" id="csIdleParticle">
                  ${['HAPPY_VILLAGER','FLAME','ENCHANT','SOUL_FIRE_FLAME','DRAGON_BREATH','END_ROD','WITCH','GLOW','FIREWORK','TOTEM_OF_UNDYING','SCRAPE'].map(p => `<option value="${p}" ${(crate.idleAnimation?.particle||'HAPPY_VILLAGER')===p?'selected':''}>${p}</option>`).join('')}
                </select>
              </div>
            </div>
          </div>
          <div>
            <div class="section-label" style="margin-bottom:8px">OPEN ANIMATION</div>
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">Type</label>
                <select class="field-input" id="csOpenType">
                  ${['RING','HELIX','SPHERE','SPIRAL','RAIN','NONE'].map(t => `<option value="${t}" ${(crate.openAnimation?.type||'RING')===t?'selected':''}>${t}</option>`).join('')}
                </select>
              </div>
              <div class="field-group">
                <label class="field-label">Particle</label>
                <select class="field-input" id="csOpenParticle">
                  ${['FIREWORK','FLAME','ENCHANT','SOUL_FIRE_FLAME','DRAGON_BREATH','END_ROD','WITCH','GLOW','HAPPY_VILLAGER','TOTEM_OF_UNDYING'].map(p => `<option value="${p}" ${(crate.openAnimation?.particle||'FIREWORK')===p?'selected':''}>${p}</option>`).join('')}
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="CrateSettingsModal.save()">✓ Save Settings</button>
      </div>
    `, 'modal-lg');

    Utils.qs('#csEnabledToggle').appendChild(ToggleSwitch('Crate Enabled', crate.enabled !== false, v => { crate.enabled = v; }));
    Utils.qs('#csMassToggle').appendChild(ToggleSwitch('Mass Open Enabled', crate.massOpenEnabled !== false, v => { crate.massOpenEnabled = v; }));
    this._crate = crate; this._onSave = onSave;
  },

  save() {
    const c = this._crate;
    c.id            = Utils.qs('#csId').value.trim();
    c.displayName   = Utils.qs('#csName').value;
    c.cooldownMs    = parseInt(Utils.qs('#csCooldown').value) || 0;
    c.massOpenLimit = parseInt(Utils.qs('#csMassLimit').value) || -1;
    if (!c.idleAnimation) c.idleAnimation = {};
    if (!c.openAnimation) c.openAnimation = {};
    c.idleAnimation.type     = Utils.qs('#csIdleType').value;
    c.idleAnimation.particle = Utils.qs('#csIdleParticle').value;
    c.openAnimation.type     = Utils.qs('#csOpenType').value;
    c.openAnimation.particle = Utils.qs('#csOpenParticle').value;
    this._onSave?.();
    Modal.close();
    toast('Crate settings saved ✓', 'success');
  },
};

/* ══════════════════════════════════════════════════════
   PITY SYSTEM MODAL
══════════════════════════════════════════════════════ */
const PityModal = {
  open(crate, onSave) {
    if (!crate.pity) crate.pity = { enabled: false, threshold: 100, softPityStart: 80, rareRarityMinimum: 'RARE', bonusChancePerOpen: 2 };
    const pity = crate.pity;

    const pityRarityOptions = State.rarities.filter((_, i) => i >= 1)
      .map(r => `<option value="${r.id}" ${pity.rareRarityMinimum===r.id?'selected':''}>${r.icon} ${r.displayName}</option>`).join('');

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">🎯 Pity System</div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName || crate.id)}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div id="pityEnabledToggle" style="margin-bottom:14px;padding-bottom:14px;border-bottom:1px solid var(--border)"></div>
        <div id="pityFields" style="${pity.enabled ? '' : 'opacity:.4;pointer-events:none'}">
          <div class="field-row">
            <div class="field-group">
              <label class="field-label">Hard Pity Threshold</label>
              <input class="field-input" type="number" id="pmMax" value="${pity.threshold || 100}" min="1" max="1000"/>
              <div style="font-size:10.5px;color:var(--text3);margin-top:3px">Guaranteed rare after N opens.</div>
            </div>
            <div class="field-group">
              <label class="field-label">Soft Pity Start</label>
              <input class="field-input" type="number" id="pmSoft" value="${pity.softPityStart || 80}" min="1"/>
              <div style="font-size:10.5px;color:var(--text3);margin-top:3px">Bonus chance starts increasing here.</div>
            </div>
          </div>
          <div class="field-row" style="margin-top:10px">
            <div class="field-group">
              <label class="field-label">Minimum Rarity (Pity)</label>
              <select class="field-input" id="pmRarity">${pityRarityOptions}</select>
            </div>
            <div class="field-group">
              <label class="field-label">Bonus Chance / Open (%)</label>
              <input class="field-input" type="number" id="pmBonus" value="${pity.bonusChancePerOpen || 2}" min="0.1" step="0.1"/>
            </div>
          </div>
          <div style="margin-top:14px;padding:12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm)">
            <div style="display:flex;justify-content:space-between;font-size:11px;margin-bottom:6px">
              <span style="color:var(--text2)">Pity Bar Preview</span>
              <span style="color:var(--cyan);font-weight:700">0 / ${pity.threshold || 100}</span>
            </div>
            <div style="height:8px;background:var(--bg2);border-radius:4px;overflow:hidden;position:relative">
              <div style="width:0%;height:100%;background:linear-gradient(90deg,var(--cyan),var(--gold));border-radius:4px"></div>
              <div style="position:absolute;top:0;bottom:0;left:${(pity.softPityStart||80)/(pity.threshold||100)*100}%;width:2px;background:rgba(255,255,255,.3)"></div>
            </div>
            <div style="font-size:10px;color:var(--text3);margin-top:4px">Soft pity starts at ${pity.softPityStart || 80} opens</div>
          </div>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="PityModal.save()">✓ Save Pity Config</button>
      </div>
    `, 'modal-md');

    const fields = Utils.qs('#pityFields');
    Utils.qs('#pityEnabledToggle').appendChild(ToggleSwitch('Enable Pity System', pity.enabled, v => {
      pity.enabled = v;
      fields.style.opacity = v ? '1' : '.4';
      fields.style.pointerEvents = v ? '' : 'none';
    }));
    this._crate = crate; this._onSave = onSave;
  },

  save() {
    const pity = this._crate.pity;
    pity.threshold          = parseInt(Utils.qs('#pmMax').value) || 100;
    pity.softPityStart      = parseInt(Utils.qs('#pmSoft').value) || 80;
    pity.rareRarityMinimum  = Utils.qs('#pmRarity').value;
    pity.bonusChancePerOpen = parseFloat(Utils.qs('#pmBonus').value) || 2;
    this._onSave?.();
    Modal.close();
    toast('Pity system updated ✓', 'success');
  },
};

/* ══════════════════════════════════════════════════════
   KEY REQUIREMENTS MODAL
══════════════════════════════════════════════════════ */
const KeyReqModal = {
  open(crate, onSave) {
    if (!crate.requiredKeys) crate.requiredKeys = [];
    this._crate = crate; this._onSave = onSave;

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">🔑 Key Requirements</div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName || crate.id)}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div id="keyReqList" style="display:flex;flex-direction:column;gap:8px"></div>
        <button class="btn btn-ghost btn-sm" style="margin-top:10px;width:100%;justify-content:center" onclick="KeyReqModal.addKey()">
          + Add Key Requirement
        </button>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="KeyReqModal.save()">✓ Save Keys</button>
      </div>
    `, 'modal-md');

    this.renderList();
  },

  renderList() {
    const list = Utils.qs('#keyReqList'); if (!list) return;
    const crate = this._crate;
    list.innerHTML = '';
    if (!crate.requiredKeys.length) {
      list.innerHTML = '<div style="color:var(--text3);font-size:12px;text-align:center;padding:16px 0">No key requirements. Click below to add one.</div>';
      return;
    }
    crate.requiredKeys.forEach((k, i) => {
      const row = Utils.el('div');
      row.style.cssText = 'display:flex;gap:8px;align-items:flex-end;padding:10px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm)';
      row.innerHTML = `
        <div class="field-group" style="flex:2">
          <label class="field-label">Key ID</label>
          <input class="field-input" value="${k.keyId}" placeholder="legendary_key" data-idx="${i}" data-field="keyId"/>
        </div>
        <div class="field-group" style="flex:0 0 70px">
          <label class="field-label">Amount</label>
          <input class="field-input" type="number" value="${k.amount || 1}" min="1" data-idx="${i}" data-field="amount"/>
        </div>
        <div class="field-group" style="flex:1.5">
          <label class="field-label">Source</label>
          <select class="field-input" data-idx="${i}" data-field="type">
            ${['VIRTUAL','PHYSICAL','MMOITEMS','ITEMSADDER','ORAXEN'].map(t => `<option value="${t}" ${k.type===t?'selected':''}>${t}</option>`).join('')}
          </select>
        </div>
        <button class="btn btn-danger btn-xs" style="flex-shrink:0;margin-bottom:1px" data-remove="${i}">✕</button>
      `;
      row.querySelectorAll('[data-field]').forEach(el => {
        el.onchange = () => { crate.requiredKeys[parseInt(el.dataset.idx)][el.dataset.field] = el.dataset.field === 'amount' ? parseInt(el.value) : el.value; };
      });
      row.querySelector('[data-remove]').onclick = () => { crate.requiredKeys.splice(i, 1); this.renderList(); };
      list.appendChild(row);
    });
  },

  addKey() { this._crate.requiredKeys.push({ keyId: '', amount: 1, type: 'VIRTUAL' }); this.renderList(); },
  save()   { this._onSave?.(); Modal.close(); toast('Key requirements updated ✓', 'success'); },
};

/* ══ RARITY EDITOR MODAL ══ */
const RarityEditor = {
  draft: [],
  open() { this.draft = State.rarities.map(r => ({ ...r })); this._render(); },

  /* ── Minecraft color palette — closest match ── */
  MC_COLORS: [
    { code:'&0', hex:'#000000' },{ code:'&1', hex:'#0000aa' },
    { code:'&2', hex:'#00aa00' },{ code:'&3', hex:'#00aaaa' },
    { code:'&4', hex:'#aa0000' },{ code:'&5', hex:'#aa00aa' },
    { code:'&6', hex:'#ffaa00' },{ code:'&7', hex:'#aaaaaa' },
    { code:'&8', hex:'#555555' },{ code:'&9', hex:'#5555ff' },
    { code:'&a', hex:'#55ff55' },{ code:'&b', hex:'#55ffff' },
    { code:'&c', hex:'#ff5555' },{ code:'&d', hex:'#ff55ff' },
    { code:'&e', hex:'#ffff55' },{ code:'&f', hex:'#ffffff' },
  ],

  /**
   * Convert a hex color to the closest Minecraft & color code.
   * Uses Euclidean distance in RGB space.
   */
  _colorToMinecraft(hex) {
    if (!hex || !hex.startsWith('#')) return '&f';
    const r1 = parseInt(hex.slice(1,3),16)||0;
    const g1 = parseInt(hex.slice(3,5),16)||0;
    const b1 = parseInt(hex.slice(5,7),16)||0;
    let best = '&f', bestDist = Infinity;
    for (const { code, hex: mh } of this.MC_COLORS) {
      const r2 = parseInt(mh.slice(1,3),16);
      const g2 = parseInt(mh.slice(3,5),16);
      const b2 = parseInt(mh.slice(5,7),16);
      const dist = (r1-r2)**2 + (g1-g2)**2 + (b1-b2)**2;
      if (dist < bestDist) { bestDist = dist; best = code; }
    }
    return best;
  },

  _render() {
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            Rarity Editor
          </div>
          <div class="modal-subtitle">
            Add, remove, or recolor rarity tiers. Minecraft & color is auto-picked from hex.
            Changes sync to rarities.yml instantly.
          </div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body" style="padding-bottom:8px">
        <div style="display:grid;grid-template-columns:28px 1fr 110px 80px 60px 36px;gap:6px;align-items:center;
          padding:0 4px 6px;border-bottom:1px solid var(--border);margin-bottom:8px;
          font-size:9.5px;font-weight:700;color:var(--text3);letter-spacing:.7px;text-transform:uppercase">
          <span></span><span>Display Name</span><span>Hex Color</span><span>Order</span><span>Icon</span><span></span>
        </div>
        <div id="rarityRows" style="display:flex;flex-direction:column;gap:6px">
          ${this.draft.map((r, i) => this._rowHtml(r, i)).join('')}
        </div>
        <button class="btn btn-ghost btn-sm" style="margin-top:10px;width:100%;justify-content:center"
          onclick="RarityEditor.addRow()">+ Add Custom Rarity</button>

        <!-- Color code legend -->
        <div style="margin-top:12px;padding:10px 12px;background:var(--bg3);border:1px solid var(--border);
          border-radius:var(--radius-sm);font-size:11px;color:var(--text3);line-height:1.7">
          <div style="color:var(--text2);font-weight:600;margin-bottom:4px">Notes:</div>
          <div>• <strong>Minecraft & color</strong> is auto-derived from the hex color you pick — the closest match from the 16 standard colors.</div>
          <div>• The & code is used in-game (item names, lore, holograms). The hex color is for the web dashboard.</div>
          <div>• <strong>Order</strong> determines tier level — 0 = lowest (most common), higher = rarer. Used by the pity system.</div>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-ghost btn-sm" onclick="RarityEditor.reload()">↻ Reload from File</button>
        <button class="btn btn-primary" onclick="RarityEditor.save()">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/>
            <polyline points="17 21 17 13 7 13 7 21"/>
            <polyline points="7 3 7 8 15 8"/>
          </svg>
          Save Rarities
        </button>
      </div>
    `, 'modal-lg');
  },

  _rowHtml(r, i) {
    // Auto-derive MC color from hex on render
    const mcColor = r.color || this._colorToMinecraft(r.hexColor);
    return `
      <div class="rarity-editor-row" data-idx="${i}"
        style="display:grid;grid-template-columns:28px 1fr 110px 80px 60px 36px;gap:6px;align-items:center;
          padding:6px 4px;border-radius:var(--radius-sm);border:1px solid var(--border);background:var(--bg3)">

        <!-- Color swatch — click to open color picker -->
        <div style="width:24px;height:24px;border-radius:6px;background:${r.hexColor};border:2px solid ${r.hexColor}40;
          flex-shrink:0;cursor:pointer;position:relative;overflow:hidden"
          title="Click to pick color" id="rSwatchWrap${i}"
          onclick="document.getElementById('rClrPick${i}').click()">
          <input type="color" id="rClrPick${i}" value="${r.hexColor}"
            style="position:absolute;opacity:0;width:0;height:0"
            oninput="RarityEditor._onColorPick(${i}, this.value)"/>
        </div>

        <!-- Display name -->
        <input class="field-input" style="padding:5px 8px;font-size:12px"
          value="${r.displayName}" placeholder="e.g. Legendary"
          oninput="RarityEditor.update(${i},'displayName',this.value)"/>

        <!-- Hex color text input -->
        <input class="field-input" style="padding:5px 8px;font-size:11px;font-family:monospace"
          id="rHexIn${i}" value="${r.hexColor}" placeholder="#aaaaaa"
          oninput="RarityEditor._onHexInput(${i}, this.value)"/>

        <!-- Order -->
        <input class="field-input" type="number" style="padding:5px 8px;font-size:12px"
          value="${r.order}" min="0" max="99"
          oninput="RarityEditor.update(${i},'order',parseInt(this.value)||0)"/>

        <!-- Icon/emoji -->
        <input class="field-input" style="padding:5px 8px;font-size:16px;text-align:center"
          value="${r.icon || '⬜'}" maxlength="4"
          oninput="RarityEditor.update(${i},'icon',this.value)"/>

        <!-- Remove -->
        <button class="btn btn-danger btn-xs" style="padding:5px 7px"
          onclick="RarityEditor.removeRow(${i})">✕</button>
      </div>
    `;
  },

  /** Called when color picker value changes */
  _onColorPick(idx, hex) {
    this.update(idx, 'hexColor', hex);
    this.update(idx, 'color', this._colorToMinecraft(hex));
    // Update swatch visual
    const swatch = Utils.qs(`#rSwatchWrap${idx}`);
    if (swatch) { swatch.style.background = hex; swatch.style.borderColor = hex + '40'; }
    // Update hex text input
    const hexIn = Utils.qs(`#rHexIn${idx}`);
    if (hexIn) hexIn.value = hex;
  },

  /** Called when hex text input changes */
  _onHexInput(idx, val) {
    if (!/^#[0-9a-f]{6}$/i.test(val)) return; // Only apply when valid 6-digit hex
    this.update(idx, 'hexColor', val);
    this.update(idx, 'color', this._colorToMinecraft(val));
    const swatch = Utils.qs(`#rSwatchWrap${idx}`);
    if (swatch) { swatch.style.background = val; swatch.style.borderColor = val + '40'; }
    const picker = Utils.qs(`#rClrPick${idx}`);
    if (picker) picker.value = val;
  },

  update(idx, field, value) {
    if (!this.draft[idx]) return;
    this.draft[idx][field] = value;
    if (field === 'displayName') {
      this.draft[idx].id = value.toUpperCase()
        .replace(/\s+/g,'_').replace(/[^A-Z0-9_]/g,'') || 'CUSTOM';
    }
  },

  addRow() {
    const nextOrder = this.draft.length > 0
      ? Math.max(...this.draft.map(r => r.order)) + 1 : 0;
    const newRarity = {
      id:'CUSTOM_'+nextOrder, displayName:'Custom', color:'&f',
      hexColor:'#ffffff', order:nextOrder,
      borderMaterial:'WHITE_STAINED_GLASS_PANE', icon:'✨'
    };
    this.draft.push(newRarity);
    this._render();
  },

  removeRow(idx) {
    if (this.draft.length <= 1) { toast('Must have at least one rarity!', 'error'); return; }
    this.draft.splice(idx, 1);
    this._render();
  },

  async save() {
    // Validate
    for (const r of this.draft) {
      if (!r.displayName?.trim()) { toast('All rarities must have a Display Name', 'error'); return; }
      if (!r.id?.trim()) {
        r.id = r.displayName.toUpperCase().replace(/\s+/g,'_').replace(/[^A-Z0-9_]/g,'');
      }
      // Ensure color (& code) is always set — auto-derive if missing
      if (!r.color || !r.color.startsWith('&')) {
        r.color = this._colorToMinecraft(r.hexColor);
      }
    }
    try {
      await API.post('/rarities', { rarities: this.draft });
      toast('Rarities saved ✓', 'success');
      State.setRarities(this.draft);
      Modal.close();
      const el = document.getElementById('page-architect');
      if (el) Architect.render(el);
    } catch (e) { toast(e.message, 'error'); }
  },

  async reload() {
    try {
      await API.post('/rarities/reload');
      toast('Rarities reloaded from rarities.yml', 'info');
      const data = await API.get('/rarities');
      State.setRarities(data.data || []);
      Modal.close();
      const el = document.getElementById('page-architect');
      if (el) Architect.render(el);
    } catch (e) { toast(e.message, 'error'); }
  },
};