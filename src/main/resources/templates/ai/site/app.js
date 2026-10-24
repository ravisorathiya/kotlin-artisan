// The ai/ workspace as a one-page site (Alpine.js + Tailwind from CDN, no build step).
// `artisan ai:site` serves this folder on 127.0.0.1 and answers /api/*: the file list, plus saving requests and
// work reports. Everything shown comes from the .md / .json files here; edit them and the page follows (it checks
// for changes every couple of seconds while the tab is visible).
import Alpine from 'https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/module.esm.js';
import * as P from './parse.js';

// ---------- vocabulary ----------

const ICONS = {
  home: ['M3 11l9-7 9 7', 'M5 10v10h14V10', 'M10 20v-6h4v6'],
  history: ['M3 12a9 9 0 1 0 3-6.7', 'M3 4v5h5', 'M12 8v4l3 2'],
  rocket: ['M12 2c3 3 4 7 3 11l-3 3-3-3c-1-4 0-8 3-11z', 'M9 16l-3 3', 'M15 16l3 3', 'M12 9v.01'],
  inbox: ['M22 12h-6l-2 3h-4l-2-3H2', 'M5.5 5.1L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.8 4H7.2a2 2 0 0 0-1.7 1.1z'],
  search: ['M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z', 'M20 20l-3.5-3.5'],
  dashboard: ['M3 3h7v9H3z', 'M14 3h7v5h-7z', 'M14 12h7v9h-7z', 'M3 16h7v5H3z'],
  workflow: ['M6 3v12', 'M18 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6z', 'M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z', 'M18 9a9 9 0 0 1-9 9'],
  plan: ['M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z', 'M14 3v5h5', 'M9 13h6', 'M9 17h4'],
  events: ['M22 12h-4l-3 9L9 3l-3 9H2'],
  image: ['M3 5h18v14H3z', 'M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z', 'M21 15l-5-5L5 19'],
  report: ['M8 2v4', 'M16 2v4', 'M3 8h18', 'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z', 'M8 13h3', 'M8 17h6'],
  book: ['M4 19.5A2.5 2.5 0 0 1 6.5 17H20', 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'],
  bug: ['M8 2l1.9 1.9', 'M16 2l-1.9 1.9', 'M9 7.1V6a3 3 0 0 1 6 0v1.1', 'M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6z', 'M12 20v-9', 'M6 13H2', 'M22 13h-4'],
  sparkle: ['M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z', 'M19 17l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z'],
  plus: ['M12 5v14', 'M5 12h14'],
  check: ['M5 12l5 5L20 7'],
  x: ['M6 6l12 12', 'M18 6L6 18'],
  chevron: ['M6 9l6 6 6-6'],
  right: ['M9 6l6 6-6 6'],
  left: ['M15 6l-6 6 6 6'],
  sun: ['M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z', 'M12 2v2', 'M12 20v2', 'M4.9 4.9l1.4 1.4', 'M17.7 17.7l1.4 1.4', 'M2 12h2', 'M20 12h2', 'M4.9 19.1l1.4-1.4', 'M17.7 6.3l1.4-1.4'],
  moon: ['M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z'],
  code: ['M16 18l6-6-6-6', 'M8 6l-6 6 6 6'],
  users: ['M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M23 21v-2a4 4 0 0 0-3-3.9', 'M16 3.1a4 4 0 0 1 0 7.8'],
  device: ['M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z', 'M11 18h2'],
  question: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z', 'M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3', 'M12 17h.01'],
  clock: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z', 'M12 6v6l4 2'],
  copy: ['M9 9h11v11H9z', 'M5 15V5a2 2 0 0 1 2-2h10'],
  upload: ['M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4', 'M17 8l-5-5-5 5', 'M12 3v12'],
  download: ['M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4', 'M7 10l5 5 5-5', 'M12 15V3'],
  folder: ['M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'],
  flame: ['M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1-2.1-.2-4.1 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3.3.4 1.2 1.3 2.3 2.5 2.8z'],
  link: ['M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7', 'M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7']
};

const AREA_COLORS = ['#0EA5E9', '#8B5CF6', '#14B8A6', '#F97316', '#E11D48', '#EAB308', '#EC4899', '#10B981', '#6366F1', '#64748B', '#84CC16', '#06B6D4'];
const DEFAULT_AREAS = ['UI', 'Ads', 'Splash', 'i18n', 'Core', 'Other'];

const WSTATUS = {
  done: { label: 'Done', color: '#10B981' },
  doing: { label: 'In progress', color: '#F59E0B' },
  todo: { label: 'To do', color: '#0EA5E9' },
  blocked: { label: 'Blocked / human', color: '#E11D48' },
  skipped: { label: 'Skipped', color: '#94A3B8' },
  rejected: { label: 'Not reproduced', color: '#94A3B8' },
  notfound: { label: 'Not found', color: '#94A3B8' },
  notes: { label: 'Notes', color: '#94A3B8' }
};
const OPEN = ['todo', 'doing', 'blocked'];
const STATUS_ORDER = ['doing', 'todo', 'blocked', 'done', 'skipped', 'rejected', 'notfound'];

const KINDS = {
  change: { label: 'Changes', short: 'New', color: '#0EA5E9' },
  planned: { label: 'Planned', short: 'Planned', color: '#8B5CF6' },
  fixed: { label: 'Fixed', short: 'Fixed', color: '#10B981' },
  verify: { label: 'Verify on device', short: 'Verify', color: '#F59E0B' },
  open: { label: 'Open', short: 'Open', color: '#94A3B8' }
};
const KIND_ORDER = ['change', 'planned', 'fixed', 'verify', 'open'];
const PHASE_STATUS = { done: 'Done', 'in-progress': 'In progress', planned: 'Planned' };

const REQUEST_STATUS = {
  new: { label: 'New', hint: 'Waiting for the team to read it', color: '#0EA5E9' },
  accepted: { label: 'Accepted', hint: 'Good idea, not scheduled yet', color: '#6366F1' },
  planned: { label: 'Planned', hint: 'Scheduled for a version', color: '#8B5CF6' },
  'in-progress': { label: 'In progress', hint: 'Being worked on', color: '#F59E0B' },
  done: { label: 'Done', hint: 'Shipped or fixed', color: '#10B981' },
  declined: { label: 'Declined', hint: 'Not doing this (reason in the file)', color: '#94A3B8' }
};

// ---------- small helpers ----------

const esc = s => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const isoDay = (d = new Date()) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
const prettyDate = (d, opts = { day: 'numeric', month: 'short', year: 'numeric' }) => {
  const t = new Date(String(d).slice(0, 10) + 'T00:00:00');
  return isNaN(t) ? (d || '') : t.toLocaleDateString('en-GB', opts);
};
const slugify = s => String(s).toLowerCase().replace(/<[^>]+>/g, '').replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
const hm = minutes => `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`;

function icon(name, cls = 'w-4 h-4', stroke = 2) {
  const paths = Array.isArray(name) ? name : ICONS[name] || ICONS.sparkle;
  return `<svg viewBox="0 0 24 24" class="${cls}" fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths.map(d => `<path d="${d}"/>`).join('')}</svg>`;
}

function download(name, content) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([content], { type: 'text/markdown' }));
  a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

async function api(method, url, body) {
  const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || `HTTP ${r.status}`);
  return data;
}

async function copyText(t) {
  try { await navigator.clipboard.writeText(t); }
  catch (e) { const ta = document.createElement('textarea'); ta.value = t; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove(); }
}

function flash(el) {
  if (!el) return;
  el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash');
  setTimeout(() => el.classList.remove('flash'), 2300);
}

