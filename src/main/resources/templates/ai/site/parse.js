// Parsers for the ai/ markdown files. Pure functions, no DOM: the site and `artisan ai:site` read the same notes.
// Tolerant on purpose: the notes are hand-written by people and agents in slightly different styles, and nothing
// here may throw on an odd line. Both folder spellings work: plan/ or plans/, event/ or events/.

export function parseFrontMatter(src) {
  const m = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(src);
  if (!m) return { data: {}, body: src };
  const data = {};
  for (const line of m[1].split(/\r?\n/)) {
    const i = line.indexOf(':');
    if (i > 0) data[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  }
  return { data, body: src.slice(m[0].length) };
}

// Checklist marker → status. Covers `[x]`, `[ x ]`, `[X]`, `[~]`, `[ ]`, `[-]`, `[ SKIP ]`, `[ reject ]`, `[ NOT FOUND ]`.
export function statusFromToken(token) {
  const t = String(token ?? '').trim().toLowerCase().replace(/_/g, ' ');
  if (t === 'x') return 'done';
  if (t === '~') return 'doing';
  if (t === '') return 'todo';
  if (t === '-') return 'blocked';
  if (t === 'skip') return 'skipped';
  if (t === 'reject') return 'rejected';
  if (t === 'not found') return 'notfound';
  return 'todo';
}

export function detectTags(text) {
  const tags = [];
  if (/VERIFY ON DEVICE|NEEDS (?:[\w ]+ )?VERIFY|DEVICE VERIFY/i.test(text)) tags.push('verify');
  if (/ASK (?:DEV|SIR)/i.test(text)) tags.push('ask');
  return tags;
}

const DATE_IN_HEADING = /(\d{1,2})\s*\/\s*(\d{1,2})\s*\/\s*(\d{4})/;
export function headingDate(h) {
  const m = DATE_IN_HEADING.exec(h);
  if (!m) return null;
  return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
}

export function stripMd(s) {
  return String(s ?? '')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/(^|\W)_([^_]+)_(?=\W|$)/g, '$1$2')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}

// plan/<slug>.md, plans/<slug>.md and bundle folders plan/<slug>/overview.md all name the plan <slug>.
const PLAN_REF = /plans?\/([\w.-]+?)(?:\/overview)?\.md/g;
export function planRefs(text) {
  const out = new Set();
  for (const m of String(text).matchAll(PLAN_REF)) if (m[1] !== 'MASTER_OVERVIEW') out.add(m[1]);
  return [...out];
}

export function assetRefs(text) {
  const out = new Set();
  for (const m of String(text).matchAll(/\b([\w-]+\.(?:png|jpe?g|gif|webp|svg|mp4))\b/gi)) out.add(m[1]);
  return [...out];
}

/** v_1_7_alpha → "1.7 alpha" */
export const versionLabel = dir => {
  const g = String(dir).replace(/^v_/, '').split('_');
  return g.slice(0, 2).join('.') + (g.length > 2 ? ' ' + g.slice(2).join(' ') : '');
};

/** Sorts version folders: numbers first (1.10 after 1.9), then the stage. */
export const verCmp = (a, b) => {
  const pa = String(a).replace(/^v_/, '').split('_'), pb = String(b).replace(/^v_/, '').split('_');
  for (let i = 0; i < 2; i++) { const d = (+pa[i] || 0) - (+pb[i] || 0); if (d) return d; }
  return (pa.slice(2).join('_')).localeCompare(pb.slice(2).join('_'));
};

// workflow/v_x_y/overview.md → title, status, phase table, plan table, carried-over items
export function parseOverview(src) {
  const lines = src.split(/\r?\n/);
  const title = (lines.find(l => /^#\s+/.test(l)) || '').replace(/^#\s+/, '').trim();
  const statusLine = lines.find(l => /^Status:/i.test(l)) || '';
  const tok = /\[([^\]]*)\]/.exec(statusLine);
  const phases = [], plans = [], carried = [];
  let inCarried = false;
  for (const l of lines) {
    const ph = /^\|\s*(\d+)\s*\|\s*\[([^\]]+)\]\(([^)]+)\)\s*\|\s*(.*?)\s*\|\s*`?\[([^\]]*)\]`?\s*\|\s*$/.exec(l);
    if (ph) { phases.push({ n: +ph[1], file: ph[3], summary: stripMd(ph[4]), status: statusFromToken(ph[5]) }); continue; }
    const pl = /^\|\s*\[([^\]]+)\]\(([^)]*plans?\/([\w.-]+?)(?:\/overview)?\.md)\)\s*\|\s*(.*?)\s*\|\s*$/.exec(l);
    if (pl) {
      const st = /`\[([^\]]*)\]`/.exec(pl[4]);
      plans.push({ slug: pl[3], note: stripMd(pl[4]), status: st ? statusFromToken(st[1]) : 'notes' });
      continue;
    }
    if (/^##\s+/.test(l)) inCarried = /open items|carried/i.test(l);
    else if (inCarried && /^\s*[-*]\s+/.test(l)) carried.push(stripMd(l.replace(/^\s*[-*]\s+/, '')));
  }
  return { title, status: tok ? statusFromToken(tok[1]) : 'notes', statusText: stripMd(statusLine.replace(/^Status:/i, '')), phases, plans, carried };
}

