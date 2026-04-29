/* ══ PAGE: CRATE ARCHITECT ══ */
const Architect = {
  dirty: false,

  render(container) {
    container.innerHTML = `
      <div class="page-header">
        <div class="page-header-inner">
          <div>
            <div class="page-title">Crate Architect</div>
            <div class="page-sub">Design and configure the ultimate loot experience.</div>
          </div>
          <div class="page-actions">
            <button class="btn btn-ghost btn-sm" id="btnRarityEditor" style="color:#fff;border-color:rgba(255,255,255,.35)">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              Rarities
            </button>
            <button class="btn btn-danger btn-sm" id="btnDiscardCrate" style="background:rgba(192,57,43,.2);border-color:rgba(192,57,43,.4);color:#ffb3aa">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 14 4 9 9 4"/><path d="M20 20v-7a4 4 0 00-4-4H4"/></svg>
              Discard
            </button>
            <button class="btn btn-primary btn-sm" id="btnSaveCrate" style="background:rgba(255,255,255,.2);border:1px solid rgba(255,255,255,.4);color:#fff">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
              Save Crate
            </button>
          </div>
        </div>
      </div>

      <div class="crate-selector-bar" id="crateTabs"></div>

      <div style="display:flex;flex-direction:column;gap:12px" id="architectMain">
        <div class="card" id="rewardsCard">
          <div class="card-header">
            <div class="card-title"><span class="card-accent"></span>REWARDS <span class="card-sub">Loot Table</span></div>
            <button class="btn btn-ghost btn-sm" id="btnAddReward">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              Add Reward
            </button>
          </div>
          <div class="rewards-grid" id="rewardsGrid"></div>
        </div>

        <div class="card arch-config-card" id="weightCard" onclick="Architect.openWeightModal()" style="cursor:pointer">
          <div style="display:flex;align-items:center;gap:12px">
            <div style="width:36px;height:36px;border-radius:8px;background:var(--cyan-dim);border:1px solid rgba(26,122,74,.2);display:flex;align-items:center;justify-content:center;flex-shrink:0">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>
            </div>
            <div style="flex:1;min-width:0">
              <div style="font-size:12.5px;font-weight:700;color:var(--text)">Chance Management</div>
              <div style="font-size:10.5px;color:var(--text3);margin-top:2px" id="weightSummary">Configure reward weights and drop chances</div>
            </div>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--text3)" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:9px" id="crateConfigCards"></div>
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
    add.innerHTML = `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> New Crate`;
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

  renderWeightSummary() {
    const el    = Utils.qs('#weightSummary'); if (!el) return;
    const crate = State.currentCrate;         if (!crate) return;
    const count = crate.rewards?.length || 0;
    const tw    = Utils.totalWeight(crate.rewards);
    if (!count) { el.textContent = 'No rewards yet — add one above'; return; }
    el.textContent = `${count} rewards · Total weight ${tw.toFixed(1)}`;
  },

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
        return (sch.daysOfWeek||[]).map(d => dayNames[d-1]).join(', ') || 'Select days';
      }
      if (sch.mode === 'EVENT') {
        const now = Date.now();
        if (sch.eventEnd && now > sch.eventEnd) return 'Event ended';
        if (sch.eventStart && now < sch.eventStart) return 'Event not started';
        return 'Event LIVE';
      }
      return sch.mode;
    };

    const hasSchedule = sch && sch.mode && sch.mode !== 'ALWAYS';

    const cfgSvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>`;
    const pitySvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>`;
    const schedSvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`;
    const keySvg  = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 11-7.778 7.778 5.5 5.5 0 017.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>`;
    const holoSvg = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>`;
    const delSvg  = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/></svg>`;

    const cards = [
      { svg: cfgSvg,   label: 'Crate Settings', sub: `${crate.enabled !== false ? 'Enabled' : 'Disabled'} · ${crate.cooldownMs ? Utils.duration(crate.cooldownMs)+' cd' : 'No cooldown'}`, color: 'var(--cyan)', bg: 'var(--cyan-dim)', border: 'rgba(26,122,74,.2)', onclick: () => CrateSettingsModal.open(crate, () => { Architect.dirty = true; Architect.renderCrateTabs(); Architect.renderConfigCards(); }) },
      { svg: pitySvg,  label: 'Pity System', sub: pity.enabled ? `Active · ${pity.threshold||100} opens` : 'Disabled', color: pity.enabled ? 'var(--gold)' : 'var(--text3)', bg: pity.enabled ? 'var(--gold-dim)' : 'var(--bg3)', border: pity.enabled ? 'rgba(176,125,26,.25)' : 'var(--border)', onclick: () => PityModal.open(crate, () => { Architect.dirty = true; Architect.renderConfigCards(); }) },
      { svg: schedSvg, label: 'Schedule', sub: schedDesc(), color: hasSchedule ? 'var(--purple)' : 'var(--text3)', bg: hasSchedule ? 'var(--purple-dim)' : 'var(--bg3)', border: hasSchedule ? 'rgba(107,70,193,.25)' : 'var(--border)', onclick: () => ScheduleModal.open(crate, () => { Architect.dirty = true; Architect.renderConfigCards(); }) },
      { svg: keySvg,   label: 'Key Requirements', sub: keyCount ? `${keyCount} key type${keyCount>1?'s':''} required` : 'None configured', color: 'var(--blue)', bg: 'var(--blue-dim)', border: 'rgba(26,86,160,.25)', onclick: () => KeyReqModal.open(crate, () => { Architect.dirty = true; Architect.renderConfigCards(); }) },
      { svg: holoSvg,  label: 'Hologram', sub: (crate.hologramLines?.length||0)+' lines', color: 'var(--green)', bg: 'var(--green-dim)', border: 'rgba(26,122,74,.25)', onclick: () => HologramModal.open() },
      { svg: delSvg,   label: 'Delete Crate', sub: 'Permanently remove', color: 'var(--red)', bg: 'var(--red-dim)', border: 'rgba(192,57,43,.25)', onclick: () => Architect.confirmDeleteCrate(crate.id) },
    ];

    cards.forEach(({ svg, label, sub, color, bg, border, onclick }) => {
      const card = Utils.el('div', 'arch-config-card');
      card.style.cssText = `cursor:pointer;background:var(--bg2);border:1px solid var(--border);border-radius:var(--radius);padding:14px;transition:all .18s var(--ease)`;
      card.innerHTML = `
        <div style="width:34px;height:34px;border-radius:8px;background:${bg};border:1px solid ${border};display:flex;align-items:center;justify-content:center;margin-bottom:10px;color:${color}">${svg}</div>
        <div style="font-size:12px;font-weight:700;color:var(--text);margin-bottom:3px">${label}</div>
        <div style="font-size:10.5px;color:${color};line-height:1.4">${sub}</div>
      `;
      card.onmouseenter = () => { card.style.borderColor = border; card.style.transform = 'translateY(-2px)'; card.style.boxShadow = 'var(--shadow)'; };
      card.onmouseleave = () => { card.style.borderColor = 'var(--border)'; card.style.transform = ''; card.style.boxShadow = ''; };
      card.onclick = onclick;
      grid.appendChild(card);
    });
  },

  openWeightModal() {
    const crate = State.currentCrate; if (!crate) return;
    if (!crate.rewards?.length) { toast('No rewards to configure — add rewards first', 'info'); return; }

    const renderRows = () => {
      const list  = Utils.qs('#wmList'); if (!list) return;
      const sorted = this._sortedRewards(crate.rewards);
      list.innerHTML = '';

      const hdr = Utils.el('div');
      hdr.style.cssText = 'display:grid;grid-template-columns:1fr 120px 80px 80px;gap:8px;padding:5px 4px;font-size:9px;font-weight:700;color:var(--text3);letter-spacing:.7px;text-transform:uppercase;border-bottom:1px solid var(--border);margin-bottom:5px';
      hdr.innerHTML = '<span>Reward</span><span>Weight</span><span>Chance</span><span>Rarity</span>';
      list.appendChild(hdr);

      sorted.forEach(r => {
        const tw = Utils.totalWeight(crate.rewards);
        const pct = Utils.chance(r.weight, tw);
        const color = Utils.rarityColor(r.rarity);
        const row = SliderRow(r, tw, () => { this.dirty = true; this.renderWeightSummary(); });
        list.appendChild(row);
      });

      const tw2 = Utils.totalWeight(crate.rewards);
      const totRow = Utils.el('div');
      totRow.style.cssText = 'display:flex;justify-content:space-between;padding:7px 4px 0;border-top:1px solid var(--border);margin-top:4px;font-size:11px';
      totRow.innerHTML = `<span style="color:var(--text3)">Total Weight</span><span style="font-weight:700;color:var(--cyan)">${tw2.toFixed(1)}</span>`;
      list.appendChild(totRow);
    };

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>
            Chance Management
          </div>
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
      <div class="modal-body"><div id="wmList"></div></div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Close</button>
      </div>
    `, 'modal-lg');

    renderRows();
    this._wmRenderFn = renderRows;
  },

  _wmSort() {
    const sel = Utils.qs('#wmSort');
    if (sel) { const crate = State.currentCrate; if (crate?.preview) crate.preview.sortOrder = sel.value; }
    this._wmRenderFn?.();
  },

  confirmDeleteCrate(id) {
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title" style="color:var(--red)">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/></svg>
            Delete Crate
          </div>
          <div class="modal-subtitle">This action cannot be undone.</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div style="padding:14px;background:var(--red-dim);border:1px solid rgba(192,57,43,.2);border-radius:var(--radius-sm);font-size:12.5px;line-height:1.7">
          <div style="font-weight:700;color:var(--red);margin-bottom:5px">Delete this crate?</div>
          <div style="color:var(--text2)">Crate <code style="color:var(--cyan);background:var(--bg3);padding:1px 5px;border-radius:3px">${id}</code> will be permanently removed.</div>
        </div>
        <div style="margin-top:12px">
          <label class="field-label" style="margin-bottom:5px;display:block">Type the crate ID to confirm:</label>
          <input class="field-input" id="deleteConfirmInput" placeholder="${id}"/>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-danger" id="btnConfirmDelete" disabled onclick="Architect._doDelete('${id}')">Delete Permanently</button>
      </div>
    `, 'modal-md');
    Utils.qs('#deleteConfirmInput').oninput = function() { Utils.qs('#btnConfirmDelete').disabled = this.value !== id; };
  },

  async _doDelete(id) {
    try {
      await API.deleteCrate(id);
      State.deleteCrate(id);
      Modal.close();
      toast(`Crate "${id}" deleted.`, 'success');
      if (State.crateOrder.length > 0) { this.loadCrate(State.crateOrder[0]); }
      else {
        State.currentCrateId = null;
        this.renderCrateTabs();
        const main = Utils.qs('#architectMain');
        if (main) main.innerHTML = '<div class="empty-state" style="padding:60px"><p>No crates yet. Create one to get started.</p></div>';
      }
    } catch (e) { toast(e.message, 'error'); }
  },

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

  async save() {
    const crate = State.currentCrate; if (!crate) return;
    const btn = Utils.qs('#btnSaveCrate');
    btn.disabled = true;
    btn.innerHTML = '<svg class="spin" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 00-9-9"/></svg> Saving...';
    try {
      await API.saveCrate(crate.id, crate);
      State.setCrate(crate);
      this.dirty = false;
      toast('Crate saved', 'success');
    } catch (e) { toast(e.message, 'error'); }
    finally {
      btn.disabled = false;
      btn.innerHTML = `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> Save Crate`;
    }
  },

  discard() {
    if (!this.dirty) { toast('Nothing to discard', 'info'); return; }
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left"><div class="modal-title">Discard Changes?</div><div class="modal-subtitle">All unsaved changes will be lost.</div></div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div style="padding:12px;background:var(--gold-dim);border:1px solid rgba(176,125,26,.2);border-radius:var(--radius-sm);font-size:12.5px;color:var(--text2)">Are you sure you want to discard all unsaved changes?</div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="Architect._doDiscard()">Discard</button>
      </div>
    `, 'modal-sm');
  },

  _doDiscard() { Modal.close(); this.dirty = false; this.loadCrate(State.currentCrateId); toast('Changes discarded', 'info'); },

  newCrate() {
    const id    = 'new_crate_' + Date.now();
    const midId = State.rarities[Math.floor(State.rarities.length / 2)]?.id || 'RARE';
    const crate = { id, displayName: '&fNew Crate', enabled: true, cooldownMs: 0, massOpenEnabled: true, massOpenLimit: 64, requiredKeys: [{ keyId: 'example_key', amount: 1, type: 'VIRTUAL' }], rewards: [], pity: { enabled: false, threshold: 100, softPityStart: 80, rareRarityMinimum: midId, bonusChancePerOpen: 2 }, preview: { sortOrder: 'RARITY_DESC', showChance: true, showPity: true, showKeyBalance: true, showActualItem: true } };
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
    return rewards.reduce((h, r) => Utils.rarityOrder(r.rarity) > Utils.rarityOrder(h) ? r.rarity : h, State.rarities[0]?.id || 'COMMON');
  },
};

