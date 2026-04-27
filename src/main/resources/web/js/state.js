/* ══ GLOBAL STATE ══ */
const State = {
  jwt: null,
  serverUrl: 'http://localhost:7420',
  currentPage: 'architect',
  currentCrateId: null,
  crates: {},          // id → crate object
  crateOrder: [],      // ordered ids
  messages: {},        // key → value from server
  serverStatus: null,
  openingsToday: 0,
  demoMode: false,

  get currentCrate() {
    return this.currentCrateId ? this.crates[this.currentCrateId] : null;
  },

  setCrate(crate) {
    this.crates[crate.id] = crate;
    if (!this.crateOrder.includes(crate.id)) this.crateOrder.push(crate.id);
  },

  deleteCrate(id) {
    delete this.crates[id];
    this.crateOrder = this.crateOrder.filter(i => i !== id);
    if (this.currentCrateId === id) {
      this.currentCrateId = this.crateOrder[0] || null;
    }
  }
};
