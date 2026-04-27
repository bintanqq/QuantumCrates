/* ══ PAGE: MESSAGES ══ */
const Messages = {
  data: {}, dirty: false,

  render(container) {
    container.innerHTML = `
      <div class="page-header">
        <div><div class="page-title">Messages</div><div class="page-sub">All plugin messages — zero hardcoded. Supports & color codes.</div></div>
        <div class="page-actions">
          <button class="btn btn-ghost btn-sm" onclick="Messages.reset()">↩ Reset to Default</button>
          <button class="btn btn-primary btn-sm" id="btnSaveMessages">💾 Save Messages</button>
        </div>
      </div>
      <div class="msg-grid" id="msgGrid">
        <div class="empty-state"><div class="spin">⟳</div><p>Loading messages...</p></div>
      </div>
    `;
    Utils.on(Utils.qs('#btnSaveMessages'), 'click', () => this.save());
    this.load();
  },

  async load() {
    try {
      this.data = await API.getMessages();
      this.renderGrid();
    } catch (e) {
      this.data = DEMO_MESSAGES;
      this.renderGrid();
    }
  },

  renderGrid() {
    const grid = Utils.qs('#msgGrid'); if (!grid) return;
    grid.innerHTML = '';
    Object.entries(this.data).forEach(([key, val]) => {
      const item = Utils.el('div', 'msg-item');
      item.innerHTML = `
        <div class="msg-key">${key}</div>
        <textarea class="msg-input" data-key="${key}" rows="1">${val}</textarea>
        <div class="msg-preview">${Utils.mc(val)}</div>
      `;
      const ta = item.querySelector('textarea');
      ta.oninput = Utils.debounce(() => {
        this.data[key] = ta.value;
        item.querySelector('.msg-preview').innerHTML = Utils.mc(ta.value);
        this.dirty = true;
      }, 200);
      // Auto-resize textarea
      ta.oninput = (e) => {
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
        this.data[key] = ta.value;
        item.querySelector('.msg-preview').innerHTML = Utils.mc(ta.value);
        this.dirty = true;
      };
      grid.appendChild(item);
    });
  },

  async save() {
    try {
      await API.saveMessages(this.data);
      this.dirty = false;
      toast('Messages saved & synced to server ✓', 'success');
    } catch (e) {
      toast(e.message, 'error');
    }
  },

  reset() {
    if (confirm('Reset all messages to server defaults?')) this.load();
  },
};
