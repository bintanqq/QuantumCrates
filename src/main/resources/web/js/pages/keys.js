/* ══ PAGE: KEY SETTINGS ══ */
const KeySettings = {
  _currentMode: 'virtual',
  _physicalCfg: { material: 'TRIPWIRE_HOOK', customModelData: -1, extraLore: [] },

  render(container) {
    container.innerHTML = `
      <div class="page-header">
        <div>
          <div class="page-title">Key Settings</div>
          <div class="page-sub">Global key mode — applies to the entire server. Requires plugin reload after changing mode.</div>
        </div>
      </div>

      <div style="max-width:640px;display:flex;flex-direction:column;gap:14px">

        <!-- Mode Card -->
        <div class="card">
          <div class="card-header">
            <div class="card-title"><span class="card-accent"></span>KEY MODE</div>
            <div id="modeStatusBadge" style="font-size:10px;font-weight:700;padding:3px 8px;border-radius:20px;background:var(--cyan-dim2);color:var(--cyan);border:1px solid rgba(0,74,173,.3)">Loading...</div>
          </div>

          <!-- Mode banners -->
          <div class="key-mode-banner virtual" id="modeBannerVirtual" style="margin-bottom:12px">
            <span style="font-size:22px">💾</span>
            <div>
              <div style="font-weight:700;color:var(--cyan)">Virtual Keys</div>
              <div style="font-size:11px;color:var(--text2);margin-top:2px">
                Keys stored as balance in database. Players have no physical item.
                More secure — cannot be dropped, traded, or duplicated.
              </div>
            </div>
          </div>
          <div class="key-mode-banner physical" id="modeBannerPhysical" style="display:none;margin-bottom:12px">
            <span style="font-size:22px">🔑</span>
            <div>
              <div style="font-weight:700;color:var(--gold)">Physical Keys</div>
              <div style="font-size:11px;color:var(--text2);margin-top:2px">
                Keys are physical items with PDC tag. Can be dropped, traded, and stored in chests.
              </div>
            </div>
          </div>

          <!-- Segmented control -->
          <div id="modeSeg"></div>

          <!-- Save mode button -->
          <div style="margin-top:12px;display:flex;align-items:center;gap:10px">
            <button class="btn btn-primary btn-sm" id="btnSaveMode" onclick="KeySettings.saveMode()">
              💾 Save Key Mode
            </button>
            <span style="font-size:11px;color:var(--text3)">Requires <code style="color:var(--cyan)">/qc reload</code> in-game to apply.</span>
          </div>
        </div>

        <!-- Physical config (only shown when physical selected) -->
        <div class="card" id="physicalConfigCard" style="display:none">
          <div class="card-header">
            <div class="card-title"><span class="card-accent"></span>PHYSICAL KEY APPEARANCE</div>
          </div>
          <div style="display:flex;flex-direction:column;gap:10px">
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">Default Material (Bukkit)</label>
                <input class="field-input" id="physMat" value="TRIPWIRE_HOOK" placeholder="TRIPWIRE_HOOK"/>
                <div style="font-size:10.5px;color:var(--text3);margin-top:3px">The item type used for physical keys given via <code>/qc give</code>.</div>
              </div>
              <div class="field-group">
                <label class="field-label">Custom Model Data</label>
                <input class="field-input no-spinner" type="number" id="physCmd" value="-1" placeholder="-1 = none"/>
                <div style="font-size:10.5px;color:var(--text3);margin-top:3px">-1 to disable. Use with resource packs.</div>
              </div>
            </div>
            <div class="field-group">
              <label class="field-label">Extra Lore Lines <span style="color:var(--text3)">(one line per row, & color codes)</span></label>
              <textarea class="field-input" id="physLore" rows="3" placeholder="&8▸ &7Right-click a crate to use."></textarea>
            </div>

            <!-- Preview -->
            <div style="padding:10px 12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm)">
              <div style="font-size:9.5px;font-weight:700;color:var(--text3);letter-spacing:.7px;text-transform:uppercase;margin-bottom:6px">KEY ITEM PREVIEW</div>
              <div style="display:flex;align-items:flex-start;gap:10px">
                <div style="width:40px;height:40px;background:var(--bg2);border:1px solid var(--border2);border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">🔗</div>
                <div>
                  <div style="font-size:12px;font-weight:600;color:var(--cyan)">Crate Key &nbsp;<span style="color:var(--text3)">[example_key]</span></div>
                  <div style="font-size:11px;color:var(--text2);margin-top:2px" id="physLorePreview">...</div>
                  <div style="font-size:10px;color:var(--text3);margin-top:2px">§8ID: §7example_key</div>
                </div>
              </div>
            </div>

            <button class="btn btn-primary btn-sm" onclick="KeySettings.savePhysical()" style="align-self:flex-start">
              💾 Save Physical Config
            </button>
          </div>
        </div>

        <!-- Key IDs in use -->
        <div class="card">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>KEY IDS IN USE</div></div>
          <div id="keyIdList" style="display:flex;flex-wrap:wrap;gap:6px">
            <div class="skeleton" style="height:24px;width:100px"></div>
          </div>
        </div>

        <!-- Give Key -->
        <div class="card">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>GIVE KEY TO PLAYER</div></div>
          <div style="display:flex;flex-direction:column;gap:10px">
            <div class="field-row-3">
              <div class="field-group">
                <label class="field-label">Player Name / UUID</label>
                <input class="field-input" id="givePlayer" placeholder="PlayerName or UUID" oninput="KeySettings.clearPlayerStatus()"/>
              </div>
              <div class="field-group">
                <label class="field-label">Key ID</label>
                <input class="field-input" id="giveKeyId" placeholder="legendary_key"/>
              </div>
              <div class="field-group">
                <label class="field-label">Amount</label>
                <input class="field-input no-spinner" type="number" id="giveAmount" value="1" min="1"/>
              </div>
            </div>
            <div id="givePlayerStatus" style="display:none;font-size:11.5px;padding:7px 10px;border-radius:var(--radius-sm)"></div>
            <button class="btn btn-primary btn-sm" onclick="KeySettings.giveKey()" style="align-self:flex-start">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 12V22H4V12"/><path d="M22 7H2v5h20V7z"/><path d="M12 22V7"/><path d="M12 7H7.5a2.5 2.5 0 010-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 000-5C13 2 12 7 12 7z"/></svg>
              Give Key
            </button>
          </div>
        </div>

      </div>
    `;

    // Build seg ctrl
    const seg = Utils.qs('#modeSeg');
    const sc  = Utils.el('div','seg-ctrl');
    ['virtual','physical'].forEach(m => {
      const opt = Utils.el('div', `seg-opt${m==='virtual'?' active':''}`,
        m === 'virtual' ? '💾 Virtual' : '🔑 Physical');
      opt.dataset.mode = m;
      opt.onclick = () => {
        sc.querySelectorAll('.seg-opt').forEach(o => o.classList.remove('active'));
        opt.classList.add('active');
        KeySettings._currentMode = m;
        KeySettings._updateModeBanners(m);
      };
      sc.appendChild(opt);
    });
    seg.appendChild(sc);

    // Lore preview live update
    Utils.qs('#physLore')?.addEventListener('input', () => KeySettings._updateLorePreview());

    // Load current config from server
    this._loadConfig();
  },

  async _loadConfig() {
    try {
      // Load key mode + known key IDs
      const keyData = await API.get('/keys');
      const mode    = (keyData.mode || 'VIRTUAL').toLowerCase();
      this._currentMode = mode;
      this._updateModeBanners(mode);

      // Update seg ctrl active state
      Utils.qsa('#modeSeg .seg-opt').forEach(o => {
        o.classList.toggle('active', o.dataset.mode === mode);
      });

      // Update status badge
      const badge = Utils.qs('#modeStatusBadge');
      if (badge) {
        badge.textContent = mode.toUpperCase();
        badge.style.background = mode === 'virtual' ? 'var(--cyan-dim2)' : 'var(--gold-dim)';
        badge.style.color      = mode === 'virtual' ? 'var(--cyan)'      : 'var(--gold)';
        badge.style.borderColor= mode === 'virtual' ? 'rgba(0,74,173,.3)': 'rgba(245,166,35,.3)';
      }

      // Key IDs list
      const keyIds = keyData.knownIds || [];
      const list   = Utils.qs('#keyIdList');
      if (list) {
        list.innerHTML = keyIds.length
          ? keyIds.map(k => `<span class="chip">${k}</span>`).join('')
          : '<span style="color:var(--text3);font-size:12px">No keys found in any crate config.</span>';
      }

      // Load physical config
      if (mode === 'physical') {
        await this._loadPhysicalConfig();
      }
    } catch (e) {
      console.warn('Could not load key config:', e.message);
      // Fallback: populate from State
      const keyIdList = Utils.qs('#keyIdList');
      if (keyIdList) {
        const ids = Object.values(State.crates).flatMap(c => c.requiredKeys || []).map(k => k.keyId);
        const unique = [...new Set(ids)];
        keyIdList.innerHTML = unique.length
          ? unique.map(k => `<span class="chip">${k}</span>`).join('')
          : '<span style="color:var(--text3);font-size:12px">No crates configured yet.</span>';
      }
    }
  },

  async _loadPhysicalConfig() {
    try {
      const cfg = await API.get('/keys/config/physical');
      this._physicalCfg = cfg;
      const matEl  = Utils.qs('#physMat');
      const cmdEl  = Utils.qs('#physCmd');
      const loreEl = Utils.qs('#physLore');
      if (matEl)  matEl.value  = cfg.material     || 'TRIPWIRE_HOOK';
      if (cmdEl)  cmdEl.value  = cfg.customModelData ?? -1;
      if (loreEl) loreEl.value = (cfg.extraLore || []).join('\n');
      this._updateLorePreview();
    } catch (e) {
      console.warn('Could not load physical key config:', e.message);
    }
  },

  _updateModeBanners(mode) {
    Utils.qs('#modeBannerVirtual').style.display  = mode === 'virtual'  ? 'flex' : 'none';
    Utils.qs('#modeBannerPhysical').style.display = mode === 'physical' ? 'flex' : 'none';
    Utils.qs('#physicalConfigCard').style.display = mode === 'physical' ? 'block': 'none';
    if (mode === 'physical') this._loadPhysicalConfig();
  },

  _updateLorePreview() {
    const el   = Utils.qs('#physLorePreview'); if (!el) return;
    const raw  = Utils.qs('#physLore')?.value || '';
    const lines = raw.split('\n').filter(Boolean);
    el.innerHTML = lines.map(l => `<div>${Utils.mc(l)}</div>`).join('') ||
      '<span style="color:var(--text3)">No extra lore</span>';
  },

  /**
   * Save key mode (virtual/physical) to server config.
   * Writes keys.mode in config.yml and reloads KeyManager.
   */
  async saveMode() {
    const btn = Utils.qs('#btnSaveMode');
    if (btn) { btn.disabled = true; btn.textContent = '⟳ Saving...'; }
    try {
      // We POST to /api/keys/config/mode (we add this endpoint)
      // It sets keys.mode in config.yml and calls KeyManager.reload()
      await API.post('/keys/config/mode', { mode: this._currentMode });

      const badge = Utils.qs('#modeStatusBadge');
      if (badge) {
        badge.textContent = this._currentMode.toUpperCase();
        badge.style.background = this._currentMode === 'virtual' ? 'var(--cyan-dim2)' : 'var(--gold-dim)';
        badge.style.color      = this._currentMode === 'virtual' ? 'var(--cyan)'      : 'var(--gold)';
        badge.style.borderColor= this._currentMode === 'virtual' ? 'rgba(0,74,173,.3)': 'rgba(245,166,35,.3)';
      }

      toast(`Key mode saved: ${this._currentMode.toUpperCase()} ✓ — run /qc reload in-game`, 'success', 5000);
    } catch (e) {
      toast('Failed to save mode: ' + e.message, 'error');
    } finally {
      if (btn) { btn.disabled = false; btn.innerHTML = '💾 Save Key Mode'; }
    }
  },

  async savePhysical() {
    const mat  = Utils.qs('#physMat')?.value?.trim() || 'TRIPWIRE_HOOK';
    const cmd  = parseInt(Utils.qs('#physCmd')?.value) || -1;
    const lore = (Utils.qs('#physLore')?.value || '').split('\n').map(s => s.trim()).filter(Boolean);

    try {
      await API.post('/keys/config/physical', {
        material:        mat,
        customModelData: cmd,
        extraLore:       lore,
      });
      this._physicalCfg = { material: mat, customModelData: cmd, extraLore: lore };
      toast('Physical key config saved ✓', 'success');
    } catch (e) {
      toast('Failed: ' + e.message, 'error');
    }
  },

  clearPlayerStatus() {
    const el = Utils.qs('#givePlayerStatus');
    if (el) el.style.display = 'none';
  },

  _showPlayerStatus(msg, type) {
    const el = Utils.qs('#givePlayerStatus'); if (!el) return;
    const colors = {
      success: { bg:'var(--green-dim)', border:'rgba(34,217,138,.2)', color:'var(--green)' },
      error:   { bg:'var(--red-dim)',   border:'rgba(255,77,109,.2)', color:'var(--red)'   },
      info:    { bg:'var(--cyan-dim)',  border:'rgba(0,74,173,.2)',   color:'var(--cyan)'  },
    };
    const c = colors[type] || colors.info;
    el.style.cssText = `display:block;font-size:11.5px;padding:7px 10px;border-radius:var(--radius-sm);
      background:${c.bg};border:1px solid ${c.border};color:${c.color}`;
    el.textContent = msg;
  },

  async giveKey() {
    const input  = Utils.qs('#givePlayer')?.value?.trim();
    const keyId  = Utils.qs('#giveKeyId')?.value?.trim();
    const amount = parseInt(Utils.qs('#giveAmount')?.value) || 1;
    if (!input || !keyId) { toast('Fill in player and key ID', 'error'); return; }

    let uuid = input, playerName = input;

    if (!Utils.isUUID(input)) {
      this._showPlayerStatus(`Looking up "${input}"...`, 'info');
      try {
        const lookup = await API.get('/players/lookup?name=' + encodeURIComponent(input));
        uuid = lookup.uuid; playerName = lookup.name || input;
        this._showPlayerStatus(`Found: ${playerName} (${uuid})`, 'success');
      } catch (e) {
        this._showPlayerStatus(`Player "${input}" not found. They must have joined before.`, 'error');
        return;
      }
    }

    try {
      await API.giveKey(uuid, keyId, amount);
      this._showPlayerStatus(`✓ Gave ${amount}x ${keyId} to ${playerName}`, 'success');
      toast(`Gave ${amount}x ${keyId} to ${playerName} ✓`, 'success');
    } catch (e) {
      this._showPlayerStatus(e.message, 'error');
      toast(e.message, 'error');
    }
  },
};