/* ══ CRATE SETTINGS MODAL ══ */
const CrateSettingsModal = {
  open(crate, onSave) {
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
            Crate Settings
          </div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName || crate.id)}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div style="display:flex;flex-direction:column;gap:10px">
          <div class="field-row">
            <div class="field-group"><label class="field-label">Crate ID</label><input class="field-input" id="csId" value="${crate.id||''}" placeholder="legendary_crate"/></div>
            <div class="field-group"><label class="field-label">Display Name</label><input class="field-input" id="csName" value="${crate.displayName||''}" placeholder="&6Legendary Crate"/></div>
          </div>
          <div class="field-row">
            <div class="field-group"><label class="field-label">Cooldown</label><select class="field-input" id="csCooldown"><option value="0" ${!crate.cooldownMs?'selected':''}>No Cooldown</option><option value="300000" ${crate.cooldownMs===300000?'selected':''}>5 Minutes</option><option value="1800000" ${crate.cooldownMs===1800000?'selected':''}>30 Minutes</option><option value="3600000" ${crate.cooldownMs===3600000?'selected':''}>1 Hour</option><option value="86400000" ${crate.cooldownMs===86400000?'selected':''}>24 Hours</option></select></div>
            <div class="field-group"><label class="field-label">Mass Open Limit</label><input class="field-input" type="number" id="csMassLimit" value="${crate.massOpenLimit??64}" min="-1"/></div>
          </div>
          <div style="display:flex;gap:10px">
            <div style="flex:1" id="csEnabledToggle"></div>
            <div style="flex:1" id="csMassToggle"></div>
          </div>

          <div style="border-top:1px solid var(--border);padding-top:10px">
            <div class="section-label" style="margin-bottom:7px">IDLE PARTICLES</div>
            <div class="field-row" style="margin-bottom:7px">
              <div class="field-group"><label class="field-label">Type</label>
                <select class="field-input" id="csIdleType">${['RING','HELIX','SPHERE','SPIRAL','RAIN','NONE'].map(t=>`<option value="${t}" ${(crate.idleAnimation?.type||'RING')===t?'selected':''}>${t}</option>`).join('')}</select>
              </div>
              <div class="field-group"><label class="field-label">Particle</label>
                <select class="field-input" id="csIdleParticle">${['HAPPY_VILLAGER','FLAME','ENCHANT','END_ROD','WITCH','TOTEM_OF_UNDYING','DRAGON_BREATH','SOUL_FIRE_FLAME','CRIMSON_SPORE','GLOW','SNOWFLAKE'].map(p=>`<option value="${p}" ${(crate.idleAnimation?.particle||'HAPPY_VILLAGER')===p?'selected':''}>${p}</option>`).join('')}</select>
              </div>
            </div>
            <div class="field-row-3">
              <div class="field-group"><label class="field-label">Speed</label><input class="field-input" type="number" id="csIdleSpeed" value="${crate.idleAnimation?.speed??1.0}" min="0.1" max="10" step="0.1"/></div>
              <div class="field-group"><label class="field-label">Radius</label><input class="field-input" type="number" id="csIdleRadius" value="${crate.idleAnimation?.radius??1.0}" min="0.1" max="5" step="0.1"/></div>
              <div class="field-group"><label class="field-label">Density</label><input class="field-input" type="number" id="csIdleDensity" value="${crate.idleAnimation?.density??8}" min="1" max="32" step="1"/></div>
            </div>
          </div>

          <div style="border-top:1px solid var(--border);padding-top:10px">
            <div class="section-label" style="margin-bottom:7px">OPEN PARTICLES</div>
            <div class="field-row" style="margin-bottom:7px">
              <div class="field-group"><label class="field-label">Type</label>
                <select class="field-input" id="csOpenType">${['RING','HELIX','SPHERE','SPIRAL','RAIN','NONE'].map(t=>`<option value="${t}" ${(crate.openAnimation?.type||'RING')===t?'selected':''}>${t}</option>`).join('')}</select>
              </div>
              <div class="field-group"><label class="field-label">Particle</label>
                <select class="field-input" id="csOpenParticle">${['HAPPY_VILLAGER','FLAME','ENCHANT','END_ROD','WITCH','TOTEM_OF_UNDYING','DRAGON_BREATH','SOUL_FIRE_FLAME','CRIMSON_SPORE','GLOW','SNOWFLAKE'].map(p=>`<option value="${p}" ${(crate.openAnimation?.particle||'HAPPY_VILLAGER')===p?'selected':''}>${p}</option>`).join('')}</select>
              </div>
            </div>
            <div class="field-row-3">
              <div class="field-group"><label class="field-label">Speed</label><input class="field-input" type="number" id="csOpenSpeed" value="${crate.openAnimation?.speed??1.0}" min="0.1" max="10" step="0.1"/></div>
              <div class="field-group"><label class="field-label">Radius</label><input class="field-input" type="number" id="csOpenRadius" value="${crate.openAnimation?.radius??1.0}" min="0.1" max="5" step="0.1"/></div>
              <div class="field-group"><label class="field-label">Density</label><input class="field-input" type="number" id="csOpenDensity" value="${crate.openAnimation?.density??8}" min="1" max="32" step="1"/></div>
            </div>
          </div>

          <div style="border-top:1px solid var(--border);padding-top:10px">
            <div class="section-label" style="margin-bottom:7px">GUI ANIMATION</div>
            <div class="field-group">
              <label class="field-label">Opening Animation Style</label>
              <select class="field-input" id="csGuiAnimation">${['ROULETTE','SHUFFLER','BOUNDARY','TRIPLE_SPIN','FLICKER'].map(t=>`<option value="${t}" ${(crate.guiAnimation||'ROULETTE')===t?'selected':''}>${t.replace(/_/g,' ')}</option>`).join('')}</select>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="CrateSettingsModal.save()">Save Settings</button>
      </div>
    `, 'modal-lg');
    Utils.qs('#csEnabledToggle').appendChild(ToggleSwitch('Crate Enabled', crate.enabled !== false, v => { crate.enabled = v; }));
    Utils.qs('#csMassToggle').appendChild(ToggleSwitch('Mass Open Enabled', crate.massOpenEnabled !== false, v => { crate.massOpenEnabled = v; }));
    this._crate = crate;
    this._onSave = onSave;
  },

  save() {
    const c = this._crate;
    c.id            = Utils.qs('#csId').value.trim();
    c.displayName   = Utils.qs('#csName').value;
    c.cooldownMs    = parseInt(Utils.qs('#csCooldown').value) || 0;
    c.massOpenLimit = parseInt(Utils.qs('#csMassLimit').value) ?? -1;
    c.idleAnimation = {
      type:     Utils.qs('#csIdleType').value,
      particle: Utils.qs('#csIdleParticle').value,
      speed:    parseFloat(Utils.qs('#csIdleSpeed').value)  || 1.0,
      radius:   parseFloat(Utils.qs('#csIdleRadius').value) || 1.0,
      density:  parseInt(Utils.qs('#csIdleDensity').value)  || 8,
    };
    c.openAnimation = {
      type:     Utils.qs('#csOpenType').value,
      particle: Utils.qs('#csOpenParticle').value,
      speed:    parseFloat(Utils.qs('#csOpenSpeed').value)  || 1.0,
      radius:   parseFloat(Utils.qs('#csOpenRadius').value) || 1.0,
      density:  parseInt(Utils.qs('#csOpenDensity').value)  || 8,
    };
    c.guiAnimation = Utils.qs('#csGuiAnimation').value;
    this._onSave?.();
    Modal.close();
    toast('Crate settings saved', 'success');
  },
};

/* ══ PITY MODAL ══ */
const PityModal = {
  open(crate, onSave) {
    if (!crate.pity) crate.pity = { enabled: false, threshold: 100, softPityStart: 80, rareRarityMinimum: 'RARE', bonusChancePerOpen: 2 };
    const pity = crate.pity;
    const pityRarityOptions = State.rarities.filter((_,i)=>i>=1).map(r=>`<option value="${r.id}" ${pity.rareRarityMinimum===r.id?'selected':''}>${r.icon} ${r.displayName}</option>`).join('');
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--gold)" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            Pity System
          </div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName||crate.id)}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div id="pityEnabledToggle" style="margin-bottom:12px;padding-bottom:12px;border-bottom:1px solid var(--border)"></div>
        <div id="pityFields" style="${pity.enabled?'':'opacity:.4;pointer-events:none'}">
          <div class="field-row">
            <div class="field-group"><label class="field-label">Hard Pity Threshold</label><input class="field-input" type="number" id="pmMax" value="${pity.threshold||100}" min="1" max="1000"/></div>
            <div class="field-group"><label class="field-label">Soft Pity Start</label><input class="field-input" type="number" id="pmSoft" value="${pity.softPityStart||80}" min="1"/></div>
          </div>
          <div class="field-row" style="margin-top:9px">
            <div class="field-group"><label class="field-label">Minimum Rarity (Pity)</label><select class="field-input" id="pmRarity">${pityRarityOptions}</select></div>
            <div class="field-group"><label class="field-label">Bonus Chance / Open (%)</label><input class="field-input" type="number" id="pmBonus" value="${pity.bonusChancePerOpen||2}" min="0.1" step="0.1"/></div>
          </div>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="PityModal.save()">Save Pity Config</button>
      </div>
    `, 'modal-md');
    const fields = Utils.qs('#pityFields');
    Utils.qs('#pityEnabledToggle').appendChild(ToggleSwitch('Enable Pity System', pity.enabled, v => { pity.enabled=v; fields.style.opacity=v?'1':'.4'; fields.style.pointerEvents=v?'':'none'; }));
    this._crate=crate; this._onSave=onSave;
  },
  save() {
    const pity = this._crate.pity;
    pity.threshold = parseInt(Utils.qs('#pmMax').value)||100;
    pity.softPityStart = parseInt(Utils.qs('#pmSoft').value)||80;
    pity.rareRarityMinimum = Utils.qs('#pmRarity').value;
    pity.bonusChancePerOpen = parseFloat(Utils.qs('#pmBonus').value)||2;
    this._onSave?.(); Modal.close(); toast('Pity system updated', 'success');
  },
};

