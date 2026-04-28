/* ══ PAGE: PLAYERS ══ */
const Players = {
  render(container) {
    container.innerHTML = `
      <div class="page-header header-players">
        <div class="page-header-inner">
          <div>
            <div class="page-title">Players</div>
            <div class="page-sub">Check and manage player pity counters and key balances.</div>
          </div>
        </div>
      </div>
      <div style="max-width:700px;display:flex;flex-direction:column;gap:14px">
        <div class="card">
          <div class="card-header"><div class="card-title"><span class="card-accent"></span>PLAYER LOOKUP</div></div>
          <div style="display:flex;gap:9px">
            <input class="field-input" id="playerUuid" placeholder="Player UUID or name..." style="flex:1"/>
            <button class="btn btn-primary btn-sm" onclick="Players.lookup()">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              Look Up
            </button>
          </div>
        </div>
        <div id="playerResult"></div>
      </div>
    `;
  },

  async lookup() {
    const input = Utils.qs('#playerUuid')?.value?.trim();
    if (!input) { toast('Enter a player name or UUID', 'error'); return; }
    const result = Utils.qs('#playerResult');
    result.innerHTML = '<div class="card"><div class="skeleton" style="height:120px"></div></div>';
    try {
      let uuid = input, playerName = input;
      if (!Utils.isUUID(input)) {
        const lookup = await API.get('/players/lookup?name=' + encodeURIComponent(input));
        uuid = lookup.uuid; playerName = lookup.name;
      }
      const pity = await API.getPlayerPity(uuid);
      result.innerHTML = '';
      const card = Utils.el('div', 'card');
      card.innerHTML = `
        <div class="card-header">
          <div class="card-title"><span class="card-accent"></span>PITY DATA — ${playerName}<span style="font-size:10px;color:var(--text3);font-weight:400;margin-left:6px">${uuid}</span></div>
        </div>
        <div id="pityList"></div>
        <button class="btn btn-danger btn-sm" style="margin-top:11px" onclick="Players.resetAll('${uuid}')">
          ${ICONS.trash} Reset All Pity
        </button>
      `;
      const list = card.querySelector('#pityList');
      const pityData = pity.pityData || {};
      if (!Object.keys(pityData).length) {
        list.innerHTML = '<div style="color:var(--text3);font-size:12px;padding:9px 0">No pity data found for this player.</div>';
      } else {
        Object.entries(pityData).forEach(([crateId, count]) => {
          const crate = State.crates[crateId];
          const max = crate?.pity?.threshold || 100;
          const div = Utils.el('div', '', `
            <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:5px;margin-top:9px">
              <span style="font-weight:600">${Utils.strip(crate?.displayName || crateId)}</span>
              <div style="display:flex;gap:7px;align-items:center">
                <span style="color:var(--cyan)">${count} / ${max}</span>
                <button class="btn btn-ghost btn-xs" onclick="Players.resetOne('${uuid}','${crateId}')">Reset</button>
              </div>
            </div>
            <div class="progress"><div class="progress-fill" style="width:${Math.min(count/max*100,100)}%"></div></div>
          `);
          list.appendChild(div);
        });
      }
      result.appendChild(card);
    } catch (e) {
      result.innerHTML = `<div class="card"><div style="color:var(--red);font-size:12px">${e.message}</div></div>`;
    }
  },

  async resetOne(uuid, crateId) {
    try { await API.resetPlayerPity(uuid, crateId); toast(`Pity reset for ${crateId}`, 'success'); this.lookup(); }
    catch (e) { toast(e.message, 'error'); }
  },
  async resetAll(uuid) {
    if (!confirm('Reset ALL pity for this player?')) return;
    try { await API.resetPlayerPity(uuid, null); toast('All pity reset', 'success'); this.lookup(); }
    catch (e) { toast(e.message, 'error'); }
  },
};