// ---------- routing: #/path/segments?query ----------

function parseHash() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, qs] = raw.split('?');
  const seg = path.split('/').filter(Boolean).map(decodeURIComponent);
  const query = Object.fromEntries(new URLSearchParams(qs || ''));
  let view = 'home';
  if (seg[0] === 'releases') view = 'releases';
  else if (seg[0] === 'soon') view = 'soon';
  else if (seg[0] === 'request') view = 'request';
  else if (seg[0] === 'dev') {
    const s = seg[1] || '';
    view = { '': 'dashboard', workflow: 'workflow', plans: seg[2] ? 'plan' : 'plans', events: 'events', assets: 'assets', requests: 'requests', reports: 'reports', guide: 'guide' }[s] || 'dashboard';
  }
  return { path, seg, query, view, dev: seg[0] === 'dev' };
}

// ---------- markdown ----------

const md = window.markdownit ? window.markdownit({ html: false, linkify: true, typographer: true }) : null;
let planDirName = 'plan', eventDirName = 'event';

/** workspace-relative file → in-site hash route (null when the file isn't a site page) */
function routeForPath(p) {
  let m;
  if ((m = /^plans?\/([\w.-]+?)(?:\/overview)?\.md$/.exec(p))) return `#/dev/plans/${m[1]}`;
  if ((m = /^workflow\/(v_\w+)\/overview\.md$/.exec(p))) return `#/dev/workflow/${m[1]}`;
  if ((m = /^workflow\/(v_\w+)\/phase_(\d+)\.md$/.exec(p))) return `#/dev/workflow/${m[1]}/${m[2]}`;
  if (/^README\.md$/i.test(p)) return '#/dev/guide';
  if (/^events?\/event\.md$/.test(p)) return '#/dev/events';
  if ((m = /^requests\/([\w.-]+)\.md$/.exec(p)) && !/README/i.test(p)) return `#/dev/requests?open=${m[1]}`;
  if ((m = /^reports\/(\d{4}-\d{2}-\d{2})\.md$/.exec(p))) return `#/dev/reports?day=${m[1]}`;
  return null;
}
const resolvePath = (from, href) => {
  try { return decodeURIComponent(new URL(href, 'http://ai.local/' + (from || '')).pathname.slice(1)); } catch (e) { return href; }
};