/* ══ KEY REQ MODAL ══ */
const KeyReqModal = {
  open(crate, onSave) {
    if (!crate.requiredKeys) crate.requiredKeys = [];
    this._crate=crate; this._onSave=onSave;
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--blue)" stroke-width="2"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 11-7.778 7.778 5.5 5.5 0 017.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>
            Key Requirements
          </div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName||crate.id)}</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <div id="keyReqList" style="display:flex;flex-direction:column;gap:7px"></div>
        <button class="btn btn-ghost btn-sm" style="margin-top:9px;width:100%;justify-content:center" onclick="KeyReqModal.addKey()">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Add Key Requirement
        </button>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="KeyReqModal.save()">Save Keys</button>
      </div>
    `, 'modal-md');
    this.renderList();
  },
  renderList() {
    const list = Utils.qs('#keyReqList'); if (!list) return;
    const crate = this._crate;
    list.innerHTML = '';
    if (!crate.requiredKeys.length) { list.innerHTML='<div style="color:var(--text3);font-size:12px;text-align:center;padding:14px 0">No key requirements. Click below to add one.</div>'; return; }
    crate.requiredKeys.forEach((k,i) => {
      const row = Utils.el('div');
      row.style.cssText='display:flex;gap:7px;align-items:flex-end;padding:9px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm)';
      row.innerHTML=`<div class="field-group" style="flex:2"><label class="field-label">Key ID</label><input class="field-input" value="${k.keyId}" placeholder="legendary_key" data-idx="${i}" data-field="keyId"/></div><div class="field-group" style="flex:0 0 64px"><label class="field-label">Amount</label><input class="field-input" type="number" value="${k.amount||1}" min="1" data-idx="${i}" data-field="amount"/></div><div class="field-group" style="flex:1.4"><label class="field-label">Source</label><select class="field-input" data-idx="${i}" data-field="type">${['VIRTUAL','PHYSICAL','MMOITEMS','ITEMSADDER','ORAXEN'].map(t=>`<option value="${t}" ${k.type===t?'selected':''}>${t}</option>`).join('')}</select></div><button class="btn btn-danger btn-xs" style="flex-shrink:0;margin-bottom:1px" data-remove="${i}">✕</button>`;
      row.querySelectorAll('[data-field]').forEach(el => { el.onchange=()=>{ crate.requiredKeys[parseInt(el.dataset.idx)][el.dataset.field]=el.dataset.field==='amount'?parseInt(el.value):el.value; }; });
      row.querySelector('[data-remove]').onclick=()=>{ crate.requiredKeys.splice(i,1); this.renderList(); };
      list.appendChild(row);
    });
  },
  addKey() { this._crate.requiredKeys.push({keyId:'',amount:1,type:'VIRTUAL'}); this.renderList(); },
  save()   { this._onSave?.(); Modal.close(); toast('Key requirements updated', 'success'); },
};

/* ══ RARITY EDITOR ══ */
const RarityEditor = {
  draft: [],
  open() { this.draft = State.rarities.map(r=>({...r})); this._render(); },
  MC_COLORS:[{code:'&0',hex:'#000000'},{code:'&1',hex:'#0000aa'},{code:'&2',hex:'#00aa00'},{code:'&3',hex:'#00aaaa'},{code:'&4',hex:'#aa0000'},{code:'&5',hex:'#aa00aa'},{code:'&6',hex:'#ffaa00'},{code:'&7',hex:'#aaaaaa'},{code:'&8',hex:'#555555'},{code:'&9',hex:'#5555ff'},{code:'&a',hex:'#55ff55'},{code:'&b',hex:'#55ffff'},{code:'&c',hex:'#ff5555'},{code:'&d',hex:'#ff55ff'},{code:'&e',hex:'#ffff55'},{code:'&f',hex:'#ffffff'}],
  _colorToMinecraft(hex){if(!hex||!hex.startsWith('#'))return'&f';const r1=parseInt(hex.slice(1,3),16)||0,g1=parseInt(hex.slice(3,5),16)||0,b1=parseInt(hex.slice(5,7),16)||0;let best='&f',bestDist=Infinity;for(const{code,hex:mh}of this.MC_COLORS){const r2=parseInt(mh.slice(1,3),16),g2=parseInt(mh.slice(3,5),16),b2=parseInt(mh.slice(5,7),16),dist=(r1-r2)**2+(g1-g2)**2+(b1-b2)**2;if(dist<bestDist){bestDist=dist;best=code;}}return best;},
  _render() {
    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            Rarity Editor
          </div>
          <div class="modal-subtitle">Add, remove, or recolor rarity tiers. Changes sync to rarities.yml.</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body" style="padding-bottom:8px">
        <div style="display:grid;grid-template-columns:24px 1fr 100px 70px 50px 32px;gap:6px;align-items:center;padding:0 3px 5px;border-bottom:1px solid var(--border);margin-bottom:7px;font-size:9px;font-weight:700;color:var(--text3);letter-spacing:.7px;text-transform:uppercase">
          <span></span><span>Name</span><span>Hex Color</span><span>Order</span><span>Icon</span><span></span>
        </div>
        <div id="rarityRows" style="display:flex;flex-direction:column;gap:5px">
          ${this.draft.map((r,i)=>this._rowHtml(r,i)).join('')}
        </div>
        <button class="btn btn-ghost btn-sm" style="margin-top:9px;width:100%;justify-content:center" onclick="RarityEditor.addRow()">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Add Rarity
        </button>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-ghost btn-sm" onclick="RarityEditor.reload()">Reload from File</button>
        <button class="btn btn-primary" onclick="RarityEditor.save()">Save Rarities</button>
      </div>
    `, 'modal-lg');
  },
  _rowHtml(r,i){return`<div class="rarity-editor-row" data-idx="${i}" style="display:grid;grid-template-columns:24px 1fr 100px 70px 50px 32px;gap:6px;align-items:center;padding:5px 3px;border-radius:5px;border:1px solid var(--border);background:var(--bg3)"><div style="width:20px;height:20px;border-radius:5px;background:${r.hexColor};border:1.5px solid ${r.hexColor}40;cursor:pointer;position:relative;overflow:hidden" title="Pick color" id="rSwatchWrap${i}" onclick="document.getElementById('rClrPick${i}').click()"><input type="color" id="rClrPick${i}" value="${r.hexColor}" style="position:absolute;opacity:0;width:0;height:0" oninput="RarityEditor._onColorPick(${i},this.value)"/></div><input class="field-input" style="padding:4px 7px;font-size:11.5px" value="${r.displayName}" placeholder="Legendary" oninput="RarityEditor.update(${i},'displayName',this.value)"/><input class="field-input" style="padding:4px 7px;font-size:11px;font-family:monospace" id="rHexIn${i}" value="${r.hexColor}" placeholder="#aaaaaa" oninput="RarityEditor._onHexInput(${i},this.value)"/><input class="field-input" type="number" style="padding:4px 7px;font-size:11.5px" value="${r.order}" min="0" max="99" oninput="RarityEditor.update(${i},'order',parseInt(this.value)||0)"/><input class="field-input" style="padding:4px 7px;font-size:15px;text-align:center" value="${r.icon||'⬜'}" maxlength="4" oninput="RarityEditor.update(${i},'icon',this.value)"/><button class="btn btn-danger btn-xs" style="padding:4px 6px" onclick="RarityEditor.removeRow(${i})">✕</button></div>`;},
  _onColorPick(idx,hex){this.update(idx,'hexColor',hex);this.update(idx,'color',this._colorToMinecraft(hex));const s=Utils.qs(`#rSwatchWrap${idx}`);if(s){s.style.background=hex;s.style.borderColor=hex+'40';}const h=Utils.qs(`#rHexIn${idx}`);if(h)h.value=hex;},
  _onHexInput(idx,val){if(!/^#[0-9a-f]{6}$/i.test(val))return;this.update(idx,'hexColor',val);this.update(idx,'color',this._colorToMinecraft(val));const s=Utils.qs(`#rSwatchWrap${idx}`);if(s){s.style.background=val;s.style.borderColor=val+'40';}const p=Utils.qs(`#rClrPick${idx}`);if(p)p.value=val;},
  update(idx,field,value){if(!this.draft[idx])return;this.draft[idx][field]=value;if(field==='displayName'){this.draft[idx].id=value.toUpperCase().replace(/\s+/g,'_').replace(/[^A-Z0-9_]/g,'')||'CUSTOM';}},
  addRow(){const nextOrder=this.draft.length>0?Math.max(...this.draft.map(r=>r.order))+1:0;this.draft.push({id:'CUSTOM_'+nextOrder,displayName:'Custom',color:'&f',hexColor:'#aaaaaa',order:nextOrder,borderMaterial:'GRAY_STAINED_GLASS_PANE',icon:'⬜'});this._render();},
  removeRow(idx){if(this.draft.length<=1){toast('Must have at least one rarity!','error');return;}this.draft.splice(idx,1);this._render();},
  async save(){for(const r of this.draft){if(!r.displayName?.trim()){toast('All rarities must have a display name','error');return;}if(!r.id?.trim())r.id=r.displayName.toUpperCase().replace(/\s+/g,'_').replace(/[^A-Z0-9_]/g,'');if(!r.color||!r.color.startsWith('&'))r.color=this._colorToMinecraft(r.hexColor);}try{await API.post('/rarities',{rarities:this.draft});toast('Rarities saved','success');State.setRarities(this.draft);Modal.close();const el=document.getElementById('page-architect');if(el)Architect.render(el);}catch(e){toast(e.message,'error');}},
  async reload(){try{await API.post('/rarities/reload');toast('Rarities reloaded','info');const data=await API.get('/rarities');State.setRarities(data.data||[]);Modal.close();const el=document.getElementById('page-architect');if(el)Architect.render(el);}catch(e){toast(e.message,'error');}},
};