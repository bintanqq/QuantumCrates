/* ══ API CLIENT ══ */
const API = {
  get base() { return State.serverUrl + '/api'; },

  headers() {
    return {
      'Content-Type': 'application/json',
      ...(State.jwt ? { Authorization: 'Bearer ' + State.jwt } : {})
    };
  },

  async request(method, path, body) {
    if (State.demoMode) return Demo.handle(method, path, body);
    try {
      const res = await fetch(this.base + path, {
        method,
        headers: this.headers(),
        body: body ? JSON.stringify(body) : undefined
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Request failed');
      return data;
    } catch (e) {
      if (e.name === 'TypeError') throw new Error('Cannot connect to server. Is it running?');
      throw e;
    }
  },

  get:    (p)    => API.request('GET',    p),
  post:   (p, b) => API.request('POST',   p, b),
  delete: (p)    => API.request('DELETE', p),

  /* ── Auth ── */
  login:  (token) => API.post('/auth/login', { token }),

  /* ── Crates ── */
  getCrates:      ()     => API.get('/crates'),
  getCrate:       (id)   => API.get('/crates/' + id),
  saveCrate:      (id,c) => API.post('/crates/' + id, c),
  deleteCrate:    (id)   => API.delete('/crates/' + id),
  reloadCrates:   ()     => API.post('/crates/reload'),

  /* ── Keys ── */
  giveKey: (uuid, keyId, amount) => API.post('/keys/give', { uuid, keyId, amount }),
  getKeyBalance: (keyId, uuid)   => API.get(`/keys/${keyId}/balance/${uuid}`),

  /* ── Logs ── */
  getLogs:  (params={}) => API.get('/logs?' + new URLSearchParams(params).toString()),
  getStats: (crateId)   => API.get('/logs/stats' + (crateId ? '?crate=' + crateId : '')),

  /* ── Players ── */
  getPlayerPity:  (uuid)        => API.get(`/players/${uuid}/pity`),
  resetPlayerPity:(uuid,crate)  => API.post(`/players/${uuid}/pity/reset${crate?'?crate='+crate:''}`),

  /* ── Server ── */
  getServerStatus: () => API.get('/server/status'),

  /* ── Messages ── */
  getMessages:  ()    => API.get('/config/messages'),
  saveMessages: (msg) => API.post('/config/messages', msg),
};

/* ══ DEMO DATA ══ */
const Demo = {
  handle(method, path) {
    if (path === '/crates') return { data: Object.values(DEMO_CRATES) };
    if (path.startsWith('/crates/') && method === 'GET') {
      const id = path.split('/')[2];
      return DEMO_CRATES[id] || { id, displayName: 'Unknown', rewards: [] };
    }
    if (path === '/server/status') return DEMO_STATUS;
    if (path === '/logs') return { data: DEMO_LOGS, count: DEMO_LOGS.length };
    if (path === '/logs/stats') return DEMO_STATS;
    if (path === '/config/messages') return DEMO_MESSAGES;
    return { status: 'ok', message: 'Demo mode — changes not saved.' };
  }
};

const DEMO_STATUS = { online: true, onlinePlayers: 42, maxPlayers: 100, tps: 19.97, crateCount: 3, timestamp: Date.now() };
const DEMO_STATS  = { totalOpenings: 12846, perCrate: { legendary_crate: 6200, epic_crate: 4200, rare_crate: 2446 } };
const DEMO_CRATES = {
  legendary_crate: {
    id:'legendary_crate', displayName:'&6&lLegendary Crate', enabled:true,
    cooldownMs:3600000, massOpenEnabled:true, massOpenLimit:64,
    pity:{ enabled:true, threshold:100, softPityStart:80, rareRarityMinimum:'LEGENDARY', bonusChancePerOpen:2 },
    preview:{ title:null, sortOrder:'RARITY_DESC', borderMaterial:null, showChance:true, showWeight:false, showPity:true, showKeyBalance:true, chanceFormat:'&7Chance: &e{chance}', rewardFooterLore:[], showActualItem:true },
    requiredKeys:[{ keyId:'legendary_key', amount:1, type:'VIRTUAL' }],
    rewards:[
      { id:'dragon_sword',  displayName:'&6Dragon Sword',  weight:0.5,  rarity:'LEGENDARY', type:'VANILLA', material:'DIAMOND_SWORD', amount:1, broadcast:true,  broadcastMessage:'&e{player} won &6Dragon Sword&7!', commands:[], lore:[], iconUrl:'' },
      { id:'quantum_armor', displayName:'&5Quantum Armor', weight:1.0,  rarity:'EPIC',      type:'VANILLA', material:'NETHERITE_CHESTPLATE', amount:1, broadcast:false, broadcastMessage:'', commands:[], lore:[], iconUrl:'' },
      { id:'photon_bow',    displayName:'&bPhoton Bow',    weight:1.5,  rarity:'RARE',      type:'VANILLA', material:'BOW', amount:1, broadcast:false, broadcastMessage:'', commands:[], lore:[], iconUrl:'' },
      { id:'credits_10k',   displayName:'&a10,000 Credits',weight:5.0,  rarity:'UNCOMMON',  type:'COMMAND', material:null, amount:1, broadcast:false, broadcastMessage:'', commands:['console: eco give {player} 10000'], lore:[], iconUrl:'' },
      { id:'diamond',       displayName:'&7Diamond',       weight:15.0, rarity:'COMMON',    type:'VANILLA', material:'DIAMOND', amount:1, broadcast:false, broadcastMessage:'', commands:[], lore:[], iconUrl:'' },
      { id:'emerald',       displayName:'&aEmerald',       weight:25.0, rarity:'COMMON',    type:'VANILLA', material:'EMERALD', amount:2, broadcast:false, broadcastMessage:'', commands:[], lore:[], iconUrl:'' },
    ]
  },
  epic_crate: {
    id:'epic_crate', displayName:'&5&lEpic Crate', enabled:true, cooldownMs:1800000,
    massOpenEnabled:true, massOpenLimit:32,
    pity:{ enabled:true, threshold:50, softPityStart:40, rareRarityMinimum:'EPIC', bonusChancePerOpen:3 },
    preview:{ title:null, sortOrder:'RARITY_DESC', borderMaterial:null, showChance:true, showWeight:false, showPity:true, showKeyBalance:true, chanceFormat:'&7Chance: &e{chance}', rewardFooterLore:[], showActualItem:true },
    requiredKeys:[{ keyId:'epic_key', amount:1, type:'VIRTUAL' }],
    rewards:[
      { id:'epic_sword', displayName:'&5Epic Sword', weight:2.0, rarity:'EPIC', type:'VANILLA', material:'IRON_SWORD', amount:1, broadcast:false, broadcastMessage:'', commands:[], lore:[], iconUrl:'' },
      { id:'gold',       displayName:'&6Gold Block',  weight:10.0,rarity:'UNCOMMON',type:'VANILLA',material:'GOLD_BLOCK',amount:1,broadcast:false,broadcastMessage:'',commands:[],lore:[],iconUrl:'' },
      { id:'iron',       displayName:'&7Iron Block',  weight:20.0,rarity:'COMMON',  type:'VANILLA',material:'IRON_BLOCK',amount:1,broadcast:false,broadcastMessage:'',commands:[],lore:[],iconUrl:'' },
    ]
  },
};

const DEMO_LOGS = [
  { uuid:'abc1', playerName:'Bintang',  crateId:'legendary_crate', rewardId:'dragon_sword',  rewardDisplay:'Dragon Sword',   pityAtOpen:99, timestamp:Date.now()-60000,  world:'world', x:0,y:64,z:0 },
  { uuid:'abc2', playerName:'Rezz',     crateId:'epic_crate',      rewardId:'epic_sword',    rewardDisplay:'Epic Sword',     pityAtOpen:12, timestamp:Date.now()-120000, world:'world', x:0,y:64,z:0 },
  { uuid:'abc3', playerName:'Miko',     crateId:'legendary_crate', rewardId:'photon_bow',    rewardDisplay:'Photon Bow',     pityAtOpen:5,  timestamp:Date.now()-180000, world:'world', x:0,y:64,z:0 },
  { uuid:'abc4', playerName:'Alfarez',  crateId:'legendary_crate', rewardId:'credits_10k',   rewardDisplay:'10,000 Credits', pityAtOpen:0,  timestamp:Date.now()-240000, world:'world', x:0,y:64,z:0 },
  { uuid:'abc5', playerName:'Vanz',     crateId:'epic_crate',      rewardId:'gold',          rewardDisplay:'Gold Block',     pityAtOpen:3,  timestamp:Date.now()-300000, world:'world', x:0,y:64,z:0 },
  { uuid:'abc6', playerName:'Nanda',    crateId:'legendary_crate', rewardId:'diamond',       rewardDisplay:'Diamond',        pityAtOpen:0,  timestamp:Date.now()-360000, world:'world', x:0,y:64,z:0 },
  { uuid:'abc7', playerName:'Dapa',     crateId:'legendary_crate', rewardId:'dragon_sword',  rewardDisplay:'Dragon Sword',   pityAtOpen:100,timestamp:Date.now()-420000, world:'world', x:0,y:64,z:0 },
  { uuid:'abc8', playerName:'Zaky',     crateId:'epic_crate',      rewardId:'iron',          rewardDisplay:'Iron Block',     pityAtOpen:0,  timestamp:Date.now()-480000, world:'world', x:0,y:64,z:0 },
];

const DEMO_MESSAGES = {
  prefix:'&8[&bQuantumCrates&8] &r',
  'reward-received':'&aSelamat! Kamu mendapat: &e{reward}',
  'no-permission':'&cKamu tidak punya permission.',
  'cooldown-active':'&cTunggu &e{time} &csebelum membuka crate ini lagi.',
};