if (md) {
  const defaultLink = md.renderer.rules.link_open || ((t, i, o, e, self) => self.renderToken(t, i, o));
  md.renderer.rules.link_open = (tokens, idx, opts, env, self) => {
    const t = tokens[idx], href = t.attrGet('href') || '';
    if (/^https?:/i.test(href)) { t.attrSet('target', '_blank'); t.attrSet('rel', 'noopener'); }
    else if (href.startsWith('#')) { t.attrSet('data-anchor', slugify(href.slice(1))); t.attrSet('href', 'javascript:void 0'); }
    else {
      const target = resolvePath(env.from, href.split('#')[0]);
      const route = routeForPath(target);
      if (route) t.attrSet('href', route);
      else if (/\.(png|jpe?g|gif|webp|svg|mp4)$/i.test(target)) { t.attrSet('href', '/' + target); t.attrSet('target', '_blank'); }
      else { t.attrSet('href', 'javascript:void 0'); t.attrSet('class', 'md-path'); t.attrSet('title', target); }
    }
    return defaultLink(tokens, idx, opts, env, self);
  };
  const defaultImage = md.renderer.rules.image;
  md.renderer.rules.image = (tokens, idx, opts, env, self) => {
    const t = tokens[idx], src = t.attrGet('src') || '';
    if (!/^https?:|^data:|^\//.test(src)) t.attrSet('src', '/' + resolvePath(env.from, src));
    t.attrSet('loading', 'lazy');
    return defaultImage(tokens, idx, opts, env, self);
  };
  md.renderer.rules.heading_open = (tokens, idx, opts, env, self) => {
    const inline = tokens[idx + 1];
    if (inline && inline.content) tokens[idx].attrSet('id', slugify(inline.content));
    return self.renderToken(tokens, idx, opts);
  };
}
// GitHub-style task lists, after rendering: "<li>[x] ..." → a disabled checkbox.
const taskify = html => html.replace(/<li>(\s*<p>)?\[([ xX~-])\]\s?/g, (m, p, c) =>
  `<li class="task-list-item">${p || ''}<input type="checkbox" disabled${/x/i.test(c) ? ' checked' : ''}> `);
const renderMd = (src, from = '') => md ? taskify(md.render(src || '', { from })) : `<pre>${esc(src)}</pre>`;
const renderInline = (src, from = '') => md ? md.renderInline(src || '', { from }) : esc(src);

// ---------- content: everything derived from the files ----------

function build(files, assetList) {
  const keys = Object.keys(files);
  planDirName = keys.some(p => p.startsWith('plans/')) ? 'plans' : 'plan';
  eventDirName = keys.some(p => p.startsWith('events/')) ? 'events' : 'event';
  const json = p => { try { return files[p] ? JSON.parse(files[p]) : null; } catch (e) { return null; } };

  const app = Object.assign({ name: 'App', tagline: '', packageId: '', features: [], areas: [], project: {} }, json('app.json') || {});
  if (!Array.isArray(app.areas) || !app.areas.length) app.areas = DEFAULT_AREAS;
  if (!Array.isArray(app.features)) app.features = [];
  const dayMinutes = Number(app.project?.dayMinutes) || 510;

  const dirs = [...new Set(keys.map(p => /^workflow\/(v_\w+)\//.exec(p)?.[1]).filter(Boolean))].sort(P.verCmp);
  const versions = dirs.map(dir => {
    const version = P.versionLabel(dir);
    const path = `workflow/${dir}/overview.md`;
    const overview = P.parseOverview(files[path] || '');
    const phases = keys.map(p => new RegExp(`^workflow/${dir}/phase_(\\d+)\\.md$`).exec(p)).filter(Boolean).map(m => {
      const n = +m[1];
      const parsed = P.parsePhase(files[m[0]], { dir, version, phase: n });
      const row = overview.phases.find(r => r.n === n) || {};
      // "(fill when phase starts)" is a placeholder, not a summary
      const summary = /^\(?fill when/i.test(row.summary || '') ? '' : row.summary || '';
      return { n, path: m[0], ...parsed, summary, status: row.status || 'notes' };
    }).sort((a, b) => a.n - b.n);
    return { dir, version, path, overview, phases, release: json(`workflow/${dir}/release.json`) };
  });
  const workflowItems = versions.flatMap(v => v.phases.flatMap(p => p.items));

  const planRe = new RegExp(`^${planDirName}/([^/]+?)(/overview)?\\.md$`);
  const plans = keys.map(p => { const m = planRe.exec(p); return m ? { path: p, bundle: !!m[2], ...P.parsePlan(files[p], m[1]) } : null; })
    .filter(Boolean)
    .sort((a, b) => (b.slug === 'MASTER_OVERVIEW') - (a.slug === 'MASTER_OVERVIEW') || (b.lastTouched || '').localeCompare(a.lastTouched || '') || a.title.localeCompare(b.title));
  const eventPath = `${eventDirName}/event.md`;
  const events = P.parseEvents(files[eventPath] || '');

  const requests = keys.filter(p => /^requests\/[^/]+\.md$/.test(p) && !/README\.md$/i.test(p))
    .map(p => ({ path: p, ...P.parseRequest(files[p], p.slice(9)) }))
    .sort((a, b) => b.date.localeCompare(a.date) || b.file.localeCompare(a.file));
  const reports = keys.filter(p => /^reports\/\d{4}-\d{2}-\d{2}\.md$/.test(p))
    .map(p => ({ path: p, ...P.parseReport(files[p], p.slice(8)) }))
    .sort((a, b) => b.date.localeCompare(a.date));
  const finalReports = Object.fromEntries(keys.filter(p => /^reports\/final\/\d{4}-\d{2}-\d{2}\.md$/.test(p)).map(p => {
    const { data, body } = P.parseFrontMatter(files[p]);
    return [p.slice(14, 24), { path: p, time: data.time || '', finalized: data.finalized || '', text: body.trim() }];
  }));
  const assets = assetList.map(a => {
    const name = a.path.split('/').pop();
    return { path: a.path, name, url: '/' + a.path.split('/').map(encodeURIComponent).join('/') + '?m=' + a.m, video: /\.mp4$/i.test(name), refs: keys.filter(f => P.assetRefs(files[f]).includes(name)) };
  }).sort((a, b) => a.path.localeCompare(b.path, undefined, { numeric: true }));

  // Public release notes: workflow/v_*/release.json; a version without one still shows its phases from overview.md.
  const releases = versions.map(v => {
    const r = v.release || {
      version: v.version, codename: '', date: '', summary: v.overview.statusText, generated: true,
      status: v.phases.length && v.phases.every(p => p.status === 'done') ? 'done' : 'in-progress',
      phases: v.phases.map(p => ({ n: p.n, title: `Phase ${p.n}`, summary: p.summary, status: p.status === 'done' ? 'done' : p.status === 'doing' ? 'in-progress' : 'planned' }))
    };
    const version = r.version || v.version;
    return {
      ...r, version, dir: v.dir,
      phases: (r.phases || []).slice().sort((a, b) => a.n - b.n).map(p => ({
        ...p,
        items: [
          ...(p.changes || []).map((c, i) => ({ ...c, kind: c.status === 'planned' ? 'planned' : 'change', id: `${v.dir}-${p.n}-c${i}`, version, phase: p.n })),
          ...(p.bugs || []).map((b, i) => ({ ...b, kind: KINDS[b.status] ? b.status : 'open', id: `${v.dir}-${p.n}-b${i}`, version, phase: p.n }))
        ]
      }))
    };
  });
  const releaseItems = releases.flatMap(r => r.phases.flatMap(p => p.items));
  const stats = Object.fromEntries(releases.map(r => {
    const items = r.phases.flatMap(p => p.items);
    const total = r.phases.length, done = r.phases.filter(p => p.status === 'done').length;
    return [r.dir, {
      progress: { total, done, pct: total ? Math.round(done / total * 100) : 0 },
      changes: items.filter(i => i.kind === 'change').length,
      fixed: items.filter(i => i.kind === 'fixed' || i.kind === 'verify').length,
      open: items.filter(i => i.kind === 'open').length
    }];
  }));
  const latest = releases[releases.length - 1] || { version: '1.0', dir: '', codename: '', status: 'planned', summary: '', phases: [] };
  const phaseOf = it => (releases.find(r => r.version === it.version)?.phases || []).find(p => p.n === it.phase) || {};
  const coming = [
    ...requests.filter(r => ['planned', 'in-progress', 'accepted'].includes(r.status)).map(r => ({
      id: 'req-' + r.id, title: r.title, area: r.area || 'Other', detail: r.text.split('\n')[0].slice(0, 160), source: 'request', status: r.status,
      lane: r.type === 'bug' ? 'fix' : 'new', current: r.status === 'in-progress', version: r.version })),
    ...releaseItems.filter(i => i.kind === 'planned' || i.kind === 'open').map(i => ({
      ...i, source: 'release', lane: i.kind === 'open' ? 'fix' : 'new', current: phaseOf(i).status === 'in-progress', detail: i.detail || i.fix || '' }))
  ];

  const c = { app, dayMinutes, versions, workflowItems, plans, events, eventPath, requests, reports, finalReports, assets, releases, releaseItems, stats, latest, coming, readme: files['README.md'] || '', files, planDir: planDirName };
  c.index = searchIndex(c);
  return c;
}

const PAGES = [
  { title: 'Home', sub: 'What the app offers', to: '#/', icon: 'home' },
  { title: 'Releases', sub: 'Versions, phases, what changed', to: '#/releases', icon: 'history' },
  { title: 'Coming soon', sub: 'Planned work and open bugs', to: '#/soon', icon: 'rocket' },
  { title: 'Request a feature / report a bug', sub: 'Anyone can send one', to: '#/request', icon: 'inbox' },
  { title: 'Dev dashboard', sub: 'Open phases, verify-on-device, decisions', to: '#/dev', icon: 'dashboard' },
  { title: 'Workflow', sub: 'Versions → phases → checklists', to: '#/dev/workflow', icon: 'workflow' },
  { title: 'Plans', sub: 'Feature / fix notes and re-apply bundles', to: '#/dev/plans', icon: 'plan' },
  { title: 'Analytics events', sub: 'Every firebaseEvent, by screen', to: '#/dev/events', icon: 'events' },
  { title: 'Assets', sub: 'Screenshots and where they are used', to: '#/dev/assets', icon: 'image' },
  { title: 'Requests inbox', sub: 'Triage feature requests and bugs', to: '#/dev/requests', icon: 'inbox' },
  { title: 'Work reports', sub: 'Daily log, add today’s work', to: '#/dev/reports', icon: 'report' },
  { title: 'Agent guide', sub: 'Rules for agents (README)', to: '#/dev/guide', icon: 'book' }
];

function searchIndex(c) {
  return [
    ...PAGES.map(p => ({ kind: 'Page', ...p })),
    ...c.plans.map(p => ({ kind: 'Plan', title: p.title, sub: p.summary || p.statusText, to: `#/dev/plans/${p.slug}`, icon: 'plan' })),
    ...c.workflowItems.map(i => ({ kind: 'Checklist', title: i.title, sub: `v${i.version} · Phase ${i.phase} · ${(WSTATUS[i.status] || WSTATUS.notes).label}${i.date ? ' · ' + prettyDate(i.date) : ''}`, to: `#/dev/workflow/${i.dir}/${i.phase}?item=${i.id}`, icon: 'workflow' })),
    ...c.releaseItems.map(i => ({ kind: 'Release', title: i.title, sub: `v${i.version} · Phase ${i.phase} · ${KINDS[i.kind].label}`, to: `#/releases?item=${i.id}`, icon: 'history' })),
    ...c.events.sections.flatMap(s => s.groups.flatMap(g => g.rows.map(r => ({ kind: 'Event', title: r.event, sub: `${s.title} · ${r.when}`, to: `#/dev/events?q=${encodeURIComponent(r.event)}`, icon: 'events', mono: true })))),
    ...c.requests.map(r => ({ kind: r.type === 'bug' ? 'Bug report' : 'Request', title: r.title, sub: `${(REQUEST_STATUS[r.status] || REQUEST_STATUS.new).label} · ${prettyDate(r.date)}${r.by ? ' · ' + r.by : ''}`, to: `#/dev/requests?open=${r.id}`, icon: r.type === 'bug' ? 'bug' : 'inbox' })),
    ...c.reports.flatMap(d => d.entries.map(e => ({ kind: 'Work report', title: e.title, sub: prettyDate(d.date), to: `#/dev/reports?day=${d.date}`, icon: 'report' })))
  ];
}

const EMPTY = build({}, []);

// ---------- daily report (the developer's format) ----------
//
//   Date —— 22/10/2026 — Day : Thursday
//
//   [ App ] — V_1.0 — Total: 65 Days 4 Hours 40 Min
//   Work Time Today : 4 Hours 30 min
//
//   - [ x ] <checklist line from that day's "## 22 / 10 / 2026" heading in workflow/v_*/phase_*.md>
//   - <work entry title from reports/2026-10-22.md>
//
// A day's time: reports/final/<date>.md `time:` when confirmed, else the larger of the logged work and the "[H:MM]"
// on that date's phase heading. Total = app.json project.timeBaseline + every day after it. One working day is
// app.json project.dayMinutes (default 510 = 8 h 30 min).

const dailyCache = new WeakMap();
function daily(c) {
  if (!dailyCache.has(c)) dailyCache.set(c, makeDaily(c));
  return dailyCache.get(c);
}

function makeDaily(c) {
  const reportByDay = Object.fromEntries(c.reports.map(r => [r.date, r]));
  const base = c.app.project?.timeBaseline || null;
  const hmToMinutes = s => { const m = /^(\d{1,2}):(\d{2})$/.exec(String(s || '').trim()); return m ? +m[1] * 60 + +m[2] : null; };
  const phaseHeadings = day => {
    const out = [];
    for (const v of c.versions) for (const p of v.phases) for (const g of p.groups) {
      if (g.date !== day) continue;
      const m = /\[(\d{1,2}):(\d{2})\]/.exec(g.title);
      out.push({ path: p.path, title: g.title, minutes: m ? +m[1] * 60 + +m[2] : null });
    }
    return out;
  };
  const dayTime = day => {
    const logged = Math.round((reportByDay[day]?.hours || 0) * 60);
    const final = hmToMinutes(c.finalReports[day]?.time);
    if (final != null) return { minutes: final, logged, source: 'final' };
    const heading = Math.max(-1, ...phaseHeadings(day).map(h => h.minutes ?? -1));
    if (heading > logged) return { minutes: heading, logged, source: 'heading' };
    return { minutes: logged, logged, source: logged ? 'logged' : 'none' };
  };
  const timedDays = [...new Set([...c.reports.map(r => r.date), ...Object.keys(c.finalReports),
    ...c.versions.flatMap(v => v.phases.flatMap(p => p.groups.filter(g => g.date && /\[\d{1,2}:\d{2}\]/.test(g.title)).map(g => g.date)))])].sort();
  const minutesBefore = day => {
    if (base && day <= base.through) return day === base.through ? base.minutes - dayTime(day).minutes : null;
    const from = base ? base.through : '';
    return (base ? base.minutes : 0) + timedDays.filter(d => d > from && d < day).reduce((n, d) => n + dayTime(d).minutes, 0);
  };
  const totalHoursThrough = day => {
    if (base && day < base.through) return null;
    if (base && day === base.through) return base.minutes / 60;
    return (minutesBefore(day) + dayTime(day).minutes) / 60;
  };
  const versionFor = day => c.workflowItems.find(i => i.date === day)?.version || c.versions[c.versions.length - 1]?.version || '';
  const fmtToday = hours => { const t = Math.round((Number(hours) || 0) * 60); return `${Math.floor(t / 60)} Hours ${t % 60} min`; };
  const box = marker => `[ ${marker || ' '} ]`.replace('[   ]', '[ ]');
  const nameLine = (version, total) => `[ ${c.app.project?.reportName || c.app.name} ] — V_${version}${total != null ? ` — Total: ${fmtLong(total, c.dayMinutes)}` : ''}`;
  const bodyLines = day => {
    const lines = c.workflowItems.filter(i => i.date === day).map(i => `- ${box(i.marker)} ${i.raw}`);
    const logged = (reportByDay[day]?.entries || []).map(e => `- ${e.title}`);
    return [...(lines.length ? ['', ...lines] : []), ...(logged.length ? [lines.length ? '' : null, ...logged].filter(l => l !== null) : [])];
  };
  const reportDays = [...new Set([...c.reports.map(r => r.date), ...Object.keys(c.finalReports), ...c.workflowItems.map(i => i.date).filter(Boolean)])].sort().reverse();
  return {
    phaseHeadings, dayTime, totalHoursThrough, reportDays,
    build(day) {
      const time = dayTime(day);
      const total = totalHoursThrough(day);
      const ddmmyyyy = day.split('-').reverse().join('/');
      const weekday = new Date(day + 'T00:00:00').toLocaleDateString('en-GB', { weekday: 'long' });
      const text = [`Date —— ${ddmmyyyy} — Day : ${weekday}`, '', nameLine(versionFor(day), total), `Work Time Today : ${fmtToday(time.minutes / 60)}`, ...bodyLines(day)].join('\n') + '\n';
      return { day, time, total, text, items: c.workflowItems.filter(i => i.date === day), report: reportByDay[day] || null };
    },
    exportText(day, minutes) {
      const before = minutesBefore(day);
      const total = before == null ? null : (before + minutes) / 60;
      const [y, m, d] = day.split('-').map(Number);
      return [`## ${d} / ${m} / ${y} - [${hm(minutes)}]`, '', nameLine(versionFor(day), total), `Work Time Today : ${fmtToday(minutes / 60)}`, ...bodyLines(day)].join('\n').replace(/\n+$/, '') + '\n';
    }
  };
}

const splitTime = (hours, dayMinutes = 510) => {
  const total = Math.round((Number(hours) || 0) * 60);
  const rest = total % dayMinutes;
  return { d: Math.floor(total / dayMinutes), h: Math.floor(rest / 60), m: rest % 60, total };
};
const fmtTime = (hours, dayMinutes = 510) => {
  const { d, h, m } = splitTime(hours, dayMinutes);
  return [[d, 'd'], [h, 'h'], [m, 'm']].filter(([n]) => n).map(([n, u]) => n + u).join(' ') || '0m';
};
const fmtLong = (hours, dayMinutes = 510) => {
  const { d, h, m } = splitTime(hours, dayMinutes);
  const out = [[d, 'Day'], [h, 'Hour']].filter(([n]) => n).map(([n, u]) => `${n} ${u}${n === 1 ? '' : 's'}`);
  if (m || !out.length) out.push(`${m} Min`);
  return out.join(' ');
};

// ---------- the root component: data, routing and helpers every view uses ----------

let filesSig = '';
const cache = {};

Alpine.data('site', () => ({
  c: EMPTY,
  route: parseHash(),
  live: null,       // null = loading, true = artisan ai:site is serving, false = opened without it
  loaded: false,
  dark: document.documentElement.classList.contains('dark'),
  pal: { open: false, q: '', sel: 0 },
  toast: '',
  scrolled: false,
  isMac: /Mac|iPhone|iPad/.test(navigator.platform),

  async init() {
    addEventListener('hashchange', () => {
      const prev = this.route.path;
      this.route = parseHash();
      if (this.route.path !== prev) scrollTo({ top: 0 });
      this.pal.open = false;
    });
    addEventListener('scroll', () => { this.scrolled = scrollY > 8; }, { passive: true });
    addEventListener('keydown', e => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); this.openPalette(); }
      else if (e.key === 'Escape') this.pal.open = false;
    });
    // In-page anchors inside rendered markdown ("#some-heading")
    document.addEventListener('click', e => {
      const a = e.target.closest('a[data-anchor]');
      if (a) { e.preventDefault(); document.getElementById(a.dataset.anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
    });
    await this.refresh();
    setInterval(() => { if (!document.hidden && this.live) this.refresh(); }, 2500);
    addEventListener('focus', () => this.refresh());
  },

  /** Reads the file list; fetches only files whose modified time changed; rebuilds the content. */
  async refresh() {
    let list;
    try {
      const r = await fetch('/api/files', { cache: 'no-store' });
      if (!r.ok) throw new Error();
      list = await r.json();
      this.live = true;
    } catch (e) { this.live = false; this.loaded = true; return; }
    const sig = list.map(f => f.p + ':' + f.m).join('|');
    if (sig === filesSig) return;
    filesSig = sig;
    const text = list.filter(f => /\.(md|json)$/i.test(f.p));
    await Promise.all(text.filter(f => cache[f.p]?.m !== f.m).map(async f => {
      const r = await fetch('/' + f.p.split('/').map(encodeURIComponent).join('/') + '?m=' + f.m, { cache: 'no-store' });
      cache[f.p] = { m: f.m, text: r.ok ? await r.text() : '' };
    }));
    const files = Object.fromEntries(text.map(f => [f.p, cache[f.p].text]));
    const assets = list.filter(f => /^assets\/.+\.(png|jpe?g|gif|webp|svg|mp4)$/i.test(f.p)).map(f => ({ path: f.p, m: f.m }));
    this.c = build(files, assets);
    this.loaded = true;
    document.title = (this.c.app.name || 'App') + ' · ai workspace';
  },

  // navigation
  go(to) { location.hash = to.replace(/^#/, ''); },
  is(view) { return this.route.view === view; },
  get q() { return this.route.query; },

  // theme
  toggleTheme() {
    this.dark = !this.dark;
    document.documentElement.classList.toggle('dark', this.dark);
    try { localStorage.setItem('ai-site-theme', this.dark ? 'dark' : 'light'); } catch (e) {}
  },

  // search palette
  openPalette() { this.pal.open = true; this.pal.q = ''; this.pal.sel = 0; this.$nextTick(() => document.getElementById('pal-input')?.focus()); },
  get palResults() {
    const s = this.pal.q.trim().toLowerCase();
    if (!s) return PAGES.map(p => ({ kind: 'Page', ...p }));
    const words = s.split(/\s+/);
    return this.c.index.map(it => {
      const t = it.title.toLowerCase(), sub = (it.sub || '').toLowerCase(), k = it.kind.toLowerCase();
      if (!words.every(w => t.includes(w) || sub.includes(w) || k.includes(w))) return null;
      return { it, score: (t.startsWith(s) ? 4 : 0) + (t.includes(s) ? 2 : 0) + (it.kind === 'Page' ? 1 : 0) };
    }).filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 40).map(x => x.it);
  },
  palKey(e) {
    const n = this.palResults.length;
    if (e.key === 'ArrowDown') { e.preventDefault(); this.pal.sel = (this.pal.sel + 1) % Math.max(1, n); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); this.pal.sel = (this.pal.sel - 1 + n) % Math.max(1, n); }
    else if (e.key === 'Enter') { const r = this.palResults[this.pal.sel]; if (r) this.go(r.to); }
  },

  notify(msg) { this.toast = msg; clearTimeout(this._t); this._t = setTimeout(() => this.toast = '', 4000); },

  // shared helpers for the templates
  icon, esc, prettyDate, isoDay, hm,
  md: renderMd, mdInline: renderInline,
  routeFor: p => routeForPath(p) || 'javascript:void 0',
  fmt(hours) { return fmtTime(hours, this.c.dayMinutes); },
  fmtLong(hours) { return fmtLong(hours, this.c.dayMinutes); },
  wst(s) { return WSTATUS[s] || WSTATUS.notes; },
  rst(s) { return REQUEST_STATUS[s] || REQUEST_STATUS.new; },
  kind(k) { return KINDS[k] || KINDS.open; },
  phaseLabel(s) { return PHASE_STATUS[s] || s || ''; },
  REQUEST_STATUS, KINDS, KIND_ORDER, WSTATUS, STATUS_ORDER, OPEN,
  areaColor(name) {
    const i = this.c.app.areas.indexOf(name);
    return i < 0 ? '#94A3B8' : AREA_COLORS[i % AREA_COLORS.length];
  },
  areaBadge(name) {
    if (!name) return '';
    const col = this.areaColor(name);
    return `<span class="inline-flex items-center gap-1 text-[11px] font-semibold px-1.5 py-0.5 rounded-md" style="background:${col}1f;color:${col}"><i class="w-1.5 h-1.5 rounded-full" style="background:${col}"></i>${esc(name)}</span>`;
  },
  donut(segments, size = 120, stroke = 12) {
    const r = (size - stroke) / 2, len = 2 * Math.PI * r, total = segments.reduce((n, s) => n + s.value, 0) || 1;
    let off = 0;
    const arcs = segments.map(s => {
      const d = s.value / total * len;
      const a = `<circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${s.color}" stroke-width="${stroke}" stroke-dasharray="${d} ${len - d}" stroke-dashoffset="${-off}" />`;
      off += d; return a;
    }).join('');
    return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" class="-rotate-90"><circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="currentColor" stroke-opacity=".08" stroke-width="${stroke}"/>${arcs}</svg>`;
  },
  /** Last [weeks] weeks as columns of 7 days, for the activity heatmap. */
  heat(weeks = 20) {
    const byDay = Object.fromEntries(this.c.reports.map(r => [r.date, r.hours]));
    const end = new Date(), start = new Date();
    start.setDate(end.getDate() - (weeks * 7 - 1));
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7)); // back to Monday
    const cols = [];
    for (let d = new Date(start); d <= end;) {
      const col = [];
      for (let i = 0; i < 7; i++, d.setDate(d.getDate() + 1)) {
        const iso = isoDay(d), h = byDay[iso] || 0;
        col.push({ iso, h, future: d > end, lvl: !h ? 0 : h < 2 ? 1 : h < 4 ? 2 : h < 6 ? 3 : 4 });
      }
      cols.push(col);
    }
    return cols;
  },
  heatColor(lvl) { return ['', 'rgb(var(--brand-rgb) / .25)', 'rgb(var(--brand-rgb) / .5)', 'rgb(var(--brand-rgb) / .75)', 'rgb(var(--brand-rgb))'][lvl]; },
  bar(items) { return STATUS_ORDER.map(k => ({ k, ...WSTATUS[k], n: items.filter(i => i.status === k).length })).filter(s => s.n); },
  get newRequests() { return this.c.requests.filter(r => r.status === 'new').length; },
  get eventCount() { return this.c.events.sections.reduce((n, s) => n + s.groups.reduce((m, g) => m + g.rows.length, 0), 0); },
  get orphans() { return this.c.assets.filter(a => !a.refs.length).length; },
  devNav() {
    const c = this.c, today = isoDay();
    return [
      { to: '#/dev', label: 'Dashboard', icon: 'dashboard', view: 'dashboard' },
      { to: '#/dev/workflow', label: 'workflow/', icon: 'workflow', view: 'workflow', count: c.versions.length },
      { to: '#/dev/plans', label: c.planDir + '/', icon: 'plan', view: 'plans', count: c.plans.length },
      { to: '#/dev/reports', label: 'reports/', icon: 'report', view: 'reports', dot: !c.reports.some(r => r.date === today) },
      { to: '#/dev/requests', label: 'requests/', icon: 'inbox', view: 'requests', badge: this.newRequests },
      { to: '#/dev/events', label: (c.eventPath.split('/')[0]) + '/', icon: 'events', view: 'events', count: this.eventCount },
      { to: '#/dev/assets', label: 'assets/', icon: 'image', view: 'assets', count: c.assets.length, warn: this.orphans },
      { to: '#/dev/guide', label: 'README.md', icon: 'book', view: 'guide' }
    ];
  },
  navActive(n) { return this.route.view === n.view || (n.view === 'plans' && this.route.view === 'plan'); },
  async copy(t, msg = 'Copied') { await copyText(t); this.notify(msg); }
}));

// ---------- views with their own state ----------

Alpine.data('dashboardView', () => ({
  get current() { return this.c.versions[this.c.versions.length - 1]; },
  get openPhases() { return this.current ? this.current.phases.filter(p => p.status === 'doing') : []; },
  get greeting() { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; },
  get counts() { return Object.keys(WSTATUS).map(k => ({ kind: k, ...WSTATUS[k], value: this.c.workflowItems.filter(i => i.status === k).length })).filter(s => s.value); },
  get donePct() { const t = this.c.workflowItems.length; return t ? Math.round(this.c.workflowItems.filter(i => i.status === 'done').length / t * 100) : 0; },
  get openItems() { return this.c.workflowItems.filter(i => OPEN.includes(i.status)); },
  get verify() {
    return [
      ...this.c.workflowItems.filter(i => i.tags.includes('verify') && i.status !== 'notfound' && i.status !== 'done').map(i => ({ title: i.title, sub: `v${i.version} · phase_${i.phase}`, to: `#/dev/workflow/${i.dir}/${i.phase}?item=${i.id}` })),
      ...this.c.plans.filter(p => p.verify && p.status !== 'done').map(p => ({ title: p.title, sub: `${this.c.planDir}/${p.slug}.md`, to: `#/dev/plans/${p.slug}`, plan: true }))
    ];
  },
  get ask() {
    return [
      ...this.c.workflowItems.filter(i => i.tags.includes('ask') && OPEN.includes(i.status)).map(i => ({ title: i.title, sub: `v${i.version} · phase_${i.phase}`, to: `#/dev/workflow/${i.dir}/${i.phase}?item=${i.id}` })),
      ...this.c.plans.filter(p => p.ask && p.status !== 'done').map(p => ({ title: p.title, sub: `${this.c.planDir}/${p.slug}.md`, to: `#/dev/plans/${p.slug}` }))
    ];
  },
  get todayReport() { return this.c.reports.find(r => r.date === isoDay()); },
  get weekHours() { const s = new Date(); s.setDate(s.getDate() - 6); const from = isoDay(s); return this.c.reports.filter(r => r.date >= from).reduce((n, r) => n + r.hours, 0); },
  get streak() {
    const by = Object.fromEntries(this.c.reports.map(r => [r.date, r.hours]));
    let n = 0; const d = new Date();
    if (!by[isoDay(d)]) d.setDate(d.getDate() - 1);
    while (by[isoDay(d)]) { n++; d.setDate(d.getDate() - 1); }
    return n;
  },
  get actions() {
    const t = this.todayReport, nr = this.newRequests, op = this.openPhases;
    return [
      { to: '#/dev/reports', icon: 'report', title: t ? 'Add to today’s report' : 'Log today’s work', text: t ? `${t.entries.length} entries · ${this.fmt(t.hours)} so far` : 'Nothing logged yet today', primary: !t },
      { to: '#/dev/requests', icon: 'inbox', title: nr ? `Triage ${nr} new request${nr > 1 ? 's' : ''}` : 'Requests inbox', text: nr ? 'Waiting for a first look' : 'Inbox is clear', primary: !!nr },
      { to: op.length ? `#/dev/workflow/${this.current.dir}/${op[op.length - 1].n}` : '#/dev/workflow', icon: 'workflow', title: op.length ? `Open phase_${op[op.length - 1].n}` : 'Workflow', text: `${this.openItems.length} open checklist items` }
    ];
  }
}));

Alpine.data('workflowView', () => ({
  tab: 'list', status: 'open', tag: null, search: '', openId: null, lastKey: '',
  init() {
    this.$watch('route', () => this.sync());
    this.sync();
  },
  sync() {
    const key = (this.route.seg[2] || '') + '/' + (this.route.seg[3] || '');
    if (key !== this.lastKey) {
      this.lastKey = key;
      this.tab = 'list'; this.search = ''; this.tag = null; this.openId = null;
      this.status = this.phase && this.phase.items.some(i => OPEN.includes(i.status)) ? 'open' : 'all';
    }
    const id = this.route.query.item;
    if (id && this.phase) {
      this.status = 'all'; this.tab = 'list'; this.openId = id;
      this.$nextTick(() => setTimeout(() => { const el = document.getElementById('w-' + id); el?.scrollIntoView({ behavior: 'smooth', block: 'center' }); flash(el); }, 150));
    }
  },
  get version() { const v = this.c.versions; return v.find(x => x.dir === this.route.seg[2]) || v[v.length - 1]; },
  get phase() { const n = this.route.seg[3]; return n != null && this.version ? this.version.phases.find(p => String(p.n) === n) : null; },
  get planBySlug() { return Object.fromEntries(this.c.plans.map(p => [p.slug, p])); },
  get filtered() {
    if (!this.phase) return [];
    const s = this.search.trim().toLowerCase();
    return this.phase.items.filter(i =>
      (this.status === 'all' || (this.status === 'open' ? OPEN.includes(i.status) : i.status === this.status)) &&
      (!this.tag || i.tags.includes(this.tag)) &&
      (!s || [i.raw, ...i.notes].join(' ').toLowerCase().includes(s)));
  },
  get grouped() {
    const g = [];
    for (const it of this.filtered) {
      let last = g[g.length - 1];
      if (!last || last.title !== it.group) { last = { title: it.group, date: it.date, items: [] }; g.push(last); }
      last.items.push(it);
    }
    return g;
  },
  get chips() {
    const items = this.phase?.items || [];
    return [
      { id: 'open', label: 'Open', n: items.filter(i => OPEN.includes(i.status)).length },
      { id: 'all', label: 'All', n: items.length },
      ...STATUS_ORDER.map(k => ({ id: k, label: WSTATUS[k].label, color: WSTATUS[k].color, n: items.filter(i => i.status === k).length })).filter(c => c.n)
    ];
  },
  statusIcon(s) { return ({ done: 'check', doing: 'clock', todo: 'plus', blocked: 'x' })[s] || 'x'; },
  groupLabel(g) { return g.date ? prettyDate(g.date, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }) : g.title; }
}));

Alpine.data('plansView', () => ({
  search: '', status: 'all', sort: 'recent',
  get chips() {
    const p = this.c.plans;
    return [
      { id: 'all', label: 'All', n: p.length },
      ...['doing', 'todo', 'blocked', 'done', 'notes'].map(s => ({ id: s, label: s === 'notes' ? 'Living notes' : WSTATUS[s].label, color: WSTATUS[s].color, n: p.filter(x => x.status === s).length })).filter(c => c.n),
      { id: 'bundle', label: 'Re-apply bundles', n: p.filter(x => x.bundle).length },
      { id: 'verify', label: 'Verify on device', n: p.filter(x => x.verify).length }
    ].filter(c => c.n || c.id === 'all');
  },
  get list() {
    const s = this.search.trim().toLowerCase();
    const out = this.c.plans.filter(p => (this.status === 'all' || (this.status === 'verify' ? p.verify : this.status === 'bundle' ? p.bundle : p.status === this.status)) &&
      (!s || [p.title, p.slug, p.summary, p.statusText].join(' ').toLowerCase().includes(s)));
    return this.sort === 'az' ? out.slice().sort((a, b) => a.title.localeCompare(b.title)) : out;
  }
}));

Alpine.data('planView', () => ({
  activeH: '',
  init() {
    const observe = () => {
      this.io?.disconnect();
      this.io = new IntersectionObserver(es => { const v = es.filter(e => e.isIntersecting); if (v.length) this.activeH = v[0].target.id; }, { rootMargin: '-80px 0px -70% 0px' });
      setTimeout(() => document.querySelectorAll('.md-body h2').forEach(h => this.io.observe(h)), 200);
    };
    observe();
    this.$watch('route', observe);
  },
  destroy() { this.io?.disconnect(); },
  get plan() { return this.c.plans.find(p => p.slug === this.route.seg[2]); },
  get mentions() { return this.plan ? this.c.workflowItems.filter(i => i.plans.includes(this.plan.slug)) : []; },
  get inVersions() { return this.plan ? this.c.versions.filter(v => v.overview.plans.some(p => p.slug === this.plan.slug)) : []; },
  get related() {
    if (!this.plan) return [];
    const by = this.c.plans.filter(p => p.plans.includes(this.plan.slug)).map(p => p.slug);
    return [...new Set([...this.plan.plans, ...by])].map(s => ({ slug: s, title: this.c.plans.find(p => p.slug === s)?.title || s }));
  },
  get planAssets() { return this.plan ? this.c.assets.filter(a => this.plan.assets.includes(a.name)) : []; },
  goH(h) { document.getElementById(slugify(h))?.scrollIntoView({ behavior: 'smooth', block: 'start' }); },
  slug: slugify
}));

Alpine.data('eventsView', () => ({
  search: '', copied: '',
  init() {
    this.search = this.route.query.q || '';
    this.$watch('route', r => { if (r.query.q != null) this.search = r.query.q; });
  },
  get sections() {
    const s = this.search.trim().toLowerCase();
    return this.c.events.sections.map((sec, i) => ({
      ...sec, i,
      groups: sec.groups.map(g => ({ ...g, rows: g.rows.filter(r => !s || r.event.toLowerCase().includes(s) || r.when.toLowerCase().includes(s) || r.from.toLowerCase().includes(s) || sec.title.toLowerCase().includes(s)) })).filter(g => g.rows.length)
    })).filter(sec => sec.groups.length);
  },
  get shown() { return this.sections.reduce((n, s) => n + s.groups.reduce((m, g) => m + g.rows.length, 0), 0); },
  hl(text) {
    const s = this.search.trim(), e = esc(text);
    if (!s) return e;
    return e.replace(new RegExp(esc(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'ig'), m => `<mark class="bg-brand-200 dark:bg-brand-500/40 rounded px-0.5">${m}</mark>`);
  },
  copyName(e) { copyText(e); this.copied = e; setTimeout(() => this.copied = '', 1200); },
  jump(i) { document.getElementById('ev-' + i)?.scrollIntoView({ behavior: 'smooth' }); }
}));

Alpine.data('assetsView', () => ({
  filter: 'all', viewing: null,
  get list() { return this.c.assets.filter(a => this.filter === 'all' || (this.filter === 'orphan' ? !a.refs.length : a.refs.length)); }
}));

Alpine.data('releasesView', () => ({
  sel: null, kindFilter: 'all', openPhase: null,
  init() {
    this.pick();
    this.$watch('route', () => this.pick());
  },
  pick() {
    const id = this.route.query.item, v = this.route.query.v;
    const rs = this.c.releases;
    if (id) {
      const it = this.c.releaseItems.find(i => i.id === id);
      if (it) {
        this.sel = rs.find(r => r.version === it.version)?.dir; this.openPhase = it.phase; this.kindFilter = 'all';
        this.$nextTick(() => setTimeout(() => { const el = document.getElementById('ri-' + id); el?.scrollIntoView({ behavior: 'smooth', block: 'center' }); flash(el); }, 250));
        return;
      }
    }
    if (v) this.sel = rs.find(r => r.version === v || r.dir === v)?.dir || this.sel;
    if (!this.sel || !rs.some(r => r.dir === this.sel)) this.sel = rs[rs.length - 1]?.dir || null;
    const r = this.release;
    if (this.openPhase == null && r) this.openPhase = (r.phases.find(p => p.status === 'in-progress') || r.phases[r.phases.length - 1] || {}).n ?? null;
  },
  get release() { return this.c.releases.find(r => r.dir === this.sel) || null; },
  get st() { return this.release ? this.c.stats[this.release.dir] : null; },
  items(p) { return p.items.filter(i => this.kindFilter === 'all' || i.kind === this.kindFilter); },
  byKind(p) { return KIND_ORDER.map(k => ({ k, ...KINDS[k], items: this.items(p).filter(i => i.kind === k) })).filter(g => g.items.length); },
  kindCounts() { const items = this.release ? this.release.phases.flatMap(p => p.items) : []; return KIND_ORDER.map(k => ({ k, ...KINDS[k], n: items.filter(i => i.kind === k).length })).filter(x => x.n); }
}));

Alpine.data('soonView', () => ({
  lane: 'all',
  get list() { return this.c.coming.filter(i => this.lane === 'all' || i.lane === this.lane).sort((a, b) => b.current - a.current); }
}));

Alpine.data('requestForm', () => ({
  form: { type: 'feature', title: '', text: '', area: '', screen: '', device: '', by: '', image: '' },
  imageName: '', saving: false, error: '', saved: null, filter: 'all', openId: null,
  init() {
    try { this.form.by = localStorage.getItem('ai-site-name') || ''; } catch (e) {}
    const apply = () => { if (this.route.query.type) this.form.type = this.route.query.type === 'bug' ? 'bug' : 'feature'; };
    apply(); this.$watch('route', apply);
  },
  get copyText() {
    return this.form.type === 'bug'
      ? { title: 'What is broken?', titlePh: 'e.g. The app crashes when I tap Save', text: 'What happened, and what did you expect?', textPh: 'Steps help: "I opened…, tapped…, then…". Any language is fine.' }
      : { title: 'What would you like?', titlePh: 'e.g. A dark theme', text: 'Tell us more (optional)', textPh: 'Why would it help you? How would you use it? Any language is fine.' };
  },
  onFile(e) {
    const f = (e.target?.files || e.dataTransfer?.files || [])[0];
    if (!f) return;
    if (!/^image\//.test(f.type)) { this.error = 'Only images can be attached.'; return; }
    if (f.size > 5 * 1024 * 1024) { this.error = 'That image is over 5 MB. Try a smaller screenshot.'; return; }
    const r = new FileReader();
    r.onload = () => { this.form.image = r.result; this.imageName = f.name; this.error = ''; };
    r.readAsDataURL(f);
  },
  onPaste(e) {
    const f = [...(e.clipboardData?.files || [])].find(x => /^image\//.test(x.type));
    if (f) this.onFile({ target: { files: [f] } });
  },
  async submit() {
    this.error = '';
    const f = this.form;
    if (!f.title.trim()) { this.error = 'Please write one line saying what it is.'; return; }
    try { localStorage.setItem('ai-site-name', f.by); } catch (e) {}
    if (!this.live) {
      download(`${isoDay()}-${slugify(f.title).slice(0, 50)}.md`, `---\ntype: ${f.type}\ntitle: ${f.title.replace(/\n/g, ' ')}\nstatus: new\narea: ${f.area}\nscreen: ${f.screen}\ndevice: ${f.device}\nby: ${f.by}\ndate: ${isoDay()}\nimage:\n---\n\n${f.text.trim()}\n`);
      this.saved = { offline: true };
      return;
    }
    this.saving = true;
    try { this.saved = await api('POST', '/api/requests', { ...f }); await this.refresh(); }
    catch (e) { this.error = 'Could not save: ' + e.message; }
    finally { this.saving = false; }
  },
  again() { Object.assign(this.form, { title: '', text: '', area: '', screen: '', device: '', image: '' }); this.imageName = ''; this.saved = null; },
  get list() {
    const f = this.filter;
    return this.c.requests.filter(r => f === 'all' || (f === 'bug' ? r.type === 'bug' : f === 'feature' ? r.type === 'feature' : r.status === f));
  }
}));

Alpine.data('requestsInbox', () => ({
  filter: 'new', openId: null, busy: '',
  init() {
    const open = () => {
      const id = this.route.query.open;
      if (!id) return;
      this.openId = id; this.filter = 'all';
      this.$nextTick(() => setTimeout(() => { const el = document.getElementById('rq-' + id); el?.scrollIntoView({ block: 'center', behavior: 'smooth' }); flash(el); }, 150));
    };
    if (!this.c.requests.some(r => r.status === 'new')) this.filter = 'all';
    open(); this.$watch('route', open);
  },
  get chips() {
    const r = this.c.requests;
    return [{ id: 'all', label: 'All', n: r.length }, ...Object.entries(REQUEST_STATUS).map(([id, s]) => ({ id, label: s.label, color: s.color, n: r.filter(x => x.status === id).length }))];
  },
  get list() { return this.c.requests.filter(r => this.filter === 'all' || r.status === this.filter); },
  async setStatus(r, status) {
    if (!this.live) { this.notify('Start the site with artisan ai:site to change statuses.'); return; }
    this.busy = r.file;
    try { await api('PATCH', '/api/requests/' + encodeURIComponent(r.file), { status }); await this.refresh(); this.notify(`${r.title}: ${REQUEST_STATUS[status].label}`); }
    catch (e) { this.notify('Could not save: ' + e.message); }
    finally { this.busy = ''; }
  }
}));

Alpine.data('reportsView', () => ({
  day: isoDay(),
  form: { date: isoDay(), time: new Date().toTimeString().slice(0, 5), title: '', text: '', h: 1, m: 0, areas: [], link: '', links: [], blockers: '', by: '' },
  more: false, saving: false, error: '',
  exp: { open: false, h: 0, m: 0, stamp: true, addExtra: false, addTitle: '', text: '', edited: false, saving: false, error: '' },
  init() {
    try { this.form.by = localStorage.getItem('ai-site-name') || ''; } catch (e) {}
    const pick = () => {
      const days = this.d.reportDays;
      this.day = this.route.query.day || (days.includes(isoDay()) ? isoDay() : days[0] || isoDay());
    };
    pick(); this.$watch('route', pick);
    this.$watch('exp.h', () => this.rebuild(false)); this.$watch('exp.m', () => this.rebuild(false));
  },
  get d() { return daily(this.c); },
  get daily() { return this.d.build(this.day); },
  get current() { return this.c.reports.find(r => r.date === this.day) || null; },
  pickDay(d) { this.go(`#/dev/reports?day=${d}`); },
  step(dir) {
    const days = this.d.reportDays, i = days.indexOf(this.day);
    const next = i < 0 ? days.find(x => dir < 0 ? x < this.day : x > this.day) : days[i - dir];
    if (next) this.pickDay(next);
  },
  sumSince(n) { const s = new Date(); s.setDate(s.getDate() - n + 1); const from = isoDay(s); return this.c.reports.filter(r => r.date >= from).reduce((t, r) => t + r.hours, 0); },
  get projectHours() { return this.d.totalHoursThrough(isoDay()) ?? this.c.reports.reduce((n, r) => n + r.hours, 0); },
  get last14() {
    const by = Object.fromEntries(this.c.reports.map(r => [r.date, r.hours])), out = [];
    for (let i = 13; i >= 0; i--) { const d = new Date(); d.setDate(d.getDate() - i); const iso = isoDay(d); out.push({ iso, h: by[iso] || 0 }); }
    const max = Math.max(4, ...out.map(x => x.h));
    return out.map(x => ({ ...x, pct: x.h / max * 100 }));
  },
  get linkOptions() {
    return [...this.c.versions.flatMap(v => v.phases.map(p => `workflow/${v.dir}/phase_${p.n}.md`)), ...this.c.plans.map(p => p.path)];
  },
  // the composer keeps decimal hours in the file, 4 decimals so whole minutes survive (10 min → 0.1667)
  get formMinutes() { return Math.max(0, Math.min(24 * 60, (Math.floor(this.form.h) || 0) * 60 + (Math.floor(this.form.m) || 0))); },
  addMinutes(n) { const t = Math.max(0, Math.min(24 * 60, this.formMinutes + n)); this.form.h = Math.floor(t / 60); this.form.m = t % 60; },
  toggleArea(a) { const i = this.form.areas.indexOf(a); i >= 0 ? this.form.areas.splice(i, 1) : this.form.areas.push(a); },
  addLink() { const l = this.form.link.trim(); if (l && !this.form.links.includes(l)) this.form.links.push(l); this.form.link = ''; },
  async save() {
    const f = this.form;
    this.error = '';
    if (!f.title.trim()) { this.error = 'Give it a short title, e.g. “Fixed the splash freeze”.'; return; }
    if (!this.formMinutes) { this.error = 'How long did it take? Set the hours or minutes.'; return; }
    this.addLink();
    try { localStorage.setItem('ai-site-name', f.by); } catch (e) {}
    const hours = Math.round(this.formMinutes / 60 * 1e4) / 1e4;
    if (!this.live) {
      download(`${f.date}.md`, `---\ndate: ${f.date}\n---\n\n## ${f.time} · ${f.title}\n\n- hours: ${hours}\n\n${f.text.trim()}\n`);
      this.notify('Downloaded. Add it to ai/reports/.');
      return;
    }
    this.saving = true;
    try {
      await api('POST', '/api/reports', { ...f, hours });
      await this.refresh();
      Object.assign(f, { title: '', text: '', links: [], blockers: '', time: new Date().toTimeString().slice(0, 5) });
      this.notify('Saved to reports/' + f.date + '.md');
      this.pickDay(f.date);
    } catch (e) { this.error = 'Could not save: ' + e.message; }
    finally { this.saving = false; }
  },
  // Export → Confirm: reports/final/<date>.md, and "- [H:MM]" stamped on that date's phase heading
  get expMinutes() { return Math.max(0, Math.min(24 * 60, (Math.floor(this.exp.h) || 0) * 60 + (Math.floor(this.exp.m) || 0))); },
  get loggedMin() { return this.daily.time.logged; },
  get extraMin() { return Math.max(0, this.expMinutes - this.loggedMin); },
  get headings() { return this.d.phaseHeadings(this.day); },
  rebuild(force = true) { if (this.exp.open && (force || !this.exp.edited)) { this.exp.text = this.d.exportText(this.day, this.expMinutes); this.exp.edited = false; } },
  openExport() {
    const t = this.daily.time.minutes;
    Object.assign(this.exp, { open: true, h: Math.floor(t / 60), m: t % 60, stamp: this.headings.length > 0, addExtra: false, addTitle: '', saving: false, error: '' });
    this.rebuild();
  },
  async confirmExport() {
    const e = this.exp;
    e.error = '';
    if (!this.live) {
      download(`${this.day}.md`, `---\ndate: ${this.day}\ntime: ${hm(this.expMinutes)}\n---\n\n${e.text}`);
      e.open = false; this.notify('Downloaded. Put it in ai/reports/final/.'); return;
    }
    e.saving = true;
    try {
      const r = await api('POST', '/api/reports/finalize', { date: this.day, minutes: this.expMinutes, text: e.text, stamp: e.stamp, addMinutes: e.addExtra ? this.extraMin : 0, addTitle: e.addTitle });
      await this.refresh();
      e.open = false; this.notify('Final report saved: ' + r.changed.join(', '));
    } catch (err) { e.error = 'Could not save: ' + err.message; }
    finally { e.saving = false; }
  }
}));

window.Alpine = Alpine;
Alpine.start();
