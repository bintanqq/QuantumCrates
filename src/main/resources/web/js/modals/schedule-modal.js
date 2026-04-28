/* ══ MODAL: CRATE SCHEDULE ══
   Handles all 4 schedule modes: ALWAYS, TIME_WINDOW, DAYS_OF_WEEK, EVENT
*/
const ScheduleModal = {
  _crate: null,
  _onSave: null,

  open(crate, onSave) {
    this._crate  = crate;
    this._onSave = onSave;

    const sch = crate.schedule || { mode: 'ALWAYS', timezone: 'UTC' };

    const modeOpts = ['ALWAYS','TIME_WINDOW','DAYS_OF_WEEK','EVENT'].map(m =>
      `<option value="${m}" ${(sch.mode||'ALWAYS')===m?'selected':''}>${{
        ALWAYS:'Always Open',TIME_WINDOW:'Daily Time Window',
        DAYS_OF_WEEK:'Specific Days of Week',EVENT:'Limited-Time Event'
      }[m]}</option>`
    ).join('');

    // Day names (ISO 1=Mon…7=Sun)
    const dayLabels = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
    const daysHtml = dayLabels.map((d,i) => {
      const val = i+1;
      const checked = (sch.daysOfWeek||[]).includes(val) ? 'checked' : '';
      return `<label style="display:flex;align-items:center;gap:4px;padding:5px 10px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm);cursor:pointer;font-size:11.5px;font-weight:600;transition:all .15s" id="dayLabel${val}">
        <input type="checkbox" value="${val}" ${checked} class="dow-check" style="display:none" onchange="ScheduleModal._updateDayLabel(${val},this.checked)"/>
        <span id="dayTxt${val}">${d}</span>
      </label>`;
    }).join('');

    // Event timestamps → datetime-local string
    const toDateLocal = (ms) => {
      if (!ms) return '';
      const d = new Date(ms);
      return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')
        +'T'+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
    };

    Modal.open(`
      <div class="modal-head">
        <div class="modal-head-left">
          <div class="modal-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--cyan)" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
            Crate Schedule
          </div>
          <div class="modal-subtitle">${Utils.strip(crate.displayName||crate.id)} — control when this crate can be opened</div>
        </div>
        <button class="modal-close" onclick="Modal.close()">✕</button>
      </div>

      <div class="modal-body">

        <!-- Mode selector -->
        <div class="field-group" style="margin-bottom:16px">
          <label class="field-label">Schedule Mode</label>
          <select class="field-input" id="schMode" onchange="ScheduleModal._onModeChange()">
            ${modeOpts}
          </select>
        </div>

        <!-- Timezone (shown for all modes except ALWAYS) -->
        <div class="field-group" id="schTimezoneGroup" style="margin-bottom:14px;display:none">
          <label class="field-label">Timezone</label>
          <select class="field-input" id="schTimezone">
            ${[
              'UTC','Asia/Jakarta','Asia/Singapore','Asia/Tokyo','Asia/Shanghai',
              'America/New_York','America/Chicago','America/Denver','America/Los_Angeles',
              'Europe/London','Europe/Paris','Europe/Berlin','Australia/Sydney'
            ].map(tz => `<option value="${tz}" ${(sch.timezone||'UTC')===tz?'selected':''}>${tz}</option>`).join('')}
          </select>
          <div style="font-size:10.5px;color:var(--text3);margin-top:3px">Server timezone for schedule calculations.</div>
        </div>

        <!-- TIME_WINDOW fields -->
        <div id="schTimeWindowFields" style="display:none">
          <div style="padding:12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm);margin-bottom:14px">
            <div style="font-size:11.5px;color:var(--text2);margin-bottom:10px">
              Crate will be openable between the two times, every day.
              Overnight windows are supported (e.g. 22:00 – 02:00).
            </div>
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">Open At</label>
                <input class="field-input" type="time" id="schStart" value="${sch.startTime||'20:00'}"/>
              </div>
              <div class="field-group">
                <label class="field-label">Close At</label>
                <input class="field-input" type="time" id="schEnd" value="${sch.endTime||'22:00'}"/>
              </div>
            </div>
          </div>
        </div>

        <!-- DAYS_OF_WEEK fields -->
        <div id="schDowFields" style="display:none">
          <div style="padding:12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm);margin-bottom:14px">
            <div style="font-size:11.5px;color:var(--text2);margin-bottom:10px">
              Select which days this crate is available. Optionally restrict to a time window.
            </div>
            <div class="section-label" style="margin-bottom:6px">Days of Week</div>
            <div style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:12px" id="dowCheckboxes">
              ${daysHtml}
            </div>
            <div id="schDowToggle" style="margin-bottom:10px"></div>
            <div id="schDowTimeFields" style="display:none">
              <div class="field-row">
                <div class="field-group">
                  <label class="field-label">Open At</label>
                  <input class="field-input" type="time" id="schDowStart" value="${sch.startTime||'20:00'}"/>
                </div>
                <div class="field-group">
                  <label class="field-label">Close At</label>
                  <input class="field-input" type="time" id="schDowEnd" value="${sch.endTime||'22:00'}"/>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- EVENT fields -->
        <div id="schEventFields" style="display:none">
          <div style="padding:12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm);margin-bottom:14px">
            <div style="font-size:11.5px;color:var(--text2);margin-bottom:10px">
              Crate is only openable between two absolute timestamps — perfect for limited-time events.
            </div>
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">Event Start</label>
                <input class="field-input" type="datetime-local" id="schEventStart" value="${toDateLocal(sch.eventStart)}"/>
              </div>
              <div class="field-group">
                <label class="field-label">Event End</label>
                <input class="field-input" type="datetime-local" id="schEventEnd" value="${toDateLocal(sch.eventEnd)}"/>
              </div>
            </div>
            <div id="schEventStatus" style="margin-top:8px;font-size:11px"></div>
          </div>
        </div>

        <!-- ALWAYS info -->
        <div id="schAlwaysInfo" style="padding:12px;background:var(--green-dim);border:1px solid rgba(34,217,138,.2);border-radius:var(--radius-sm)">
          <div style="color:var(--green);font-weight:600;font-size:12px;margin-bottom:4px">✓ No Restrictions</div>
          <div style="color:var(--text2);font-size:11.5px">This crate can be opened at any time. To add time restrictions, select a different schedule mode above.</div>
        </div>

        <!-- Preview -->
        <div id="schPreview" style="margin-top:12px;padding:10px 12px;background:var(--bg3);border:1px solid var(--border);border-radius:var(--radius-sm);font-size:11.5px;color:var(--text3)">
          <span style="color:var(--text2);font-weight:600">Next Open: </span>
          <span id="schPreviewText">Always available</span>
        </div>

      </div>

      <div class="modal-foot">
        <button class="btn btn-ghost" onclick="Modal.close()">Cancel</button>
        <button class="btn btn-danger btn-sm" onclick="ScheduleModal.clearSchedule()" style="margin-right:auto">Remove Schedule</button>
        <button class="btn btn-primary" onclick="ScheduleModal.save()">✓ Save Schedule</button>
      </div>
    `, 'modal-lg');

    // Bind dow time toggle
    const dowToggleEl = Utils.qs('#schDowToggle');
    if (dowToggleEl) {
      dowToggleEl.appendChild(ToggleSwitch(
        'Restrict to time window',
        !!(sch.startTime && sch.mode === 'DAYS_OF_WEEK'),
        (v) => { Utils.qs('#schDowTimeFields').style.display = v ? '' : 'none'; }
      ));
    }

    // Init day label colors
    (sch.daysOfWeek||[]).forEach(d => this._updateDayLabel(d, true));

    // Init event status
    if (sch.mode === 'EVENT') this._updateEventStatus();

    Utils.qs('#schEventStart')?.addEventListener('change', () => this._updateEventStatus());
    Utils.qs('#schEventEnd')?.addEventListener('change', () => this._updateEventStatus());

    this._onModeChange();
  },

  _onModeChange() {
    const mode = Utils.qs('#schMode')?.value || 'ALWAYS';
    Utils.qs('#schAlwaysInfo').style.display     = mode === 'ALWAYS'       ? '' : 'none';
    Utils.qs('#schTimeWindowFields').style.display = mode === 'TIME_WINDOW'  ? '' : 'none';
    Utils.qs('#schDowFields').style.display       = mode === 'DAYS_OF_WEEK' ? '' : 'none';
    Utils.qs('#schEventFields').style.display     = mode === 'EVENT'        ? '' : 'none';
    Utils.qs('#schTimezoneGroup').style.display   = mode !== 'ALWAYS'       ? '' : 'none';
    this._updatePreview();
  },

  _updateDayLabel(val, checked) {
    const lbl = Utils.qs(`#dayLabel${val}`);
    const inp = Utils.qs(`#dayLabel${val} input`);
    if (!lbl) return;
    if (inp) inp.checked = checked;
    lbl.style.background    = checked ? 'var(--cyan-dim2)' : 'var(--bg3)';
    lbl.style.borderColor   = checked ? 'rgba(0,74,173,.4)' : 'var(--border)';
    lbl.style.color         = checked ? 'var(--cyan)' : '';
    lbl.onclick = (e) => {
      if (e.target === inp) return;
      inp.checked = !inp.checked;
      this._updateDayLabel(val, inp.checked);
    };
  },

  _updateEventStatus() {
    const el = Utils.qs('#schEventStatus'); if (!el) return;
    const start = Utils.qs('#schEventStart')?.value;
    const end   = Utils.qs('#schEventEnd')?.value;
    if (!start || !end) { el.textContent = ''; return; }
    const now       = Date.now();
    const startMs   = new Date(start).getTime();
    const endMs     = new Date(end).getTime();
    if (endMs <= startMs) {
      el.innerHTML = '<span style="color:var(--red)">⚠ End time must be after start time</span>';
      return;
    }
    if (now < startMs) {
      const diff = startMs - now;
      el.innerHTML = `<span style="color:var(--gold)">⏳ Event starts in ${Utils.duration(diff)}</span>`;
    } else if (now >= startMs && now <= endMs) {
      const diff = endMs - now;
      el.innerHTML = `<span style="color:var(--green)">✓ Event is LIVE — ends in ${Utils.duration(diff)}</span>`;
    } else {
      el.innerHTML = '<span style="color:var(--text3)">Event has ended.</span>';
    }
  },

  _updatePreview() {
    const el   = Utils.qs('#schPreviewText'); if (!el) return;
    const mode = Utils.qs('#schMode')?.value || 'ALWAYS';
    switch (mode) {
      case 'ALWAYS':      el.textContent = 'Always available'; break;
      case 'TIME_WINDOW': {
        const s = Utils.qs('#schStart')?.value || '?';
        const e = Utils.qs('#schEnd')?.value   || '?';
        el.textContent = `Daily ${s} – ${e} (${Utils.qs('#schTimezone')?.value||'UTC'})`;
        break;
      }
      case 'DAYS_OF_WEEK': {
        const days = [...Utils.qsa('.dow-check')].filter(c=>c.checked).map(c=>
          ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'][parseInt(c.value)-1]);
        el.textContent = days.length ? days.join(', ') : 'No days selected';
        break;
      }
      case 'EVENT': {
        const s = Utils.qs('#schEventStart')?.value;
        const e = Utils.qs('#schEventEnd')?.value;
        el.textContent = s && e ? `${s} → ${e}` : 'Set start and end above';
        break;
      }
    }
  },

  clearSchedule() {
    if (!confirm('Remove schedule? Crate will be always open.')) return;
    this._crate.schedule = null;
    this._onSave?.();
    Modal.close();
    toast('Schedule removed — crate is now always open.', 'info');
  },

  save() {
    const mode = Utils.qs('#schMode')?.value || 'ALWAYS';
    const tz   = Utils.qs('#schTimezone')?.value || 'UTC';

    if (mode === 'ALWAYS') {
      this._crate.schedule = null;
      this._onSave?.();
      Modal.close();
      toast('Schedule saved — crate is always open.', 'success');
      return;
    }

    const sch = { mode, timezone: tz };

    if (mode === 'TIME_WINDOW') {
      const s = Utils.qs('#schStart')?.value;
      const e = Utils.qs('#schEnd')?.value;
      if (!s || !e) { toast('Set both open and close times', 'error'); return; }
      sch.startTime = s;
      sch.endTime   = e;
    }

    if (mode === 'DAYS_OF_WEEK') {
      const days = [...Utils.qsa('.dow-check')].filter(c=>c.checked).map(c=>parseInt(c.value));
      if (!days.length) { toast('Select at least one day', 'error'); return; }
      sch.daysOfWeek = days;
      const hasWindow = Utils.qs('#schDowTimeFields')?.style.display !== 'none';
      if (hasWindow) {
        const s = Utils.qs('#schDowStart')?.value;
        const e = Utils.qs('#schDowEnd')?.value;
        if (s) sch.startTime = s;
        if (e) sch.endTime   = e;
      }
    }

    if (mode === 'EVENT') {
      const sEl = Utils.qs('#schEventStart');
      const eEl = Utils.qs('#schEventEnd');
      if (!sEl?.value || !eEl?.value) { toast('Set both event start and end', 'error'); return; }
      const startMs = new Date(sEl.value).getTime();
      const endMs   = new Date(eEl.value).getTime();
      if (endMs <= startMs) { toast('End time must be after start time', 'error'); return; }
      sch.eventStart = startMs;
      sch.eventEnd   = endMs;
    }

    this._crate.schedule = sch;
    this._onSave?.();
    Modal.close();
    toast('Schedule saved ✓', 'success');
  },
};