// workflow/v_x_y/phase_n.md → checklist items grouped by date/section heading, with indented follow-ups as notes
export function parsePhase(src, meta = {}) {
  const lines = src.split(/\r?\n/);
  const items = [], groups = [];
  let group = { title: 'General', date: null };
  let cur = null, n = 0;
  const title = (lines.find(l => /^#\s+/.test(l)) || '').replace(/^#\s+/, '').trim();
  for (const line of lines) {
    const h = /^#{2,4}\s+(.*)$/.exec(line);
    if (h) {
      group = { title: h[1].trim(), date: headingDate(h[1]) };
      groups.push(group);
      cur = null;
      continue;
    }
    if (/^-{3,}|^-{5,}\[/.test(line.trim())) {
      const label = /\[([^\]]+)\]/.exec(line);
      if (label) { group = { title: label[1].trim(), date: null }; groups.push(group); }
      cur = null;
      continue;
    }
    const c = /^(\s*)[-*]\s*\[\s*([^\]]*?)\s*\]\s*(.*)$/.exec(line);
    if (c && c[1].length < 2) {
      const raw = c[3].trim();
      if (!raw) { cur = null; continue; }
      const bold = /^\*\*(.+?):?\*\*:?\s*(.*)$/.exec(raw);
      cur = {
        id: `${meta.dir || 'v'}-${meta.phase ?? 'p'}-${n++}`,
        dir: meta.dir, version: meta.version, phase: meta.phase,
        status: statusFromToken(c[2]),
        marker: c[2].trim(), // the checkbox exactly as written (x, ~, -, NOT FOUND, …), for the daily report
        title: stripMd(bold ? bold[1] : raw),
        text: bold ? bold[2] : '',
        raw, notes: [],
        group: group.title, date: group.date
      };
      items.push(cur);
      continue;
    }
    if (cur && /^\s{2,}\S/.test(line)) { cur.notes.push(line.trim().replace(/^[-*]\s+/, '')); continue; }
    if (line.trim() === '') continue;
    cur = null;
  }
  for (const it of items) {
    const all = [it.raw, ...it.notes].join(' ');
    it.tags = detectTags(all);
    it.plans = planRefs(all);
    it.hasTitle = !!it.text;
  }
  return { title, items, groups };
}

const PLAN_WORD_STATUS = [
  [/\b(complete|done|verified|finished)\b/i, 'done'],
  [/needs a repro|blocked|needs human/i, 'blocked'],
  [/in progress|implemented|built|investigat/i, 'doing']
];

// plan/<slug>.md (or a bundle's overview.md) → title, status, summary, dates, table of contents, references
export function parsePlan(src, slug) {
  const lines = src.split(/\r?\n/);
  const heading = (lines.find(l => /^#\s+/.test(l)) || '').replace(/^#\s+/, '').trim();
  const title = heading || slug.replace(/^\d+_/, '').replace(/[_-]+/g, ' ').replace(/^./, c => c.toUpperCase());
  const statusIdx = lines.findIndex(l => /^(?:#{1,3}\s*)?\**\s*status\b/i.test(l.trim()));
  const statusLine = statusIdx >= 0 ? lines.slice(statusIdx, statusIdx + 2).join(' ') : '';
  let status = 'notes';
  const tok = /`?\[\s*([x~ -])\s*\]`?/i.exec(statusLine);
  if (tok) status = statusFromToken(tok[1]);
  else if (statusLine) for (const [re, s] of PLAN_WORD_STATUS) if (re.test(statusLine)) { status = s; break; }
  const dates = [...src.matchAll(/\b(20\d\d-\d\d-\d\d)\b/g)].map(m => m[1]).sort();
  const toc = lines.filter(l => /^##\s+/.test(l)).map(l => l.replace(/^##\s+/, '').trim());
  let summary = '';
  for (const l of lines) {
    const t = l.trim();
    if (!t || /^#/.test(t) || /^\|/.test(t) || /^```/.test(t) || /^<!--/.test(t) || /^\**status/i.test(t)) continue;
    summary = stripMd(t.replace(/^[-*>]\s+/, ''));
    if (summary.length > 30) break;
  }
  const words = src.split(/\s+/).length;
  return {
    slug, title, untitled: !heading, status,
    statusText: stripMd(statusLine.replace(/^(?:#{1,3}\s*)?\**\s*status\**\s*:?\s*/i, '')).slice(0, 240),
    verify: /VERIFY ON DEVICE/i.test(src),
    ask: /ASK (?:DEV|SIR)/i.test(src),
    summary: summary.slice(0, 260),
    lastTouched: dates[dates.length - 1] || null,
    toc, plans: planRefs(src).filter(p => p !== slug), assets: assetRefs(src),
    minutes: Math.max(1, Math.round(words / 220))
  };
}

// event/event.md → { crashlytics, sections: [{ title, file, intro, groups: [{ label, rows: [{ event, from, when }] }] }] }
// A row is a table line whose first cell is a `backticked` name; `## Example...` sections are samples and skipped.
export function parseEvents(src) {
  const crash = /\*\*Crashlytics:\*\*\s*\[[^\]]*\]\(([^)]+)\)/.exec(src);
  const sections = [];
  let sec = null, grp = null;
  for (const line of src.split(/\r?\n/)) {
    const h = /^##\s+(.*)$/.exec(line);
    if (h) {
      const title = h[1].trim();
      sec = /^example\b/i.test(title) ? null : { title, file: '', intro: '', groups: [] };
      if (sec) sections.push(sec);
      grp = null;
      continue;
    }
    if (!sec) continue;
    const t = line.trim();
    const file = /^`([^`]+\.(?:kt|java))`$/.exec(t);
    if (file && !sec.file) { sec.file = file[1]; continue; }
    const it = /^\*([^*].*)\*\s*$/.exec(t);
    if (it && !sec.intro) { sec.intro = it[1].trim(); continue; }
    const b = /^\*\*(.+?)\*\*\s*$/.exec(t);
    if (b) { grp = { label: b[1].trim(), rows: [] }; sec.groups.push(grp); continue; }
    if (!t.startsWith('|')) continue;
    const cells = t.replace(/^\||\|$/g, '').split('|').map(c => c.trim());
    if (!/^`[^`]+`$/.test(cells[0] || '')) continue;
    if (!grp) { grp = { label: 'Events', rows: [] }; sec.groups.push(grp); }
    const three = cells.length >= 3;
    grp.rows.push({ event: cells[0].replace(/`/g, ''), from: three ? stripMd(cells[1]) : '', when: stripMd(three ? cells.slice(2).join(' | ') : cells[1] || '') });
  }
  return { crashlytics: crash ? crash[1] : '', sections: sections.filter(s => s.groups.some(g => g.rows.length)) };
}

// requests/<file>.md
export function parseRequest(src, file) {
  const { data, body } = parseFrontMatter(src);
  return {
    file, id: file.replace(/\.md$/, ''),
    type: data.type === 'bug' ? 'bug' : 'feature',
    title: data.title || file,
    status: data.status || 'new',
    area: data.area || '', screen: data.screen || '', device: data.device || '', by: data.by || '',
    date: data.date || file.slice(0, 10), image: data.image || '', version: data.version || '',
    link: data.link || '', reason: data.reason || '',
    text: body.trim()
  };
}

// reports/<YYYY-MM-DD>.md → entries ("## HH:MM · Title" + "- key: value" meta + free text)
export function parseReport(src, file) {
  const { data, body } = parseFrontMatter(src);
  const date = data.date || file.replace(/\.md$/, '');
  const entries = [];
  body.split(/^##\s+/m).slice(1).forEach((part, i) => {
    const [head, ...rest] = part.split(/\r?\n/);
    const hm = /^(\d{1,2}:\d{2})\s*[·\-–]\s*(.*)$/.exec(head.trim());
    const e = { id: `${date}-${i}`, date, time: hm ? hm[1] : '', title: (hm ? hm[2] : head).trim(), hours: 0, areas: [], links: [], blockers: '', by: '', text: '' };
    const textLines = [];
    for (const l of rest) {
      const m = /^-\s*(hours|areas|links|blockers|by):\s*(.*)$/i.exec(l.trim());
      if (m && !textLines.some(t => t.trim())) {
        const k = m[1].toLowerCase(), v = m[2].trim();
        if (k === 'hours') e.hours = Number(v) || 0;
        else if (k === 'areas') e.areas = v.split(',').map(s => s.trim()).filter(Boolean);
        else if (k === 'links') e.links = v.split(',').map(s => s.trim()).filter(Boolean);
        else e[k] = v;
      } else textLines.push(l);
    }
    e.text = textLines.join('\n').trim();
    entries.push(e);
  });
  return { date, file, entries, hours: entries.reduce((n, e) => n + e.hours, 0) };
}
