/* ══ PAGE: SETTINGS ══ */
const Settings = {
  render(container) {
    container.innerHTML = `
      <div class="page-header header-settings">
        <div class="page-header-inner">
          <div>
            <div class="page-title">Settings</div>
            <div class="page-sub">Server connection and plugin configuration.</div>
          </div>
          <div class="page-actions">
            <button class="btn btn-ghost btn-sm" style="color:rgba(255,255,255,.85);border-color:rgba(255,255,255,.3)" onclick="Settings.save()">
              ${ICONS.diskSave} Save Settings
            </button>
          </div>
        </div>
      </div>
      <div class="settings-grid">
        <div class="settings-section">
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>WEB SERVER</div></div>
            <div style="display:flex;flex-direction:column;gap:11px">
              <div class="field-group">
                <label class="field-label">Server URL</label>
                <input class="field-input" id="cfgUrl" value="${State.serverUrl}" placeholder="http://localhost:7420"/>
                <div style="font-size:10.5px;color:var(--text3);margin-top:3px">WebSocket will connect to ws:// equivalent automatically.</div>
              </div>
              <div class="field-group">
                <label class="field-label">Web Port</label>
                <input class="field-input" type="number" id="cfgPort" value="7420"/>
              </div>
              <div class="field-group">
                <label class="field-label">CORS Origins</label>
                <input class="field-input" id="cfgCors" value="*" placeholder="* or http://panel.myserver.com"/>
                <div style="font-size:10.5px;color:var(--text3);margin-top:3px">* = allow all (dev only).</div>
              </div>
              <div class="field-group">
                <label class="field-label">Rate Limit (req/min per IP)</label>
                <input class="field-input" type="number" id="cfgRateLimit" value="120"/>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>DATABASE</div></div>
            <div style="display:flex;flex-direction:column;gap:11px">
              <div id="dbModeToggle"></div>
              <div id="dbFields"></div>
            </div>
          </div>
        </div>
        <div class="settings-section">
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>SERVER STATUS</div></div>
            <div id="serverStatusCard" style="display:flex;flex-direction:column;gap:9px">
              <div class="skeleton" style="height:20px"></div>
              <div class="skeleton" style="height:20px"></div>
              <div class="skeleton" style="height:20px"></div>
            </div>
            <button class="btn btn-ghost btn-sm" style="margin-top:11px" onclick="Settings.refreshStatus()">
              ${ICONS.refresh} Refresh Status
            </button>
          </div>
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>DANGER ZONE</div></div>
            <div style="display:flex;flex-direction:column;gap:9px">
              <button class="btn btn-ghost btn-sm" onclick="Settings.reloadPlugin()">
                ${ICONS.reload} Reload Plugin (all crates)
              </button>
              <button class="btn btn-danger btn-sm" onclick="Settings.logout()">
                ${ICONS.logout} Logout
              </button>
            </div>
          </div>
        </div>
      </div>
    `;

    this.refreshStatus();
    const dbSeg = Utils.el('div');
    dbSeg.appendChild(ToggleSwitch('Use MySQL (default: SQLite)', false, v => {
      Utils.qs('#dbFields').innerHTML = v ? `
        <div class="field-row"><div class="field-group"><label class="field-label">Host</label><input class="field-input" value="localhost"/></div><div class="field-group"><label class="field-label">Port</label><input class="field-input" value="3306"/></div></div>
        <div class="field-group"><label class="field-label">Database</label><input class="field-input" value="quantumcrates"/></div>
        <div class="field-row"><div class="field-group"><label class="field-label">Username</label><input class="field-input" value="root"/></div><div class="field-group"><label class="field-label">Password</label><input class="field-input" type="password" placeholder="••••••"/></div></div>
      ` : '<div style="font-size:11.5px;color:var(--text2)">Using SQLite — file: <code style="color:var(--cyan)">quantumcrates.db</code></div>';
    }));
    Utils.qs('#dbModeToggle')?.appendChild(dbSeg);
  },

  async refreshStatus() {
    const card = Utils.qs('#serverStatusCard'); if (!card) return;
    try {
      const s = await API.getServerStatus();
      State.serverStatus = s;
      card.innerHTML = `
        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--text2)">Status</span><span style="color:var(--green);font-weight:600;display:flex;align-items:center;gap:5px"><svg width="8" height="8" viewBox="0 0 24 24" fill="var(--green)"><circle cx="12" cy="12" r="12"/></svg>Online</span></div>
        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--text2)">Players</span><span>${s.onlinePlayers} / ${s.maxPlayers}</span></div>
        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--text2)">TPS</span><span style="color:${s.tps>18?'var(--green)':s.tps>15?'var(--gold)':'var(--red)'}">${s.tps?.toFixed(2)}</span></div>
        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--text2)">Version</span><span style="color:var(--text3);font-size:10px">${s.version||'—'}</span></div>
        <div style="display:flex;justify-content:space-between;font-size:12px"><span style="color:var(--text2)">Crates</span><span>${s.crateCount}</span></div>
      `;
      refreshStatus();
    } catch (e) {
      card.innerHTML = `<div style="color:var(--red);font-size:12px;display:flex;align-items:center;gap:6px"><svg width="8" height="8" viewBox="0 0 24 24" fill="var(--red)"><circle cx="12" cy="12" r="12"/></svg>Offline — ${e.message}</div>`;
      updateStatusDot(false);
    }
  },

  save() { toast('Settings saved — restart server to apply DB/port changes.', 'info'); },
  async reloadPlugin() {
    try { await API.reloadCrates(); toast('Plugin reload triggered', 'success'); }
    catch (e) { toast(e.message, 'error'); }
  },
  logout() { logout(); },
};
