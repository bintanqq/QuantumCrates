/* ══ PAGE: ANALYTICS & LOGS ══ */
const Analytics = {
  logs: [], filter: { crate: '', search: '' },

  render(container) {
    container.innerHTML = `
      <div class="page-header">
        <div><div class="page-title">Analytics & Logs</div><div class="page-sub">Real-time crate opening feed and statistics.</div></div>
        <div class="page-actions">
          <div class="live-indicator"><div class="live-dot"></div>LIVE</div>
          <button class="btn btn-ghost btn-sm" onclick="Analytics.refresh()">↻ Refresh</button>
        </div>
      </div>

      <div class="analytics-grid">
        <div style="display:flex;flex-direction:column;gap:14px">
          <!-- Stats -->
          <div class="stat-grid" id="analyticsStats"></div>

          <!-- Log feed -->
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

        <!-- Right: Per-crate breakdown -->
        <div style="display:flex;flex-direction:column;gap:14px">
          <div class="card">
            <div class="card-header"><div class="card-title"><span class="card-accent"></span>PER CRATE STATS</div></div>
            <div id="crateBreakdown" style="display:flex;flex-direction:column;gap:8px"></div>
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

    // Listen to WS live feed
    WS.on('CRATE_OPEN', data => this.onLiveOpen(data));
    WS.on('SERVER_STATS', data => this.onServerStats(data));
  },

  async loadStats() {
    try {
      const stats = await API.getStats();
      this.renderStats(stats);
      this.renderCrateBreakdown(stats);
    } catch(e) {
      console.warn('Stats load failed:', e.message);
    }
  },

  async loadLogs() {
    try {
      const res = await API.getLogs({ limit: 50 });
      this.logs = res.data || res || [];
      this.renderLogs();
    } catch(e) {
      console.warn('Logs load failed:', e.message);
    }
  },

  renderStats(stats) {
    const container = Utils.qs('#analyticsStats'); if (!container) return;
    const total = stats.totalOpenings || 0;
    container.innerHTML = '';
    const cards = [
      ['📦', Utils.num(total),         'Total Openings',  null, 'rgba(0,229,212,.08)'],
      ['🔑', Utils.num(total),         'Keys Used',       null, 'rgba(245,166,35,.08)'],
      ['💎', Utils.num(Math.floor(total*0.05)), 'Rare Drops', 5, 'rgba(155,89,245,.08)'],
      ['🏆', Utils.num(Math.floor(total*0.01)), 'Pity Triggers', 1,'rgba(68,136,255,.08)'],
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
        <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:4px">
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

    // Top rewards count
    const topRewards = Utils.qs('#topRewards'); if (!topRewards) return;
    topRewards.innerHTML = '';
    const allRewards = Object.values(State.crates).flatMap(c => c.rewards||[]);
    allRewards.slice(0,5).forEach(r => {
      const color = Utils.rarityColor(r.rarity);
      const div = Utils.el('div', '', `
        <div style="display:flex;align-items:center;gap:8px;padding:5px 0">
          <span style="font-size:18px">${Utils.materialIcon(r.material)}</span>
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
