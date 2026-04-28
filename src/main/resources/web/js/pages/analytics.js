/* ══ PAGE: ANALYTICS & LOGS ══ */
const Analytics = {
  logs: [], filter: { crate: '', search: '' },

  render(container) {
    container.innerHTML = `
      <div class="page-header header-analytics">
        <div class="page-header-inner">
          <div>
            <div class="page-title">Analytics & Logs</div>
            <div class="page-sub">Real-time crate opening feed and statistics.</div>
          </div>
          <div class="page-actions">
            <div class="live-indicator">
              <div class="live-dot"></div>LIVE
            </div>
            <button class="btn btn-ghost btn-sm" style="color:rgba(255,255,255,.85);border-color:rgba(255,255,255,.3)" onclick="Analytics.refresh()">
              ${ICONS.refresh} Refresh
            </button>
          </div>
        </div>
      </div>

      <div class="analytics-grid">
        <div style="display:flex;flex-direction:column;gap:14px">
          <div class="stat-grid" id="analyticsStats"></div>
          <div class="card">
            <div class="card-header">
              <div class="card-title"><span class="card-accent"></span>OPENING FEED</div>
              <div class="log-filter-bar" style="margin:0">
                <select class="field-input" id="logCrateFilter" onchange="Analytics.setFilter('crate',this.value)">
                  <option value="">All Crates</option>
                  ${Object.values(State.crates).map(c => `<option value="${c.id}">${Utils.strip(c.displayName||c.id)}</option>`).join('')}
                </select>
                <input class="field-input" id="logSearch" placeholder="Search player..." oninput="Analytics.setFilter('search',this.value)"/>
                <button class="btn btn-ghost btn-sm" onclick="Analytics.clearLogs()">Clear</button>
              </div>
            </div>
            <div class="log-feed" id="logFeed" style="max-height:380px;overflow-y:auto"></div>
          </div>
        </div>

        <div style="display:flex;flex-direction:column;gap:14px">
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>PER CRATE STATS</div></div>
            <div id="crateBreakdown" style="display:flex;flex-direction:column;gap:9px"></div>
          </div>
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>TOP REWARDS</div></div>
            <div id="topRewards" style="display:flex;flex-direction:column;gap:6px"></div>
          </div>
        </div>
      </div>
    `;

    this.loadStats();
    this.loadLogs();
    WS.on('CRATE_OPEN', data => this.onLiveOpen(data));
    WS.on('SERVER_STATS', data => this.onServerStats(data));
  },

  async loadStats() {
    try {
      const stats = await API.getStats();
      this.renderStats(stats);
      this.renderCrateBreakdown(stats);
    } catch(e) { console.warn('Stats load failed:', e.message); }
  },

  async loadLogs() {
    try {
      const res = await API.getLogs({ limit: 50 });
      this.logs = res.data || res || [];
      this.renderLogs();
    } catch(e) { console.warn('Logs load failed:', e.message); }
  },

  renderStats(stats) {
    const container = Utils.qs('#analyticsStats'); if (!container) return;
    const total = stats.totalOpenings || 0;
    container.innerHTML = '';
    const svgBox   = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>`;
    const svgKey   = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 11-7.778 7.778 5.5 5.5 0 017.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>`;
    const svgGem   = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 22 9 18 20 6 20 2 9"/></svg>`;
    const svgTroph = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9H4.5a2.5 2.5 0 010-5H6"/><path d="M18 9h1.5a2.5 2.5 0 000-5H18"/><path d="M4 22h16"/><path d="M10 22v-4.5a2 2 0 014 0V22"/><rect x="6" y="2" width="12" height="13" rx="4"/></svg>`;
    const cards = [
      [svgBox,   Utils.num(total),                       'Total Openings',  null, 'rgba(26,122,74,.09)'],
      [svgKey,   Utils.num(total),                       'Keys Used',       null, 'rgba(176,125,26,.09)'],
      [svgGem,   Utils.num(Math.floor(total*.05)),        'Rare Drops',       5,  'rgba(107,70,193,.09)'],
      [svgTroph, Utils.num(Math.floor(total*.01)),        'Pity Triggers',    1,  'rgba(26,86,160,.09)'],
    ];
    cards.forEach(([icon,val,label,delta,bg]) => container.appendChild(StatCard(icon,val,label,delta,bg)));
  },

  renderCrateBreakdown(stats) {
    const container = Utils.qs('#crateBreakdown'); if (!container) return;
    container.innerHTML = '';
    const perCrate = stats.perCrate || {};
    const total    = stats.totalOpenings || 1;
    Object.entries(perCrate).forEach(([crateId, count]) => {
      const crate = State.crates[crateId];
      const pct   = (count / total * 100).toFixed(1);
      const color = Utils.rarityColor(Architect._highestRarity(crate?.rewards));
      const div   = Utils.el('div');
      div.innerHTML = `
        <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:5px">
          <span style="font-weight:600">${Utils.strip(crate?.displayName||crateId)}</span>
          <span style="color:var(--text2)">${Utils.num(count)} <span style="color:var(--text3)">(${pct}%)</span></span>
        </div>
        <div class="progress"><div class="progress-fill" style="width:${pct}%;background:${color}"></div></div>
      `;
      container.appendChild(div);
    });
    if (!Object.keys(perCrate).length) {
      container.innerHTML = '<div class="empty-state"><p>No data yet</p></div>';
    }
    const topRewards = Utils.qs('#topRewards'); if (!topRewards) return;
    topRewards.innerHTML = '';
    const allRewards = Object.values(State.crates).flatMap(c => c.rewards||[]);
    allRewards.slice(0,5).forEach(r => {
      const color = Utils.rarityColor(r.rarity);
      const svgIcon = REWARD_TYPE_SVGS[r.type] || REWARD_TYPE_SVGS.VANILLA;
      const div = Utils.el('div', '', `
        <div style="display:flex;align-items:center;gap:10px;padding:6px 0;border-bottom:1px solid var(--border)">
          <div style="width:30px;height:30px;border-radius:7px;background:var(--bg3);border:1px solid var(--border);display:flex;align-items:center;justify-content:center;flex-shrink:0">${svgIcon}</div>
          <div style="flex:1;min-width:0">
            <div style="font-size:12px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${Utils.strip(r.displayName)}</div>
            <div style="font-size:10px;color:${color}">${r.rarity}</div>
          </div>
          <span style="font-size:11px;color:var(--text3)">${Utils.fmtChance(Utils.chance(r.weight,Utils.totalWeight(Object.values(State.crates).find(c=>c.rewards?.includes(r))?.rewards||[])))}</span>
        </div>
      `);
      topRewards.appendChild(div);
    });
  },

  renderLogs() {
    const feed = Utils.qs('#logFeed'); if (!feed) return;
    feed.innerHTML = '';
    const filtered = this.logs.filter(l =>
      (!this.filter.crate  || l.crateId === this.filter.crate) &&
      (!this.filter.search || l.playerName.toLowerCase().includes(this.filter.search.toLowerCase()))
    );
    if (!filtered.length) {
      feed.innerHTML = '<div class="empty-state"><p>No logs found</p></div>';
      return;
    }
    filtered.forEach(l => feed.appendChild(LogItem(l)));
  },

  onLiveOpen(data) {
    this.logs.unshift(data);
    if (this.logs.length > 200) this.logs.pop();
    State.openingsToday++;
    Utils.qs('#tstatOpens') && (Utils.qs('#tstatOpens').textContent = Utils.num(State.openingsToday));
    this.renderLogs();
  },

  onServerStats(data) {
    if (Utils.qs('#tstatOnline')) Utils.qs('#tstatOnline').textContent = data.onlinePlayers ?? '—';
    if (Utils.qs('#tstatTps'))    Utils.qs('#tstatTps').textContent    = data.tps?.toFixed(1) ?? '—';
    if (Utils.qs('#tstatOpens'))  Utils.qs('#tstatOpens').textContent  = Utils.num(data.openingsToday || State.openingsToday);
  },

  setFilter(key, val) { this.filter[key] = val; this.renderLogs(); },
  clearLogs() { this.logs = []; this.renderLogs(); },
  async refresh() { await Promise.all([this.loadStats(), this.loadLogs()]); toast('Refreshed', 'success', 1500); },
};