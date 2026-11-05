/* kotlin-artisan docs: Vue 3 + Tailwind (both from a CDN), no build step. Pages are Markdown files in content/. */
const { createApp, nextTick } = Vue;

const REPO = 'https://github.com/ravisorathiya/kotlin-artisan';
const RAW = 'https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/';
const FALLBACK_VERSION = 'v0.16.2';

/* The sidebar: one entry per page. `file` is under content/, `remote` is a file of the repo (always current). */
const SIDEBAR = [
  { text: 'Getting started', section: 'guide', items: [
    { text: 'Introduction', path: '/guide/introduction', file: 'guide/introduction.md' },
    { text: 'Installation', path: '/guide/installation', file: 'guide/installation.md' },
    { text: 'Quick start', path: '/guide/quick-start', file: 'guide/quick-start.md' },
    { text: 'Configuration', path: '/guide/configuration', file: 'guide/configuration.md' },
    { text: 'In your terminal', path: '/guide/terminal', file: 'guide/terminal.md' },
  ] },
  { text: 'Recipes', section: 'recipes', items: [
    { text: 'Set up a new project', path: '/recipes/new-project', file: 'recipes/new-project.md' },
    { text: 'Add Firebase to an app', path: '/recipes/firebase', file: 'recipes/firebase.md' },
    { text: 'Add ads with a fallback', path: '/recipes/ads', file: 'recipes/ads.md' },
    { text: 'Track a new screen', path: '/recipes/events', file: 'recipes/events.md' },
    { text: 'Ask for permissions', path: '/recipes/permissions', file: 'recipes/permissions.md' },
    { text: 'Translate the app', path: '/recipes/translations', file: 'recipes/translations.md' },
    { text: 'QA on a phone', path: '/recipes/qa', file: 'recipes/qa.md' },
    { text: 'Ship a release', path: '/recipes/release', file: 'recipes/release.md' },
    { text: 'Keep a project up to date', path: '/recipes/update', file: 'recipes/update.md' },
  ] },
  { text: 'Commands', section: 'commands', items: [
    { text: 'All commands', path: '/commands/', file: 'commands/index.md' },
    { text: 'Phone (app:)', path: '/commands/phone', file: 'commands/phone.md' },
    { text: 'Generators (make:)', path: '/commands/generators', file: 'commands/generators.md' },
    { text: 'Checks and lists', path: '/commands/checks', file: 'commands/checks.md' },
    { text: 'Open, build, tools', path: '/commands/tools', file: 'commands/tools.md' },
  ] },
  { text: 'Modules', section: 'modules', items: [
    { text: 'Firebase module', path: '/modules/firebase', file: 'modules/firebase.md' },
    { text: 'Ads module', path: '/modules/ads', file: 'modules/ads.md' },
    { text: 'Analytics events', path: '/modules/events', file: 'modules/events.md' },
    { text: 'Permission flow', path: '/modules/permissions', file: 'modules/permissions.md' },
  ] },
  { text: 'Workspaces', section: 'workspace', items: [
    { text: 'AI agent workspace', path: '/workspace/ai', file: 'workspace/ai.md' },
    { text: 'Notes workspace', path: '/workspace/notes', file: 'workspace/notes.md' },
  ] },
  { text: 'Project', section: 'project', items: [
    { text: 'Changelog', path: '/changelog', remote: 'CHANGELOG.md' },
    { text: 'Contributing', path: '/contributing', remote: 'CONTRIBUTING.md' },
  ] },
];

const PAGES = SIDEBAR.flatMap(g => g.items.map(i => ({ ...i, group: g.text, section: g.section })));

const TOP_NAV = [
  { text: 'Guide', path: '/guide/introduction', section: 'guide' },
  { text: 'Recipes', path: '/recipes/new-project', section: 'recipes' },
  { text: 'Commands', path: '/commands/', section: 'commands' },
  { text: 'Modules', path: '/modules/firebase', section: 'modules' },
  { text: 'Changelog', path: '/changelog', section: 'project' },
];

/* ---------------- Markdown ---------------- */

