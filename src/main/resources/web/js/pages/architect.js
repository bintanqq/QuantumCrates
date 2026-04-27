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
          <button class="btn btn-ghost btn-sm" id="btnPreviewCrate">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            Preview
          </button>
          <button class="btn btn-danger btn-sm" id="btnDiscardCrate">↩ Discard</button>
          <button class="btn btn-primary btn-sm" id="btnSaveCrate">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
            Save Crate
          </button>
        </div>
      </div>

      <!-- Crate tabs -->
      <div class="crate-selector-bar" id="crateTabs"></div>

      <div class="architect-grid">
        <div class="architect-left" id="architectLeft">
          <div class="card" id="rewardsCard">
            <div class="card-header">
              <div class="card-title"><span class="card-accent"></span>REWARDS <span class="card-sub">Loot Table</span></div>
              <button class="btn btn-ghost btn-sm" id="btnAddReward">+ Add Reward</button>
            </div>
            <div class="rewards-grid" id="rewardsGrid"></div>
            <div class="total-weight-bar"><div class="total-weight-fill" id="totalWeightFill"></div></div>
            <div class="weight-footer">
              <span>Total Weight</span>
              <span class="weight-total" id="totalWeightLabel">0%</span>
            </div>
          </div>

          <div class="card" id="sliderCard">
            <div class="card-header">
              <div class="card-title"><span class="card-accent"></span>CHANCE MANAGEMENT <span class="card-sub">Weight %</span></div>
              <div style="display:flex;align-items:center;gap:6px;font-size:11px;color:var(--text3)">
                Sort:
                <select class="field-input" style="padding:4px 22px 4px 8px;width:auto;font-size:11px" id="sortOrder">
                  <option value="RARITY_DESC">Rarity ↓</option>
                  <option value="RARITY_ASC">Rarity ↑</option>
                  <option value="WEIGHT_DESC">Weight ↓</option>
                  <option value="WEIGHT_ASC">Weight ↑</option>
                  <option value="CONFIG_ORDER">Config Order</option>
                </select>
              </div>
            </div>
            <div id="sliderGrid"></div>
          </div>
        </div>

        <div class="architect-right" id="architectRight">
          <!-- Crate Settings -->
          <div class="card card-sm" id="crateSettingsCard">
            <div class="card-header" style="margin-bottom:10px">
              <div class="card-title"><span class="card-accent"></span>CRATE SETTINGS</div>
            </div>
            <div style="display:flex;flex-direction:column;gap:8px" id="crateSettingsFields"></div>
          </div>

          <!-- Pity System -->
          <div class="card card-sm" id="pityCard">
            <div class="card-header" style="margin-bottom:8px">
              <div class="card-title"><span class="card-accent"></span>PITY SYSTEM</div>
              <div id="pityToggle"></div>
            </div>
            <div id="pityBody"></div>
          </div>

          <!-- Key Section -->
          <div class="card card-sm" id="keyCard">
            <div class="card-header" style="margin-bottom:8px">
              <div class="card-title"><span class="card-accent"></span>KEY REQUIREMENTS</div>
            </div>
            <div id="keyBody"></div>
          </div>

          <!-- Preview Config -->
          <div class="card card-sm" id="previewConfigCard">
            <div class="card-header" style="margin-bottom:8px">
              <div class="card-title"><span class="card-accent"></span>PREVIEW GUI CONFIG</div>
            </div>
            <div id="previewConfigBody"></div>
          </div>
        </div>
      </div>
    `;

    this._bindTopActions();
    this.renderCrateTabs();
    if (State.currentCrateId) this.loadCrate(State.currentCrateId);
  },

  _bindTopActions() {
    Utils.on(Utils.qs('#btnSaveCrate'),    'click', () => this.save());
    Utils.on(Utils.qs('#btnDiscardCrate'), 'click', () => this.discard());
    Utils.on(Utils.qs('#btnPreviewCrate'), 'click', () => PreviewModal.open(State.currentCrate));
    Utils.on(Utils.qs('#btnAddReward'),    'click', () => RewardModal.open(null, r => this.addReward(r)));
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
    this.renderSliders();
    this.renderCrateSettings();
    this.renderPity();
    this.renderKeys();
    this.renderPreviewConfig();
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
    this._updateWeightBar(tw);
  },

  renderSliders() {
    const grid  = Utils.qs('#sliderGrid'); if (!grid) return;
    const crate = State.currentCrate;      if (!crate) return;
    grid.innerHTML = '';
    const sorted = this._sortedRewards(crate.rewards);
    const tw = Utils.totalWeight(crate.rewards);
    sorted.forEach(r => grid.appendChild(SliderRow(r, tw, () => {
      this.dirty = true;
      this.renderRewards();
      this.renderSliders();
    })));
  },

  _updateWeightBar(tw) {
    const fill  = Utils.qs('#totalWeightFill');
    const label = Utils.qs('#totalWeightLabel');
    if (!fill || !label) return;
    fill.style.width = Math.min(tw, 100) + '%';
    label.textContent = tw.toFixed(1) + '%';
    label.style.color = tw > 100 ? 'var(--red)' : 'var(--cyan)';
  },

  /* ── Crate Settings ── */
  renderCrateSettings() {
    const body  = Utils.qs('#crateSettingsFields'); if (!body) return;
    const crate = State.currentCrate;               if (!crate) return;

    body.innerHTML = `
      <div class="field-row">
        <div class="field-group">
          <label class="field-label">Crate ID</label>
          <input class="field-input" id="cfgId" value="${crate.id || ''}" placeholder="legendary_crate"/>
        </div>
        <div class="field-group">
          <label class="field-label">Display Name</label>
          <input class="field-input" id="cfgName" value="${crate.displayName || ''}" placeholder="&6&lLegendary Crate"/>
        </div>
      </div>
      <div class="field-row">
        <div class="field-group">
          <label class="field-label">Cooldown</label>
          <select class="field-input" id="cfgCooldown">
            <option value="0" ${!crate.cooldownMs?'selected':''}>No Cooldown</option>
            <option value="300000"   ${crate.cooldownMs===300000?'selected':''}>5 Minutes</option>
            <option value="1800000"  ${crate.cooldownMs===1800000?'selected':''}>30 Minutes</option>
            <option value="3600000"  ${crate.cooldownMs===3600000?'selected':''}>1 Hour</option>
            <option value="86400000" ${crate.cooldownMs===86400000?'selected':''}>24 Hours</option>
            <option value="custom">Custom...</option>
          </select>
        </div>
        <div class="field-group">
          <label class="field-label">Mass Open Limit</label>
          <input class="field-input" type="number" id="cfgMassLimit" value="${crate.massOpenLimit ?? 64}" min="-1"/>
        </div>
      </div>
      <div id="enabledToggle"></div>
      <div id="massOpenToggle"></div>
    `;

    body.querySelector('#enabledToggle').appendChild(
      ToggleSwitch('Crate Enabled', crate.enabled !== false, v => { crate.enabled = v; this.dirty = true; })
    );
    body.querySelector('#massOpenToggle').appendChild(
      ToggleSwitch('Mass Open Enabled', crate.massOpenEnabled !== false, v => { crate.massOpenEnabled = v; this.dirty = true; })
    );

    ['cfgId','cfgName','cfgCooldown','cfgMassLimit'].forEach(id => {
      Utils.qs('#'+id)?.addEventListener('change', () => {
        crate.id          = Utils.qs('#cfgId').value.trim();
        crate.displayName = Utils.qs('#cfgName').value;
        crate.cooldownMs  = parseInt(Utils.qs('#cfgCooldown').value) || 0;
        crate.massOpenLimit = parseInt(Utils.qs('#cfgMassLimit').value) || -1;
        this.dirty = true;
        this.renderCrateTabs();
      });
    });
  },

  /* ── Pity ── */
  renderPity() {
    const body  = Utils.qs('#pityBody');   if (!body) return;
    const tog   = Utils.qs('#pityToggle'); if (!tog) return;
    const crate = State.currentCrate;      if (!crate) return;
    const pity  = crate.pity || {};

    tog.innerHTML = '';
    tog.appendChild(ToggleSwitch('', pity.enabled, v => {
      pity.enabled = v; this.dirty = true; this.renderPity();
    }));

    if (!pity.enabled) { body.innerHTML = '<div style="font-size:11px;color:var(--text3);padding:4px 0">Pity system is disabled for this crate.</div>'; return; }

    body.innerHTML = `
      ${pity.enabled ? PityBar(0, pity.threshold || 100, pity.softPityStart || 80).outerHTML : ''}
      <div class="field-row" style="margin-top:10px">
        <div class="field-group">
          <label class="field-label">Hard Pity Threshold</label>
          <input class="field-input" type="number" id="pityMax" value="${pity.threshold || 100}" min="1" max="1000"/>
        </div>
        <div class="field-group">
          <label class="field-label">Soft Pity Start</label>
          <input class="field-input" type="number" id="pitySoft" value="${pity.softPityStart || 80}" min="1"/>
        </div>
      </div>
      <div class="field-row">
        <div class="field-group">
          <label class="field-label">Minimum Rarity for Pity</label>
          <select class="field-input" id="pityRarity">
            ${['RARE','EPIC','LEGENDARY','MYTHIC'].map(r => `<option value="${r}" ${pity.rareRarityMinimum===r?'selected':''}>${r}</option>`).join('')}
          </select>
        </div>
        <div class="field-group">
          <label class="field-label">Bonus Chance/Open (%)</label>
          <input class="field-input" type="number" id="pityBonus" value="${pity.bonusChancePerOpen || 2}" min="0.1" step="0.1"/>
        </div>
      </div>
    `;

    ['pityMax','pitySoft','pityRarity','pityBonus'].forEach(id => {
      Utils.qs('#'+id)?.addEventListener('change', () => {
        pity.threshold         = parseInt(Utils.qs('#pityMax').value);
        pity.softPityStart     = parseInt(Utils.qs('#pitySoft').value);
        pity.rareRarityMinimum = Utils.qs('#pityRarity').value;
        pity.bonusChancePerOpen= parseFloat(Utils.qs('#pityBonus').value);
        this.dirty = true;
      });
    });
  },

  /* ── Keys ── */
  renderKeys() {
    const body  = Utils.qs('#keyBody'); if (!body) return;
    const crate = State.currentCrate;  if (!crate) return;
    if (!crate.requiredKeys) crate.requiredKeys = [];

    const renderList = () => {
      body.innerHTML = '';
      crate.requiredKeys.forEach((k, i) => {
        const row = Utils.el('div', 'field-row', `
          <div class="field-group">
            <label class="field-label">Key ID</label>
            <input class="field-input" value="${k.keyId}" placeholder="legendary_key" data-field="keyId" data-idx="${i}"/>
          </div>
          <div class="field-group">
            <label class="field-label">Amount</label>
            <input class="field-input" type="number" value="${k.amount || 1}" min="1" data-field="amount" data-idx="${i}"/>
          </div>
          <div class="field-group">
            <label class="field-label">Source</label>
            <select class="field-input" data-field="type" data-idx="${i}">
              ${['VIRTUAL','PHYSICAL','MMOITEMS','ITEMSADDER','ORAXEN'].map(t =>
                `<option value="${t}" ${k.type===t?'selected':''}>${t}</option>`).join('')}
            </select>
          </div>
          <button class="btn btn-danger btn-xs" style="align-self:flex-end;margin-bottom:0" data-remove="${i}">✕</button>
        `);
        row.querySelectorAll('[data-field]').forEach(el => {
          el.onchange = () => {
            const idx   = parseInt(el.dataset.idx);
            const field = el.dataset.field;
            crate.requiredKeys[idx][field] = field === 'amount' ? parseInt(el.value) : el.value;
            this.dirty = true;
          };
        });
        row.querySelector('[data-remove]').onclick = () => {
          crate.requiredKeys.splice(i, 1); this.dirty = true; renderList();
        };
        body.appendChild(row);
      });

      const addBtn = Utils.el('button', 'btn btn-ghost btn-sm', '+ Add Key Requirement');
      addBtn.style.marginTop = '6px';
      addBtn.onclick = () => {
        crate.requiredKeys.push({ keyId: '', amount: 1, type: 'VIRTUAL' });
        this.dirty = true; renderList();
      };
      body.appendChild(addBtn);
    };
    renderList();
  },

  /* ── Preview Config ── */
  renderPreviewConfig() {
    const body  = Utils.qs('#previewConfigBody'); if (!body) return;
    const crate = State.currentCrate;             if (!crate) return;
    if (!crate.preview) crate.preview = {};
    const p = crate.preview;

    body.innerHTML = `
      <div class="field-group">
        <label class="field-label">GUI Title <span style="color:var(--text3)">(leave blank = auto)</span></label>
        <input class="field-input" id="pvTitle" value="${p.title || ''}" placeholder="&0Preview &8» &b{crate}"/>
      </div>
      <div class="field-row" style="margin-top:8px">
        <div class="field-group">
          <label class="field-label">Sort Order</label>
          <select class="field-input" id="pvSort">
            ${['RARITY_DESC','RARITY_ASC','WEIGHT_DESC','WEIGHT_ASC','CONFIG_ORDER'].map(s =>
              `<option value="${s}" ${p.sortOrder===s?'selected':''}>${s.replace('_',' ')}</option>`).join('')}
          </select>
        </div>
        <div class="field-group">
          <label class="field-label">Border Material</label>
          <input class="field-input" id="pvBorder" value="${p.borderMaterial || ''}" placeholder="auto"/>
        </div>
      </div>
      <div style="display:flex;flex-direction:column;gap:7px;margin-top:10px" id="pvToggles"></div>
      <div class="field-group" style="margin-top:8px">
        <label class="field-label">Chance Format</label>
        <input class="field-input" id="pvChanceFmt" value="${p.chanceFormat || '&7Chance: &e{chance}'}" placeholder="&7Chance: &e{chance}"/>
      </div>
    `;

    const togs = [
      ['Show Chance %',   'showChance',    p.showChance !== false],
      ['Show Weight',     'showWeight',    !!p.showWeight],
      ['Show Pity',       'showPity',      p.showPity !== false],
      ['Show Key Balance','showKeyBalance',p.showKeyBalance !== false],
      ['Show Actual Item','showActualItem',p.showActualItem !== false],
    ];
    const togContainer = Utils.qs('#pvToggles');
    togs.forEach(([label, key, val]) => {
      togContainer.appendChild(ToggleSwitch(label, val, v => { p[key] = v; this.dirty = true; }));
    });

    ['pvTitle','pvSort','pvBorder','pvChanceFmt'].forEach(id => {
      Utils.qs('#'+id)?.addEventListener('change', () => {
        p.title         = Utils.qs('#pvTitle').value || null;
        p.sortOrder     = Utils.qs('#pvSort').value;
        p.borderMaterial= Utils.qs('#pvBorder').value || null;
        p.chanceFormat  = Utils.qs('#pvChanceFmt').value;
        this.dirty = true;
      });
    });
  },

  /* ── Reward CRUD ── */
  addReward(reward) {
    const crate = State.currentCrate; if (!crate) return;
    if (!crate.rewards) crate.rewards = [];
    crate.rewards.push(reward);
    this.dirty = true;
    this.renderRewards();
    this.renderSliders();
  },

  updateReward(id, updated) {
    const crate = State.currentCrate; if (!crate) return;
    const idx   = crate.rewards.findIndex(r => r.id === id);
    if (idx !== -1) crate.rewards[idx] = updated;
    this.dirty = true;
    this.renderRewards();
    this.renderSliders();
  },

  removeReward(id) {
    const crate = State.currentCrate; if (!crate) return;
    crate.rewards = crate.rewards.filter(r => r.id !== id);
    this.dirty = true;
    this.renderRewards();
    this.renderSliders();
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
      toast('Crate saved & synced to server ✓', 'success');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.innerHTML = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> Save Crate';
    }
  },

  discard() {
    if (!this.dirty || confirm('Discard all unsaved changes?')) {
      this.dirty = false;
      this.loadCrate(State.currentCrateId);
      toast('Changes discarded', 'info');
    }
  },

  newCrate() {
    const id = 'new_crate_' + Date.now();
    const crate = {
      id, displayName: '&fNew Crate', enabled: true,
      cooldownMs: 0, massOpenEnabled: true, massOpenLimit: 64,
      requiredKeys: [{ keyId: 'example_key', amount: 1, type: 'VIRTUAL' }],
      rewards: [],
      pity: { enabled: false, threshold: 100, softPityStart: 80, rareRarityMinimum: 'LEGENDARY', bonusChancePerOpen: 2 },
      preview: { sortOrder: 'RARITY_DESC', showChance: true, showPity: true, showKeyBalance: true, showActualItem: true },
    };
    State.setCrate(crate);
    State.currentCrateId = id;
    this.dirty = true;
    this.renderCrateTabs();
    this.loadCrate(id);
    toast('New crate created — fill in the details and save!', 'info');
  },

  /* ── Sort helper ── */
  _sortedRewards(rewards) {
    if (!rewards) return [];
    const order = Utils.qs('#sortOrder')?.value || State.currentCrate?.preview?.sortOrder || 'RARITY_DESC';
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
    if (!rewards?.length) return 'COMMON';
    return rewards.reduce((h, r) => Utils.rarityOrder(r.rarity) > Utils.rarityOrder(h) ? r.rarity : h, 'COMMON');
  },
};
