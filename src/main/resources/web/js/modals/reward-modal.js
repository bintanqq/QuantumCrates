/* ══ MODAL: ADD / EDIT REWARD ══ */
const RewardModal = {
  selectedRarity: 'COMMON',
  iconUrl: '',
  callback: null,
  editing: null,

  open(reward, onSave) {
    this.callback = onSave;
    this.editing  = reward;
    this.selectedRarity = reward?.rarity || 'COMMON';
    this.iconUrl  = reward?.iconUrl || '';

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            ${reward ? 'Edit Reward' : 'Add Reward'}
          </div>
          <div class="modal-subtitle">Configure reward properties and icon.</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>
      <div class="modal-body">
        <!-- Row 1: ID + Name -->
        <div class="field-row" style="margin-bottom:12px">
          <div class="field-group">
            <label class="field-label">Reward ID *</label>
            <input class="field-input" id="rwId" value="${reward?.id||''}" placeholder="dragon_sword" autofocus/>
          </div>
          <div class="field-group">
            <label class="field-label">Display Name *</label>
            <input class="field-input" id="rwName" value="${reward?.displayName||''}" placeholder="&6Dragon Sword"/>
          </div>
        </div>

        <!-- Icon upload + material -->
        <div style="display:flex;gap:12px;margin-bottom:12px;align-items:flex-start">
          <div style="flex-shrink:0">
            <label class="field-label" style="margin-bottom:5px">Reward Icon</label>
            <div class="icon-drop" id="iconDrop" onclick="document.getElementById('iconFile').click()" title="Click or drag to upload">
              ${this.iconUrl ? `<img class="icon-drop-preview" src="${this.iconUrl}" id="iconPreview"/>` : `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity=".4"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>`}
              <div class="icon-drop-overlay" id="iconOverlay">${this.iconUrl ? '🔄 Change' : '📁 Upload PNG/GIF'}</div>
            </div>
            <input type="file" id="iconFile" accept="image/*,.gif" style="display:none" onchange="RewardModal.handleIcon(event)"/>
          </div>
          <div style="flex:1;display:flex;flex-direction:column;gap:8px">
            <div class="field-group">
              <label class="field-label">Type</label>
              <select class="field-input" id="rwType" onchange="RewardModal.toggleTypeFields()">
                ${['VANILLA','COMMAND','VANILLA_WITH_COMMANDS','MMOITEMS','ITEMSADDER','ORAXEN'].map(t =>
                  `<option value="${t}" ${reward?.type===t?'selected':''}>${t.replace(/_/g,' ')}</option>`).join('')}
              </select>
            </div>
            <div class="field-group" id="rwMatGroup">
              <label class="field-label">Material (Bukkit)</label>
              <input class="field-input" id="rwMat" value="${reward?.material||'TRIPWIRE_HOOK'}" placeholder="DIAMOND_SWORD"/>
            </div>
            <div class="field-group" id="rwAmtGroup">
              <label class="field-label">Amount</label>
              <input class="field-input" type="number" id="rwAmt" value="${reward?.amount||1}" min="1" max="64"/>
            </div>
          </div>
        </div>

        <!-- Rarity -->
        <div class="field-group" style="margin-bottom:12px">
          <label class="field-label">Rarity</label>
          <div class="rarity-picker" id="rarityPicker">
            ${['COMMON','UNCOMMON','RARE','EPIC','LEGENDARY','MYTHIC'].map(r =>
              `<div class="rarity-opt${this.selectedRarity===r?' active':''}" data-r="${r}" onclick="RewardModal.pickRarity(this,'${r}')">${r}</div>`
            ).join('')}
          </div>
        </div>

        <!-- Weight + Broadcast row -->
        <div class="field-row" style="margin-bottom:12px">
          <div class="field-group">
            <label class="field-label">Weight (higher = more common)</label>
            <input class="field-input" type="number" id="rwWeight" value="${reward?.weight||10}" min="0.01" step="0.5"/>
          </div>
          <div class="field-group">
            <label class="field-label">Broadcast on Win?</label>
            <select class="field-input" id="rwBroadcast">
              <option value="false" ${!reward?.broadcast?'selected':''}>No</option>
              <option value="true"  ${reward?.broadcast?'selected':''}>Yes</option>
            </select>
          </div>
        </div>

        <!-- Broadcast message (shows when broadcast=yes) -->
        <div class="field-group" id="broadcastMsgGroup" style="${reward?.broadcast?'':'display:none'}margin-bottom:12px">
          <label class="field-label">Broadcast Message</label>
          <input class="field-input" id="rwBroadcastMsg" value="${reward?.broadcastMessage||'&e{player} &7won &6{reward}&7!'}" placeholder="&e{player} &7won &6{reward}!"/>
        </div>

        <!-- Custom Model Data -->
        <div class="field-group" style="margin-bottom:12px">
          <label class="field-label">Custom Model Data <span style="color:var(--text3)">(-1 = disabled)</span></label>
          <input class="field-input" type="number" id="rwCmd" value="${reward?.customModelData ?? -1}"/>
        </div>

        <!-- Commands -->
        <div class="field-group" id="rwCmdsGroup" style="margin-bottom:0">
          <label class="field-label">Commands <span style="color:var(--text3)">(one per line · prefix: console: or player:)</span></label>
          <textarea class="field-input" id="rwCmds" rows="3" placeholder="console: eco give {player} 10000&#10;player: say I got a reward!">${(reward?.commands||[]).join('\n')}</textarea>
        </div>

        <!-- Lore -->
        <div class="field-group" style="margin-top:10px">
          <label class="field-label">Lore <span style="color:var(--text3)">(one line per row, supports &amp; codes)</span></label>
          <textarea class="field-input" id="rwLore" rows="2" placeholder="&7A powerful weapon of legend.">${(reward?.lore||[]).join('\n')}</textarea>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-primary" onclick="RewardModal.save()">
          ${reward ? '✓ Update Reward' : '+ Add Reward'}
        </button>
      </div>
    `, 'modal-lg');

    Utils.qs('#rwBroadcast').onchange = e => {
      Utils.qs('#broadcastMsgGroup').style.display = e.target.value==='true' ? '' : 'none';
    };

    this.toggleTypeFields();
    this._bindDrop();
  },

  toggleTypeFields() {
    const type = Utils.qs('#rwType')?.value || 'VANILLA';
    const isCmd = type === 'COMMAND';
    const hasMat = !isCmd;
    if (Utils.qs('#rwMatGroup')) Utils.qs('#rwMatGroup').style.display = hasMat ? '' : 'none';
    if (Utils.qs('#rwAmtGroup')) Utils.qs('#rwAmtGroup').style.display = hasMat ? '' : 'none';
  },

  pickRarity(el, rarity) {
    this.selectedRarity = rarity;
    Utils.qsa('.rarity-opt').forEach(o => o.classList.remove('active'));
    el.classList.add('active');
  },

  handleIcon(e) {
    const file = e.target.files[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
      this.iconUrl = ev.target.result;
      const drop = Utils.qs('#iconDrop');
      if (drop) {
        drop.innerHTML = `<img class="icon-drop-preview" src="${this.iconUrl}"/><div class="icon-drop-overlay">🔄 Change</div>`;
        this._bindDrop();
      }
    };
    reader.readAsDataURL(file);
  },

  _bindDrop() {
    const drop = Utils.qs('#iconDrop'); if (!drop) return;
    drop.ondragover = e => { e.preventDefault(); drop.classList.add('drag'); };
    drop.ondragleave= () => drop.classList.remove('drag');
    drop.ondrop     = e => { e.preventDefault(); drop.classList.remove('drag'); this.handleIcon({ target: { files: e.dataTransfer.files } }); };
  },

  save() {
    const id   = Utils.qs('#rwId')?.value?.trim();
    const name = Utils.qs('#rwName')?.value?.trim();
    if (!id || !name) { toast('ID and Display Name are required', 'error'); return; }

    const reward = {
      id, displayName: name,
      rarity:          this.selectedRarity,
      type:            Utils.qs('#rwType')?.value || 'VANILLA',
      material:        Utils.qs('#rwMat')?.value?.trim() || null,
      amount:          parseInt(Utils.qs('#rwAmt')?.value) || 1,
      weight:          parseFloat(Utils.qs('#rwWeight')?.value) || 10,
      broadcast:       Utils.qs('#rwBroadcast')?.value === 'true',
      broadcastMessage:Utils.qs('#rwBroadcastMsg')?.value || '',
      customModelData: parseInt(Utils.qs('#rwCmd')?.value) || -1,
      commands:        (Utils.qs('#rwCmds')?.value || '').split('\n').map(s=>s.trim()).filter(Boolean),
      lore:            (Utils.qs('#rwLore')?.value || '').split('\n').map(s=>s.trim()).filter(Boolean),
      iconUrl:         this.iconUrl,
    };

    this.callback?.(reward);
    Modal.close();
    toast(`Reward "${Utils.strip(name)}" ${this.editing ? 'updated' : 'added'} ✓`, 'success');
  },
};