const slug = s => s.toLowerCase().replace(/<[^>]+>/g, '').replace(/&[a-z]+;/g, '').replace(/[`'"]/g, '')
  .replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
const escapeHtml = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const LANG_ALIAS = { sh: 'bash', shell: 'bash', zsh: 'bash', console: 'bash', ps: 'powershell', ps1: 'powershell', kt: 'kotlin', kts: 'kotlin', text: 'plaintext', txt: 'plaintext', toml: 'ini' };

function renderMarkdown(src, fromRepo) {
  src = src.replace(/\r\n?/g, '\n');   // files saved on Windows
  const used = {};
  const renderer = new marked.Renderer();
  renderer.heading = function ({ tokens, depth }) {
    const text = this.parser.parseInline(tokens);
    let id = slug(text) || 'section';
    if (used[id] != null) id = `${id}-${++used[id]}`; else used[id] = 0;
    if (depth === 1) return `<h1 id="${id}">${text}</h1>`;
    return `<h${depth} id="${id}"><a class="anchor" href="#${id}" data-anchor="${id}" aria-hidden="true">#</a>${text}</h${depth}>`;
  };
  renderer.code = function ({ text, lang }) {
    const raw = (lang || '').trim().split(/\s+/)[0].toLowerCase();
    const name = LANG_ALIAS[raw] || raw || 'plaintext';
    let body;
    try { body = hljs.getLanguage(name) && name !== 'plaintext' ? hljs.highlight(text, { language: name }).value : escapeHtml(text); }
    catch (e) { body = escapeHtml(text); }
    const label = raw && raw !== 'text' ? raw : '';
    return `<div class="code-block"><div class="code-head"><span>${label}</span><button class="copy-btn" data-copy>Copy</button></div>` +
      `<pre><code class="hljs language-${name}">${body}</code></pre><textarea class="hidden" aria-hidden="true">${escapeHtml(text)}</textarea></div>`;
  };
  renderer.link = function ({ href, title, tokens }) {
    const text = this.parser.parseInline(tokens);
    let url = href || '';
    // files of the repo, linked from the README-style files fetched from GitHub
    if (fromRepo && !/^([a-z]+:|#|\/)/i.test(url)) url = `${REPO}/blob/main/${url}`;
    const external = /^https?:/i.test(url);
    return `<a href="${url}"${title ? ` title="${escapeHtml(title)}"` : ''}${external ? ' target="_blank" rel="noopener"' : ''}>${text}</a>`;
  };
  renderer.table = function (token) {
    const html = marked.Renderer.prototype.table.call(this, token);
    return `<div class="table-wrap">${html}</div>`;
  };

  // ::: tip|warning|danger|info|details [Title] ... :::   (VitePress-style blocks)
  const titles = { tip: 'Tip', warning: 'Warning', danger: 'Careful', info: 'Note', details: 'Details' };
  // innermost first: a body never holds a ::: line, so a tip inside steps closes the tip, not the steps
  const block = /^:::[ \t]*(tip|warning|danger|info|details|steps)\b[ \t]*(.*)\n((?:(?!^:::)[^])*?)\n:::[ \t]*$/m;
  for (let m; (m = block.exec(src));) {
    const [all, kind, title, body] = m;
    const t = title.trim() || titles[kind];
    const html = kind === 'steps' ? `<div class="steps">\n\n${body}\n\n</div>`
      : kind === 'details' ? `<details class="custom-block details"><summary>${t}</summary>\n\n${body}\n\n</details>`
      : `<div class="custom-block ${kind}"><p class="custom-block-title">${t}</p>\n\n${body}\n\n</div>`;
    // blank lines around it, so the Markdown after </div> isn't read as part of the HTML block
    src = src.slice(0, m.index) + `\n${html}\n` + src.slice(m.index + all.length);
  }
  return marked.parse(src, { renderer, gfm: true });
}

/* ---------------- loading pages ---------------- */

const cache = {};
async function loadPage(page) {
  if (cache[page.path]) return cache[page.path];
  let text;
  if (page.remote) {
    // the repo's own file, so the page is never out of date; the local copy (../) when served from a clone
    for (const url of [RAW + page.remote, '../' + page.remote]) {
      try { const r = await fetch(url); if (r.ok) { text = await r.text(); break; } } catch (e) { /* next */ }
    }
    if (text == null) text = `# ${page.text}\n\nCouldn't load it. Read it [on GitHub](${REPO}/blob/main/${page.remote}).`;
  } else {
    try {
      const r = await fetch('content/' + page.file);
      text = r.ok ? await r.text() : `# Not found\n\nThis page is missing: \`content/${page.file}\`.`;
    } catch (e) {
      // opened as a file: browsers don't let a page read files next to it
      text = location.protocol === 'file:'
        ? `# Preview with a local server\n\nBrowsers block pages opened from disk from loading the docs. From the repo root:\n\n\`\`\`sh\npython -m http.server 8000 -d docs\n\`\`\`\n\nThen open http://localhost:8000.`
        : `# Couldn't load this page\n\nCheck your connection and reload.`;
    }
  }
  return (cache[page.path] = text.replace(/\r\n?/g, '\n'));
}

/* Search index: each page split at its ## / ### headings. */
let index = null;
async function buildIndex() {
  if (index) return index;
  const entries = [];
  await Promise.all(PAGES.map(async p => {
    const md = await loadPage(p);
    let title = p.text, id = '', buf = [];
    const used = {};
    const flush = () => {
      const body = buf.join(' ').replace(/```[\s\S]*?```/g, ' ').replace(/[`*_>|#\[\]()]/g, ' ').replace(/\s+/g, ' ').trim();
      entries.push({ key: p.path + '#' + id, path: p.path, id, page: `${p.group} › ${p.text}`, title, body, lower: (title + ' ' + body).toLowerCase(), titleLower: title.toLowerCase() });
    };
    for (const line of md.split('\n')) {
      const m = /^(#{1,3})\s+(.*)$/.exec(line);
      if (m) {
        flush();
        buf = [];
        const text = m[2].replace(/`/g, '');
        let s = slug(marked.parseInline(m[2]));
        if (used[s] != null) s = `${s}-${++used[s]}`; else used[s] = 0;
        title = m[1].length === 1 ? p.text : text;
        id = m[1].length === 1 ? '' : s;
      } else buf.push(line);
    }
    flush();
  }));
  return (index = entries);
}

function search(entries, q) {
  const words = q.toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  const hits = [];
  for (const e of entries) {
    if (!words.every(w => e.lower.includes(w))) continue;
    let score = 0;
    for (const w of words) {
      if (e.titleLower.includes(w)) score += 10;
      if (e.titleLower.startsWith(w)) score += 5;
    }
    if (!e.id) score += 2;
    const at = e.body.toLowerCase().indexOf(words[0]);
    const snippet = at < 0 ? e.body.slice(0, 110) : (at > 40 ? '…' : '') + e.body.slice(Math.max(0, at - 40), at + 80);
    hits.push({ ...e, score, snippet });
  }
  return hits.sort((a, b) => b.score - a.score).slice(0, 12);
}

/* ---------------- the app ---------------- */

createApp({
  data: () => ({
    repo: REPO,
    sidebar: SIDEBAR,
    topNav: TOP_NAV,
    path: '/',
    html: '',
    renderedPath: '',
    loading: false,
    progress: 0,
    toc: [],
    activeId: '',
    menuOpen: false,
    dark: document.documentElement.classList.contains('dark'),
    version: FALLBACK_VERSION,
    searchOpen: false,
    query: '',
    results: [],
    selected: 0,
    copied: '',
    installTab: 'Windows',
    installs: {
      'Windows': 'irm https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.ps1 | iex',
      'macOS / Linux': 'curl -fsSL https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.sh | sh',
    },
    terminal: [
      { cmd: 'artisan app:dark on', note: '      # dark theme on the phone' },
      { cmd: 'artisan app:shot', note: '         # screenshot into your notes' },
      { cmd: 'artisan check:strings', note: '    # missing translations' },
      { cmd: 'artisan make:firebase', note: '    # a whole Firebase module' },
      { c: '  ✓ :core:firebase created, wired into 4 modules' },
      { c: '  ✓ App.kt: Analytics builder + init(this)' },
      { cmd: 'artisan bump minor', note: '       # 1.7 → 1.8, versionCode +1' },
    ],
    features: [
      { icon: '📱', title: 'Phone switches for QA', text: 'Dark mode, font scale, rotation, per-app language, screenshots, recordings, logcat, deep links, permissions, a clean status bar.', link: '/commands/phone' },
      { icon: '🏭', title: 'Generators', text: 'Strings in every locale at once, screens with layouts, events, permission flows, version bumps, re-apply bundles.', link: '/commands/generators' },
      { icon: '🔥', title: 'A Firebase module in one command', text: 'Analytics, Crashlytics, Remote Config from one-line keys, Messaging, Timber and the event box, all on the latest versions.', link: '/modules/firebase' },
      { icon: '💰', title: 'Ads with a fallback', text: 'Banner, native, interstitial, app open and splash. When the Google ad fails, your own More Apps ad shows instead.', link: '/modules/ads' },
      { icon: '🩺', title: 'Project health', text: 'Missing translations per module and language, events out of sync, TODOs, lines of code, and a doctor for your tools.', link: '/commands/checks' },
      { icon: '🤖', title: 'Made for AI agents too', text: 'An :ai workspace with skills, plans and event docs, and output that switches to compact rows in a pipe.', link: '/workspace/ai' },
    ],
    shots: [
      { src: 'assets/app.svg', alt: 'artisan app: a numbered picker of every phone command', cap: '$ artisan app' },
      { src: 'assets/strings.svg', alt: 'artisan check:strings: missing translations per module and language', cap: '$ artisan check:strings' },
      { src: 'assets/doctor.svg', alt: 'artisan doctor: Java, SDK, adb, phone, git and notes checks', cap: '$ artisan doctor' },
      { src: 'assets/stats.svg', alt: 'artisan stats: Kotlin and Java lines per module', cap: '$ artisan stats' },
    ],
  }),
  computed: {
    isHome() { return this.path === '/' || this.path === ''; },
    page() { return PAGES.find(p => p.path === this.path) || null; },
    section() { return this.page ? this.page.section : ''; },
    prev() { const i = PAGES.indexOf(this.page); return i > 0 ? PAGES[i - 1] : null; },
    next() { const i = PAGES.indexOf(this.page); return i >= 0 && i < PAGES.length - 1 ? PAGES[i + 1] : null; },
    editUrl() { return this.page && this.page.file ? `${REPO}/edit/main/docs/content/${this.page.file}` : REPO; },
    releaseUrl() { return `${REPO}/releases/latest`; },
  },
  watch: {
    async query(q) {
      this.selected = 0;
      this.results = search(await buildIndex(), q);
    },
    dark: { immediate: true, handler(on) {
      document.getElementById('hljs-light').disabled = on;
      document.getElementById('hljs-dark').disabled = !on;
    } },
  },
  methods: {
    /* #/guide/installation#updating -> path + heading */
    parseHash() {
      const h = decodeURIComponent(location.hash.replace(/^#/, ''));
      if (!h.startsWith('/')) return { path: this.path || '/', anchor: h };
      const at = h.indexOf('#');
      return at < 0 ? { path: h, anchor: '' } : { path: h.slice(0, at), anchor: h.slice(at + 1) };
    },
    async route() {
      const { path, anchor } = this.parseHash();
      const changed = path !== this.path || !this.html;
      this.path = path;
      this.menuOpen = false;
      if (this.isHome) { document.title = 'kotlin-artisan · an artisan-style CLI for Android projects'; window.scrollTo(0, 0); return; }
      if (changed) {
        const page = this.page;
        if (!page) { this.html = renderMarkdown(`# Page not found\n\nThere's no page at \`${path}\`. [Back to the introduction](#/guide/introduction).`); this.toc = []; return; }
        // keep the current page on screen while the next one loads; a skeleton only when it's slow
        let slow = null;
        if (!cache[page.path]) {
          this.progress = 0.3;
          slow = setTimeout(() => { if (this.path === path) this.loading = true; }, 250);
        }
        const md = await loadPage(page);
        clearTimeout(slow);
        if (this.path !== path) return;
        this.html = renderMarkdown(md, !!page.remote);
        this.renderedPath = page.path;
        this.loading = false;
        this.progress = 1;
        setTimeout(() => (this.progress = 0), 350);
        document.title = `${page.text} · kotlin-artisan`;
        window.scrollTo({ top: 0 });
        await nextTick();
        this.buildToc();
      }
      await nextTick();
      if (anchor) setTimeout(() => this.scrollTo(anchor, false), 180);
    },
    buildToc() {
      const el = this.$refs.article;
      if (!el) return;
      this.toc = [...el.querySelectorAll('h2, h3')].map(h => ({ id: h.id, level: +h.tagName[1], text: h.textContent.replace(/^#/, '').trim() }));
      this.onScroll();
    },
    scrollTo(id, push = true) {
      const el = document.getElementById(id);
      if (!el) return;
      el.scrollIntoView({ behavior: push ? 'smooth' : 'auto', block: 'start' });
      if (push) history.replaceState(null, '', `#${this.path}#${id}`);
      this.activeId = id;
    },
    onScroll() {
      if (this.isHome || !this.toc.length) return;
      let current = this.toc[0].id;
      for (const h of this.toc) {
        const el = document.getElementById(h.id);
        if (el && el.getBoundingClientRect().top < 120) current = h.id;
      }
      if (window.innerHeight + window.scrollY >= document.body.scrollHeight - 4) current = this.toc[this.toc.length - 1].id;
      this.activeId = current;
    },
    onArticleClick(e) {
      const copy = e.target.closest('[data-copy]');
      if (copy) {
        const text = copy.closest('.code-block').querySelector('textarea').value;
        navigator.clipboard.writeText(text).then(() => { copy.textContent = 'Copied'; setTimeout(() => (copy.textContent = 'Copy'), 1500); });
        return;
      }
      const a = e.target.closest('a[data-anchor]') || e.target.closest('a[href^="#"]:not([href^="#/"])');
      if (a) { e.preventDefault(); this.scrollTo(a.getAttribute('href').slice(1)); }
    },
    copy(text, key) {
      navigator.clipboard.writeText(text).then(() => { this.copied = key; setTimeout(() => (this.copied = ''), 1500); });
    },
    toggleDark() {
      this.dark = !this.dark;
      document.documentElement.classList.toggle('dark', this.dark);
      localStorage.setItem('theme', this.dark ? 'dark' : 'light');
    },
    async openSearch() {
      this.searchOpen = true;
      await nextTick();
      this.$refs.searchInput && this.$refs.searchInput.focus();
      buildIndex();
    },
    move(d) { if (this.results.length) this.selected = (this.selected + d + this.results.length) % this.results.length; },
    go(r) {
      if (!r) return;
      this.searchOpen = false;
      this.query = '';
      location.hash = r.path + (r.id ? '#' + r.id : '');
    },
    async fetchVersion() {
      const saved = sessionStorage.getItem('artisan-version');
      if (saved) { this.version = saved; return; }
      try {
        const r = await fetch('https://api.github.com/repos/ravisorathiya/kotlin-artisan/releases/latest');
        if (r.ok) { const j = await r.json(); if (j.tag_name) { this.version = j.tag_name; sessionStorage.setItem('artisan-version', j.tag_name); } }
      } catch (e) { /* offline: keep the fallback */ }
    },
  },
  mounted() {
    window.addEventListener('hashchange', () => this.route());
    window.addEventListener('scroll', () => this.onScroll(), { passive: true });
    window.addEventListener('keydown', e => {
      if ((e.key === 'k' && (e.ctrlKey || e.metaKey)) || (e.key === '/' && !/input|textarea/i.test(document.activeElement.tagName))) {
        e.preventDefault();
        this.searchOpen ? (this.searchOpen = false) : this.openSearch();
      }
    });
    document.addEventListener('mouseover', e => {
      const a = e.target.closest && e.target.closest('a[href^="#/"]');
      if (!a) return;
      const p = PAGES.find(x => x.path === a.getAttribute('href').slice(1).split('#')[0]);
      if (p && !cache[p.path]) loadPage(p);
    }, { passive: true });
    this.route().then(() => (window.requestIdleCallback || setTimeout)(() => buildIndex(), 1200));
    this.fetchVersion();
  },
}).mount('#app');
