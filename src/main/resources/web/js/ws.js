/* ══ WEBSOCKET CLIENT ══ */
const WS = {
  socket: null, reconnectTimer: null, reconnectDelay: 3000, handlers: {},

  connect() {
    if (State.demoMode) { this._startDemoFeed(); return; }
    const url = State.serverUrl.replace(/^http/, 'ws') + '/ws?token=' + State.jwt;
    this.socket = new WebSocket(url);
    this.socket.onopen    = ()  => { console.log('[WS] Connected'); this.reconnectDelay = 3000; };
    this.socket.onmessage = (e) => this._handle(JSON.parse(e.data));
    this.socket.onclose   = ()  => { console.log('[WS] Closed'); this._scheduleReconnect(); };
    this.socket.onerror   = ()  => {};
    setInterval(() => this.socket?.readyState === 1 && this.socket.send(JSON.stringify({type:'PING'})), 20000);
  },

  disconnect() {
    clearTimeout(this.reconnectTimer);
    this.socket?.close();
  },

  on(type, fn) {
    (this.handlers[type] = this.handlers[type] || []).push(fn);
    return this;
  },

  _handle(data) {
    (this.handlers[data.type] || []).forEach(fn => fn(data));
    (this.handlers['*'] || []).forEach(fn => fn(data));
  },

  _scheduleReconnect() {
    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = setTimeout(() => this.connect(), this.reconnectDelay);
    this.reconnectDelay = Math.min(this.reconnectDelay * 1.5, 30000);
  },

  _startDemoFeed() {
    const players = ['Bintang','Rezz','Miko','Alfarez','Vanz','Nanda','Dapa','Zaky'];
    const crates  = Object.keys(DEMO_CRATES);
    const getRewards = cId => DEMO_CRATES[cId]?.rewards || [];
    setInterval(() => {
      const cId = crates[Math.floor(Math.random() * crates.length)];
      const rewards = getRewards(cId); if (!rewards.length) return;
      const r = rewards[Math.floor(Math.random() * rewards.length)];
      this._handle({
        type: 'CRATE_OPEN',
        uuid: 'demo-' + Math.random(),
        playerName: players[Math.floor(Math.random() * players.length)],
        crateId: cId,
        rewardId: r.id,
        rewardDisplay: r.displayName,
        pityAtOpen: Math.floor(Math.random() * 50),
        timestamp: Date.now(), world:'world', x:0, y:64, z:0
      });
    }, 3500);
    setInterval(() => {
      this._handle({ type:'SERVER_STATS', onlinePlayers: 30+Math.floor(Math.random()*20), tps:19+Math.random(), openingsToday: State.openingsToday });
    }, 8000);
  }
};
