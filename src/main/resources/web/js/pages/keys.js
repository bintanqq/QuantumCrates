/* ══ PAGE: KEY SETTINGS ══ */
const KeySettings = {
  render(container) {
    container.innerHTML = `
      <div class="page-header">
        <div><div class="page-title">Key Settings</div><div class="page-sub">Global key mode configuration. One mode for the whole server.</div></div>
      </div>

      <div style="max-width:640px;display:flex;flex-direction:column;gap:14px">
        <div class="card">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>KEY MODE</div></div>
          <div style="display:flex;flex-direction:column;gap:12px">
            <div class="key-mode-banner virtual" id="modeBannerVirtual">
              <span style="font-size:20px">💾</span>
              <div>
                <div style="font-weight:700;color:var(--cyan)">Virtual Keys</div>
                <div style="font-size:11px;color:var(--text2);margin-top:2px">Keys stored as balance in database. Players have no physical item. More secure — cannot be dropped, traded, or duplicated.</div>
              </div>
            </div>
            <div class="key-mode-banner physical" id="modeBannerPhysical" style="display:none">
              <span style="font-size:20px">🔑</span>
              <div>
                <div style="font-weight:700;color:var(--gold)">Physical Keys</div>
                <div style="font-size:11px;color:var(--text2);margin-top:2px">Keys are physical items in player inventory with PDC tag. Can be dropped, traded, stored in chests. More immersive feel.</div>
              </div>
            </div>
            <div id="modeSeg"></div>
          </div>
        </div>

        <div class="card" id="physicalConfigCard" style="display:none">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>PHYSICAL KEY APPEARANCE</div></div>
          <div style="display:flex;flex-direction:column;gap:10px">
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">Default Material</label>
                <input class="field-input" id="physMat" value="TRIPWIRE_HOOK" placeholder="TRIPWIRE_HOOK"/>
              </div>
              <div class="field-group">
                <label class="field-label">Custom Model Data</label>
                <input class="field-input no-spinner" type="number" id="physCmd" value="-1" placeholder="-1 = none"/>
              </div>
            </div>
            <div class="field-group">
              <label class="field-label">Extra Lore Lines (one per line)</label>
              <textarea class="field-input" id="physLore" rows="3" placeholder="&8▸ &7Right-click crate to use."></textarea>
            </div>
            <button class="btn btn-primary btn-sm" onclick="KeySettings.savePhysical()" style="align-self:flex-start">💾 Save Physical Config</button>
          </div>
        </div>

        <div class="card">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>KEY IDS IN USE</div></div>
          <div id="keyIdList" style="display:flex;flex-wrap:wrap;gap:6px">
            ${Object.values(State.crates).flatMap(c=>c.requiredKeys||[]).map(k =>
              `<span class="chip">${k.keyId}</span>`
            ).join('') || '<span style="color:var(--text3);font-size:12px">No crates configured yet.</span>'}
          </div>
        </div>

        <div class="card">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>GIVE KEY TO PLAYER</div></div>
          <div style="display:flex;flex-direction:column;gap:10px">
            <div class="field-row-3">
              <div class="field-group">
                <label class="field-label">Player Name / UUID</label>
                <input class="field-input" id="givePlayer" placeholder="PlayerName or UUID"
                  oninput="KeySettings.clearPlayerStatus()"/>
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

            <!-- Player status feedback -->
            <div id="givePlayerStatus" style="display:none;font-size:11.5px;padding:7px 10px;border-radius:var(--radius-sm)"></div>

            <div style="font-size:11px;color:var(--text3);background:var(--bg3);padding:8px 12px;border-radius:var(--radius-sm);border:1px solid var(--border)">
              ℹ️ Can use Name / UUID. Key type (virtual/physical).
            </div>
            <button class="btn btn-primary btn-sm" onclick="KeySettings.giveKey()" style="align-self:flex-start">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 12V22H4V12"/><path d="M22 7H2v5h20V7z"/><path d="M12 22V7"/><path d="M12 7H7.5a2.5 2.5 0 010-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 000-5C13 2 12 7 12 7z"/></svg>
              Give Key
            </button>
          </div>
        </div>
      </div>
    `;

    // Mode segmented control
    const seg = Utils.qs('#modeSeg');
    const modes = ['virtual','physical'];
    seg.innerHTML = '';
    const sc = Utils.el('div','seg-ctrl');
    modes.forEach(m => {
      const opt = Utils.el('div', `seg-opt${m==='virtual'?' active':''}`, m.charAt(0).toUpperCase()+m.slice(1));
      opt.onclick = () => { sc.querySelectorAll('.seg-opt').forEach(o=>o.classList.remove('active')); opt.classList.add('active'); this.setMode(m); };
      sc.appendChild(opt);
    });
    seg.appendChild(sc);
  },

  setMode(mode) {
    Utils.qs('#modeBannerVirtual').style.display  = mode==='virtual'  ? 'flex' : 'none';
    Utils.qs('#modeBannerPhysical').style.display = mode==='physical' ? 'flex' : 'none';
    Utils.qs('#physicalConfigCard').style.display = mode==='physical' ? 'block': 'none';
    toast(`Switched to ${mode} key mode. Save in Settings to apply.`, 'info');
  },

  savePhysical() { toast('Physical key config saved ✓', 'success'); },

  clearPlayerStatus() {
    const el = Utils.qs('#givePlayerStatus');
    if (el) el.style.display = 'none';
  },

  _showPlayerStatus(msg, type) {
    const el = Utils.qs('#givePlayerStatus');
    if (!el) return;
    const colors = {
      success: { bg: 'var(--green-dim)', border: 'rgba(34,217,138,.2)', color: 'var(--green)' },
      error:   { bg: 'var(--red-dim)',   border: 'rgba(255,77,109,.2)', color: 'var(--red)'   },
      info:    { bg: 'var(--cyan-dim)',  border: 'rgba(0,74,173,.2)',   color: 'var(--cyan)'  },
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

    if (!input || !keyId) {
      toast('Fill in player and key ID', 'error');
      return;
    }

    let uuid = input;
    let playerName = input;

    if (!Utils.isUUID(input)) {
      this._showPlayerStatus(`Looking up player "${input}"...`, 'info');
      try {
        const lookup = await API.get('/players/lookup?name=' + encodeURIComponent(input));
        uuid = lookup.uuid;
        playerName = lookup.name || input;
        this._showPlayerStatus(`Found: ${playerName} (${uuid})`, 'success');
      } catch (e) {
        this._showPlayerStatus(`Player "${input}" not found. Make sure they've joined the server before.`, 'error');
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