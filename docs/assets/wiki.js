(function () {
  const DATA = window.WIKI_DATA || {};
  const escapeHtml = (str) => String(str)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');

  const STORAGE_KEY = 'dce_wiki_view';

  function resolveInitialView() {
    try {
      const params = new URLSearchParams(window.location.search);
      const qView = params.get('view');
      if (qView === 'player' || qView === 'admin') {
        localStorage.setItem(STORAGE_KEY, qView);
        return qView;
      }
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'player' || saved === 'admin') return saved;
    } catch (_) {}
    return 'admin';
  }

  let currentView = resolveInitialView();

  function applyViewMode(view) {
    currentView = (view === 'player') ? 'player' : 'admin';
    document.body.setAttribute('data-view', currentView);
    try { localStorage.setItem(STORAGE_KEY, currentView); } catch (_) {}

    document.querySelectorAll('.view-btn[data-set-view]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.setView === currentView);
    });

    renderCommandsTable();
    renderPlaceholdersTable();
    renderStatsTable();
    renderScrollsTable();
    renderPetsTable();
    renderAdminTables();

    const searchInput = document.getElementById('globalSearch');
    if (searchInput && document.getElementById('searchPopover')?.classList.contains('open')) {
      renderSearchPopover(searchInput.value);
    }
  }

  function injectChrome() {
    const activePage = document.body.getAttribute('data-page') || 'index';

    const header = document.createElement('header');
    header.className = 'topbar';
    header.innerHTML = `
      <div class="topbar-inner">
        <button type="button" class="menu-btn" id="mobileMenuBtn" aria-label="Toggle navigation">Menu</button>
        <a class="brand-link" href="index.html">
          <span class="brand-dot"></span>
          <span>DCEssentials Wiki</span>
        </a>

        <div class="search-wrap" id="searchWrap">
          <div class="search-input-box">
            <span class="search-icon" aria-hidden="true">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            </span>
            <input type="text" id="globalSearch" placeholder="Search stats, keystones, commands, placeholders..." autocomplete="off" aria-label="Search documentation" />
            <span class="search-actions">
              <button type="button" class="search-clear" id="searchClearBtn" aria-label="Clear search">&times;</button>
              <span class="search-kbd" id="searchKbd">/</span>
            </span>
          </div>
          <div class="search-popover" id="searchPopover" role="listbox"></div>
        </div>

        <div class="topbar-right">
          <div class="view-toggle" role="group" aria-label="Audience mode">
            <button type="button" class="view-btn" data-set-view="player">Player Wiki</button>
            <button type="button" class="view-btn" data-set-view="admin">Admin View</button>
          </div>
          <div class="topbar-links">
            <a class="top-link" href="commands.html">Commands</a>
            <a class="top-link" href="placeholders.html">Placeholders</a>
          </div>
        </div>
      </div>
    `;

    const backdrop = document.createElement('div');
    backdrop.className = 'sidebar-backdrop';
    backdrop.id = 'sidebarBackdrop';

    document.body.prepend(backdrop);
    document.body.prepend(header);

    const sidebar = document.getElementById('sidebar');
    if (sidebar && DATA.NAV_GROUPS) {
      sidebar.innerHTML = `<nav id="navList">${DATA.NAV_GROUPS.map(g => `
        <div class="nav-group" ${g.items.every(i => i.audience === 'admin') ? 'data-audience="admin"' : ''}>
          <div class="nav-group-label">${escapeHtml(g.name)}</div>
          ${g.items.map(item => `
            <a class="nav-link ${item.id === activePage ? 'active' : ''}"
               href="${escapeHtml(item.file)}"
               ${item.audience === 'admin' ? 'data-audience="admin"' : ''}>${escapeHtml(item.label)}</a>
          `).join('')}
        </div>
      `).join('')}</nav>`;
    }

    if (!document.getElementById('toast')) {
      const toast = document.createElement('div');
      toast.id = 'toast';
      toast.className = 'toast';
      toast.textContent = 'Copied';
      document.body.appendChild(toast);
    }

    document.querySelectorAll('.view-btn[data-set-view]').forEach(btn => {
      btn.addEventListener('click', () => applyViewMode(btn.dataset.setView));
    });

    document.getElementById('mobileMenuBtn')?.addEventListener('click', () => toggleMobileMenu());
    backdrop.addEventListener('click', () => toggleMobileMenu(false));
  }

  function showToast(msg) {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(() => el.classList.remove('show'), 1600);
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => showToast('Copied: ' + text)).catch(() => fallbackCopy(text));
    } else {
      fallbackCopy(text);
    }
  }

  function fallbackCopy(text) {
    const ta = document.createElement('textarea');
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); showToast('Copied: ' + text); } catch (_) {}
    document.body.removeChild(ta);
  }

  function toggleMobileMenu(forceState) {
    const sidebar = document.getElementById('sidebar');
    const backdrop = document.getElementById('sidebarBackdrop');
    if (!sidebar || !backdrop) return;
    const open = typeof forceState === 'boolean' ? forceState : !sidebar.classList.contains('open');
    sidebar.classList.toggle('open', open);
    backdrop.classList.toggle('open', open);
  }

  function wireCopyHandlers() {
    document.body.addEventListener('click', (e) => {
      const lit = e.target.closest('[data-copy-literal]');
      if (lit) {
        copyText(lit.dataset.copyLiteral);
        return;
      }
      const btn = e.target.closest('[data-copy-target]');
      if (btn) {
        const el = document.getElementById(btn.dataset.copyTarget);
        if (el) copyText(el.textContent);
      }
    });
  }

  /* ==========================================================================
   * DYNAMIC DATA TABLE RENDERERS
   * ========================================================================== */

  let activeStatCat = 'ALL';
  function renderStatsTable() {
    const tbody = document.getElementById('statsTableBody');
    if (tbody && DATA.STATS_REGISTRY) {
      const list = DATA.STATS_REGISTRY.filter(s => {
        if (currentView === 'player' && s.audience === 'admin') return false;
        return activeStatCat === 'ALL' || s.category === activeStatCat;
      });
      tbody.innerHTML = list.map(s => {
        const catCls = s.category === 'OFFENSE' ? 'red' : s.category === 'DEFENSE' ? 'blue' : 'green';
        return `
          <tr>
            <td class="nowrap-col"><span style="color:${escapeHtml(s.color)}">${escapeHtml(s.icon)}</span> <strong>${escapeHtml(s.name)}</strong><br><code>${escapeHtml(s.id)}</code></td>
            <td class="nowrap-col"><span class="tag ${catCls}">${escapeHtml(s.category)}</span></td>
            <td><strong>${escapeHtml(s.brief)}</strong><br>${escapeHtml(s.effect)}</td>
            <td class="nowrap-col"><code>${escapeHtml(s.cap)}</code></td>
          </tr>
        `;
      }).join('');
    }

    const srcList = document.getElementById('statSourcesList');
    if (srcList && DATA.STAT_SOURCES) {
      srcList.innerHTML = DATA.STAT_SOURCES.map(src =>
        `<li><code>${escapeHtml(src.id)}</code> — ${escapeHtml(src.desc)}</li>`
      ).join('');
    }
  }

  let activeScrollCat = 'ALL';
  function renderScrollsTable() {
    const tbody = document.getElementById('scrollsTableBody');
    if (!tbody || !DATA.SCROLLS_REGISTRY) return;
    const filterInput = document.getElementById('scrollsTableFilter');
    const q = filterInput ? filterInput.value.trim().toLowerCase() : '';
    const rows = DATA.SCROLLS_REGISTRY.filter(s => {
      if (currentView === 'player' && s.audience === 'admin') return false;
      if (activeScrollCat !== 'ALL' && s.category !== activeScrollCat) return false;
      if (q && !(s.id + ' ' + s.rarity + ' ' + s.category + ' ' + s.target + ' ' + s.effect).toLowerCase().includes(q)) return false;
      return true;
    });
    tbody.innerHTML = rows.map(s => {
      const cls = s.rarity === 'MASTERY' || s.rarity === 'TRANSCENDENT' ? 'red' : s.rarity === 'MYTHIC' || s.rarity === 'LEGENDARY' ? 'amber' : s.rarity === 'EPIC' ? 'purple' : 'blue';
      return `
        <tr>
          <td class="nowrap-col"><code class="copyable" title="Click to copy" data-copy-literal="${escapeHtml(s.id)}">${escapeHtml(s.id)}</code></td>
          <td class="nowrap-col"><span class="tag ${cls}">${escapeHtml(s.rarity)}</span></td>
          <td class="nowrap-col"><span class="tag">${escapeHtml(s.category)}</span></td>
          <td class="nowrap-col">${escapeHtml(s.target)}</td>
          <td>${escapeHtml(s.effect)}</td>
        </tr>
      `;
    }).join('');
  }

  let activePetCat = 'ALL';
  function renderPetsTable() {
    const tbody = document.getElementById('petsTableBody');
    if (tbody && DATA.PETS_REGISTRY) {
      const rows = DATA.PETS_REGISTRY.filter(p => {
        if (currentView === 'player' && p.audience === 'admin') return false;
        return activePetCat === 'ALL' || p.category === activePetCat;
      });
      tbody.innerHTML = rows.map(p => {
        const tagCls = p.rarity === 'MASTERY' ? 'red' : p.rarity === 'LEGENDARY' ? 'amber' : p.rarity === 'EPIC' ? 'purple' : p.rarity === 'RARE' ? 'blue' : 'green';
        return `
          <tr>
            <td><code class="copyable" title="Click to copy /pet summon ${escapeHtml(p.id)}" data-copy-literal="/pet summon ${escapeHtml(p.id)}">${escapeHtml(p.id)}</code><br><strong>${escapeHtml(p.name)}</strong></td>
            <td class="nowrap-col">${escapeHtml(p.category)}</td>
            <td class="nowrap-col"><span class="tag ${tagCls}">${escapeHtml(p.rarity)}</span></td>
            <td>${escapeHtml(p.stats)}</td>
            <td><strong>${escapeHtml(p.skill)}</strong><br>${escapeHtml(p.effect)} <span style="color:var(--text-muted)">(${escapeHtml(p.perks)})</span></td>
          </tr>
        `;
      }).join('');
    }

    const domBody = document.getElementById('domainsTableBody');
    if (domBody && DATA.SKILL_DOMAINS_REGISTRY) {
      domBody.innerHTML = DATA.SKILL_DOMAINS_REGISTRY.map(d => `
        <tr>
          <td class="nowrap-col"><strong>${escapeHtml(d.category)}</strong></td>
          <td><code>${escapeHtml(d.domains)}</code></td>
          <td>${escapeHtml(d.adaptSkills)}</td>
        </tr>
      `).join('');
    }
  }

  let activeCmdMod = 'ALL';
  function renderCommandsTable() {
    const tbody = document.getElementById('commandsTableBody');
    if (!tbody || !DATA.COMMANDS_REGISTRY) return;
    const filterInput = document.getElementById('cmdTableFilter');
    const q = filterInput ? filterInput.value.trim().toLowerCase() : '';

    const visible = DATA.COMMANDS_REGISTRY.filter(c => {
      if (currentView === 'player' && c.audience === 'admin') return false;
      if (activeCmdMod !== 'ALL' && c.module !== activeCmdMod) return false;
      if (q && !(c.cmd + ' ' + (c.aliases || []).join(' ') + ' ' + c.perm + ' ' + c.module + ' ' + c.desc).toLowerCase().includes(q)) return false;
      return true;
    });

    const countEl = document.getElementById('cmdCountLabel');
    if (countEl) countEl.textContent = `${visible.length} command${visible.length === 1 ? '' : 's'} shown (${currentView === 'player' ? 'Player Wiki' : 'Admin View'})`;

    tbody.innerHTML = visible.map(c => {
      const permCls = c.audience === 'admin' ? 'red' : 'green';
      const aliasesHtml = (c.aliases || []).length
        ? c.aliases.map(a => `<code>${escapeHtml(a)}</code>`).join(' ')
        : '<span style="color:var(--text-muted)">&mdash;</span>';
      return `
        <tr>
          <td><code class="copyable" title="Click to copy" data-copy-literal="${escapeHtml(c.cmd)}">${escapeHtml(c.cmd)}</code></td>
          <td>${aliasesHtml}</td>
          <td class="nowrap-col"><span class="tag ${permCls}">${escapeHtml(c.perm)}</span></td>
          <td class="nowrap-col"><span class="tag">${escapeHtml(c.module)}</span></td>
          <td>${escapeHtml(c.desc)}</td>
        </tr>
      `;
    }).join('');
  }

  function renderPlaceholdersTable() {
    const tbody = document.getElementById('placeholdersTableBody');
    if (!tbody || !DATA.PLACEHOLDERS_REGISTRY) return;
    const filterInput = document.getElementById('phTableFilter');
    const q = filterInput ? filterInput.value.trim().toLowerCase() : '';

    const visible = DATA.PLACEHOLDERS_REGISTRY.filter(p => {
      if (currentView === 'player' && p.audience === 'admin') return false;
      if (q && !(p.ph + ' ' + p.scope + ' ' + p.returns + ' ' + p.desc).toLowerCase().includes(q)) return false;
      return true;
    });

    const countEl = document.getElementById('phCountLabel');
    if (countEl) countEl.textContent = `Showing ${visible.length} indexed placeholders (${currentView === 'player' ? 'Player Wiki' : 'Admin View'})`;

    tbody.innerHTML = visible.map(p => `
      <tr>
        <td><code class="copyable" title="Click to copy" data-copy-literal="${escapeHtml(p.ph)}">${escapeHtml(p.ph)}</code></td>
        <td class="nowrap-col"><span class="tag blue">${escapeHtml(p.scope)}</span></td>
        <td><code>${escapeHtml(p.returns)}</code></td>
        <td>${escapeHtml(p.desc)}</td>
      </tr>
    `).join('');
  }

  function renderAdminTables() {
    const adminCmdsBody = document.getElementById('adminCmdsBody');
    if (adminCmdsBody && DATA.COMMANDS_REGISTRY) {
      const list = DATA.COMMANDS_REGISTRY.filter(c => c.audience === 'admin');
      adminCmdsBody.innerHTML = list.map(c => `
        <tr>
          <td><code class="copyable" title="Click to copy" data-copy-literal="${escapeHtml(c.cmd)}">${escapeHtml(c.cmd)}</code></td>
          <td class="nowrap-col"><span class="tag red">${escapeHtml(c.perm)}</span></td>
          <td class="nowrap-col"><span class="tag">${escapeHtml(c.module)}</span></td>
          <td>${escapeHtml(c.desc)}</td>
        </tr>
      `).join('');
    }
  }

  /* ==========================================================================
   * FILTER PILLS & CALCULATORS
   * ========================================================================== */

  function wirePageFilters() {
    document.querySelectorAll('#statFilters .pill-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#statFilters .pill-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeStatCat = btn.dataset.statFilter;
        renderStatsTable();
      });
    });

    document.querySelectorAll('#scrollFilters .pill-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#scrollFilters .pill-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeScrollCat = btn.dataset.scrollFilter;
        renderScrollsTable();
      });
    });
    document.getElementById('scrollsTableFilter')?.addEventListener('input', renderScrollsTable);

    document.querySelectorAll('#petFilters .pill-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#petFilters .pill-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activePetCat = btn.dataset.petFilter;
        renderPetsTable();
      });
    });

    document.querySelectorAll('#cmdFilters .pill-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#cmdFilters .pill-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeCmdMod = btn.dataset.cmdFilter;
        renderCommandsTable();
      });
    });
    document.getElementById('cmdTableFilter')?.addEventListener('input', renderCommandsTable);
    document.getElementById('phTableFilter')?.addEventListener('input', renderPlaceholdersTable);
  }

  function wireCalculators() {
    // 1. Stat Stack & Keystone Cost Calculator (items-stats.html)
    const statOut = document.getElementById('statCalcOut');
    if (statOut) {
      const updateStatCalc = () => {
        const w = Math.max(0, Number(document.getElementById('cWepSurge')?.value) || 0);
        const p = Math.max(0, Number(document.getElementById('cPetSurge')?.value) || 0);
        const a = Math.max(0, Number(document.getElementById('cAccSurge')?.value) || 0);
        const baseRes = Math.max(2, Math.min(8, Number(document.getElementById('cResBase')?.value) || 2));
        const resCount = Math.max(0, Number(document.getElementById('cResCount')?.value) || 0);
        const conduit = Math.min(40, Math.max(0, Number(document.getElementById('cConduit')?.value) || 0));
        const flux = Math.min(25, Math.max(0, Number(document.getElementById('cFlux')?.value) || 0));
        const tierPct = Math.max(0, Number(document.getElementById('cTierPct')?.value) || 0);

        const rawSurge = w + p + a;
        const cappedSurge = Math.min(100, rawSurge);
        const effRes = resCount <= 0 ? 'None' : `${Math.max(2, Math.round(baseRes - Math.max(0, resCount - 1)))}-Hit Cadence`;
        const totalRedPct = Math.min(70, tierPct + conduit + flux);
        const effBounce = Math.max(1, Math.round(75 * (1 - totalRedPct / 100)));
        const effBoom = Math.max(1, Math.round(250 * (1 - totalRedPct / 100)));

        statOut.innerHTML = `
          <span>Surge Cap: <code>+${cappedSurge}%</code> ${rawSurge > 100 ? '<small>(hard-capped)</small>' : ''}</span>
          <span>Resonance: <code>${effRes}</code></span>
          <span>mC Reduction: <code>-${totalRedPct}%</code> <small>(cap 70%)</small></span>
          <span>Void Bounce: <code>${effBounce} mC</code></span>
          <span>Sonic Boom: <code>${effBoom} mC</code></span>
        `;
      };
      ['cWepSurge', 'cPetSurge', 'cAccSurge', 'cResBase', 'cResCount', 'cConduit', 'cFlux', 'cTierPct'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', updateStatCalc);
      });
      updateStatCalc();
    }

    // 2. Keystone Microcharge Simulator (keystones.html)
    const ksOut = document.getElementById('ksCalcOut');
    if (ksOut) {
      const updateKsCalc = () => {
        const energy = Math.max(0, Math.floor(Number(document.getElementById('kEnergy')?.value) || 0));
        const micro = Math.max(0, Math.min(999, Math.floor(Number(document.getElementById('kMicro')?.value) || 0)));
        const cost = Math.max(1, Math.floor(Number(document.getElementById('kCost')?.value) || 75));
        const overHeat = Math.max(1, Number(document.getElementById('kOverHeat')?.value) || 6);
        const dimMult = Math.max(1, Number(document.getElementById('kDim')?.value) || 1);
        const thermal = Math.min(60, Math.max(0, Number(document.getElementById('kThermal')?.value) || 0));

        let curE = energy;
        let curM = micro;
        let cracked = 0;
        while (curM < cost && curE > 0) {
          curE -= 1;
          curM += 1000;
          cracked += 1;
        }
        const paid = Math.min(curM, cost);
        curM -= paid;
        const shortfall = cost - paid;
        const overdraw = shortfall > 0;
        const heatMult = dimMult * (1 - thermal / 100);
        const addedHeat = overdraw ? Math.max(1, Math.round(overHeat * (shortfall / cost) * heatMult)) : 0;

        ksOut.innerHTML = `
          <span>Charges Cracked: <code>${cracked}</code></span>
          <span>Remaining: <code>${curE}🗲 | ${curM} mC</code></span>
          <span>Mode: <span class="tag ${overdraw ? 'red' : 'green'}">${overdraw ? 'OVERDRAW' : 'CHARGED'}</span></span>
          <span>Heat Added: <code>+${addedHeat} 🔥</code></span>
        `;
      };
      ['kEnergy', 'kMicro', 'kCost', 'kOverHeat', 'kDim', 'kThermal'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', updateKsCalc);
      });
      updateKsCalc();
    }

    // 3. Emperor Scaling Simulator (emperor.html)
    const empOut = document.getElementById('empCalcOut');
    if (empOut) {
      const updateEmpCalc = () => {
        const n = Math.max(1, Number(document.getElementById('cEmpPlayers')?.value) || 1);
        const base = Math.max(1, Number(document.getElementById('cEmpBaseDmg')?.value) || 20);
        const scale = n <= 1 ? 1.0 : 1.0 + ((n - 1) * 0.33);
        const empOutgoing = (base * scale).toFixed(1);
        const playerDmgPct = ((1.0 / scale) * 100).toFixed(1);
        empOut.innerHTML = `
          <span>angerScale: <code>${scale.toFixed(2)}x</code></span>
          <span>Boss Outgoing: <code>${empOutgoing}</code></span>
          <span>Player Damage Weight: <code>${playerDmgPct}%</code></span>
        `;
      };
      ['cEmpPlayers', 'cEmpBaseDmg'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', updateEmpCalc);
      });
      updateEmpCalc();
    }
  }

  /* ==========================================================================
   * CROSS-PAGE OFFLINE SEARCH CONTROLLER
   * ========================================================================== */

  let SEARCH_INDEX = [];
  let currentSearchHits = [];
  let selectedHitIndex = -1;

  function buildSearchIndex() {
    SEARCH_INDEX = [];

    (DATA.SEARCH_SECTIONS || []).forEach(sec => {
      SEARCH_INDEX.push({
        kind: sec.page,
        tagCls: sec.audience === 'admin' ? 'red' : 'blue',
        title: sec.title,
        sub: sec.page + ' § ' + sec.secId,
        href: `${sec.file}#${sec.secId}`,
        copy: null,
        audience: sec.audience || 'player',
        haystack: `${sec.page} ${sec.title} ${sec.text}`.toLowerCase()
      });
    });

    (DATA.STATS_REGISTRY || []).forEach(s => {
      SEARCH_INDEX.push({
        kind: 'Stat',
        tagCls: 'blue',
        title: `${s.icon} ${s.name} (${s.category})`,
        sub: `${s.brief} · Cap ${s.cap}`,
        href: 'items-stats.html#sec-stats',
        copy: s.id,
        audience: s.audience || 'player',
        haystack: `${s.id} ${s.name} ${s.category} ${s.brief} ${s.effect} ${s.cap}`.toLowerCase()
      });
    });

    (DATA.COMMANDS_REGISTRY || []).forEach(c => {
      SEARCH_INDEX.push({
        kind: 'Command',
        tagCls: c.audience === 'admin' ? 'red' : 'green',
        title: c.cmd,
        sub: `${c.perm} · ${c.desc}`,
        href: c.audience === 'admin' ? 'admin.html#sec-admin-tools' : 'commands.html',
        copy: c.cmd,
        audience: c.audience || 'player',
        haystack: `${c.cmd} ${(c.aliases || []).join(' ')} ${c.perm} ${c.module} ${c.desc}`.toLowerCase()
      });
    });

    (DATA.PLACEHOLDERS_REGISTRY || []).forEach(p => {
      SEARCH_INDEX.push({
        kind: 'Placeholder',
        tagCls: 'blue',
        title: p.ph,
        sub: `${p.scope} · ${p.desc}`,
        href: 'placeholders.html',
        copy: p.ph,
        audience: p.audience || 'player',
        haystack: `${p.ph} ${p.scope} ${p.returns} ${p.desc}`.toLowerCase()
      });
    });

    (DATA.PETS_REGISTRY || []).forEach(p => {
      SEARCH_INDEX.push({
        kind: 'Pet',
        tagCls: 'purple',
        title: `${p.name} (${p.id})`,
        sub: `${p.rarity} · ${p.category} · ${p.stats}`,
        href: 'pets.html#sec-pet-catalog',
        copy: `/pet summon ${p.id}`,
        audience: p.audience || 'player',
        haystack: `${p.id} ${p.name} ${p.category} ${p.rarity} ${p.stats} ${p.skill} ${p.perks} ${p.effect}`.toLowerCase()
      });
    });

    (DATA.SCROLLS_REGISTRY || []).forEach(s => {
      SEARCH_INDEX.push({
        kind: 'Scroll',
        tagCls: 'amber',
        title: s.id,
        sub: `${s.rarity} (${s.target}) · ${s.effect}`,
        href: s.category === 'Keystone' ? 'keystones.html#sec-sockets' : 'items-stats.html#sec-scrolls',
        copy: s.id,
        audience: s.audience || 'player',
        haystack: `${s.id} ${s.rarity} ${s.category} ${s.target} ${s.effect}`.toLowerCase()
      });
    });
  }

  function closeSearchPopover() {
    document.getElementById('searchPopover')?.classList.remove('open');
    selectedHitIndex = -1;
  }

  function openSearchHit(hit) {
    if (!hit) return;
    closeSearchPopover();
    window.location.href = hit.href;
  }

  function renderSearchPopover(query) {
    const pop = document.getElementById('searchPopover');
    const clearBtn = document.getElementById('searchClearBtn');
    const kbdEl = document.getElementById('searchKbd');
    if (!pop) return;

    const q = query.trim().toLowerCase();
    clearBtn?.classList.toggle('visible', q.length > 0);
    if (kbdEl) kbdEl.style.display = q.length > 0 ? 'none' : '';

    if (!q) {
      currentSearchHits = [];
      closeSearchPopover();
      return;
    }

    const terms = q.split(/\s+/).filter(Boolean);
    const scored = [];
    for (const item of SEARCH_INDEX) {
      if (currentView === 'player' && item.audience === 'admin') continue;
      if (!terms.every(t => item.haystack.includes(t))) continue;
      let score = 0;
      const titleLower = item.title.toLowerCase();
      if (titleLower === q) score += 100;
      else if (titleLower.includes(q)) score += 30;
      if (item.kind === 'Command' && titleLower.startsWith(q)) score += 20;
      scored.push({ item, score });
    }
    scored.sort((a, b) => b.score - a.score);
    currentSearchHits = scored.slice(0, 16).map(x => x.item);
    selectedHitIndex = currentSearchHits.length ? 0 : -1;

    if (!currentSearchHits.length) {
      pop.innerHTML = `<div class="search-pop-head"><span>No matches for "${escapeHtml(query)}"</span><span>Esc to close</span></div>`;
      pop.classList.add('open');
      return;
    }

    pop.innerHTML = `
      <div class="search-pop-head">
        <span>${scored.length} match${scored.length === 1 ? '' : 'es'}${scored.length > 16 ? ' (top 16)' : ''}</span>
        <span>Up/Down · Enter</span>
      </div>
      ${currentSearchHits.map((h, i) => `
        <div class="search-hit ${i === selectedHitIndex ? 'selected' : ''}" data-hit-idx="${i}" role="option">
          <div class="search-hit-body">
            <div class="search-hit-top">
              <span class="tag ${h.tagCls}">${escapeHtml(h.kind)}</span>
              <span class="search-hit-title">${escapeHtml(h.title)}</span>
            </div>
            <div class="search-hit-sub">${escapeHtml(h.sub)}</div>
          </div>
          ${h.copy ? `<button type="button" class="pill-btn" data-hit-copy="${escapeHtml(h.copy)}">Copy</button>` : ''}
        </div>
      `).join('')}
    `;
    pop.classList.add('open');

    pop.querySelectorAll('.search-hit').forEach(el => {
      el.addEventListener('click', () => {
        const idx = Number(el.dataset.hitIdx);
        openSearchHit(currentSearchHits[idx]);
      });
    });

    pop.querySelectorAll('[data-hit-copy]').forEach(btn => {
      btn.addEventListener('click', e => {
        e.stopPropagation();
        copyText(btn.dataset.hitCopy);
      });
    });
  }

  function updateSelectedHitClass() {
    const rows = document.querySelectorAll('#searchPopover .search-hit');
    rows.forEach((row, i) => {
      row.classList.toggle('selected', i === selectedHitIndex);
      if (i === selectedHitIndex && row.scrollIntoView) {
        row.scrollIntoView({ block: 'nearest' });
      }
    });
  }

  function wireSearch() {
    buildSearchIndex();
    const searchInput = document.getElementById('globalSearch');
    if (!searchInput) return;

    searchInput.addEventListener('input', e => renderSearchPopover(e.target.value));
    searchInput.addEventListener('focus', e => {
      if (e.target.value.trim()) renderSearchPopover(e.target.value);
    });
    searchInput.addEventListener('keydown', e => {
      if (e.key === 'Escape') {
        closeSearchPopover();
        searchInput.blur();
      } else if (e.key === 'ArrowDown' && currentSearchHits.length) {
        e.preventDefault();
        selectedHitIndex = (selectedHitIndex + 1) % currentSearchHits.length;
        updateSelectedHitClass();
      } else if (e.key === 'ArrowUp' && currentSearchHits.length) {
        e.preventDefault();
        selectedHitIndex = (selectedHitIndex - 1 + currentSearchHits.length) % currentSearchHits.length;
        updateSelectedHitClass();
      } else if (e.key === 'Enter' && selectedHitIndex >= 0 && currentSearchHits[selectedHitIndex]) {
        e.preventDefault();
        openSearchHit(currentSearchHits[selectedHitIndex]);
      }
    });

    document.getElementById('searchClearBtn')?.addEventListener('click', () => {
      searchInput.value = '';
      renderSearchPopover('');
      searchInput.focus();
    });

    document.addEventListener('click', e => {
      const wrap = document.getElementById('searchWrap');
      if (wrap && !wrap.contains(e.target)) closeSearchPopover();
    });

    document.addEventListener('keydown', e => {
      const tag = document.activeElement ? document.activeElement.tagName : '';
      if ((e.ctrlKey && e.key.toLowerCase() === 'k') || (e.key === '/' && tag !== 'INPUT' && tag !== 'TEXTAREA')) {
        e.preventDefault();
        searchInput.focus();
        searchInput.select && searchInput.select();
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    injectChrome();
    wireCopyHandlers();
    wirePageFilters();
    wireCalculators();
    wireSearch();
    applyViewMode(currentView);
  });
})();
