import { spawn } from 'node:child_process';
import { createReadStream } from 'node:fs';
import fs from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import {
  expectedScreenshotFiles,
  loadCases,
  type VisualCase,
} from './cases.js';
import {
  defaultArtifactRoot,
  defaultCasesPath,
  defaultReferencePath,
  findRepoRoot,
  resolveFromRepoRoot,
  toolRoot,
} from './paths.js';

type CliOptions = {
  casesPath: string;
  artifactRoot: string;
  referencePath: string;
  port: number;
};

const PLATFORMS = ['web-webgpu'];

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));

  async function freshCases() {
    const resolvedCases = await loadCases(options.casesPath);
    return resolvedCases.cases
      .filter((c) => !c.skip)
      .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  }

  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url ?? '/', 'http://127.0.0.1');

      if (url.pathname === '/') {
        sendText(res, 200, buildHtml(), 'text/html; charset=utf-8');
        return;
      }

      if (url.pathname === '/api/cases') {
        const cases = await freshCases();
        const data = await buildCasesApi(
          cases,
          options.artifactRoot,
          options.referencePath,
        );
        sendText(
          res,
          200,
          JSON.stringify(data),
          'application/json; charset=utf-8',
        );
        return;
      }

      if (url.pathname === '/api/mark-stable' && req.method === 'POST') {
        const body = await readBody(req);
        const { caseId, stable } = JSON.parse(body);
        if (typeof caseId !== 'string' || caseId.length === 0) {
          sendText(
            res,
            400,
            JSON.stringify({ error: 'caseId is required' }),
            'application/json; charset=utf-8',
          );
          return;
        }
        const raw = JSON.parse(await fs.readFile(options.casesPath, 'utf8'));
        const entry = raw.cases?.find((c: { id: string }) => c.id === caseId);
        if (!entry) {
          sendText(
            res,
            404,
            JSON.stringify({ error: 'case not found' }),
            'application/json; charset=utf-8',
          );
          return;
        }
        const newStable = typeof stable === 'boolean' ? stable : true;
        if (newStable) {
          entry.stable = true;
        } else {
          delete entry.stable;
        }
        await fs.writeFile(
          options.casesPath,
          `${JSON.stringify(raw, null, 2)}\n`,
        );
        sendText(
          res,
          200,
          JSON.stringify({ ok: true }),
          'application/json; charset=utf-8',
        );
        return;
      }

      if (url.pathname === '/api/recapture' && req.method === 'POST') {
        const body = await readBody(req);
        const { caseId, platform } = JSON.parse(body);
        if (typeof caseId !== 'string' || caseId.length === 0) {
          sendText(
            res,
            400,
            JSON.stringify({ error: 'caseId is required' }),
            'application/json; charset=utf-8',
          );
          return;
        }
        if (!PLATFORMS.includes(platform)) {
          sendText(
            res,
            400,
            JSON.stringify({ error: `Invalid platform: ${platform}` }),
            'application/json; charset=utf-8',
          );
          return;
        }

        const scriptPath = path.join(toolRoot(), 'dist', 'capture-web.js');

        const args: string[] = [
          '--case',
          caseId,
          '--platform',
          platform,
          '--out',
          options.artifactRoot,
          '--cases',
          options.casesPath,
        ];

        try {
          await buildJvmRust();
          await runCommand(process.execPath, [scriptPath, ...args], {
            cwd: toolRoot(),
            label: 'Capture process',
          });
          sendText(
            res,
            200,
            JSON.stringify({ ok: true }),
            'application/json; charset=utf-8',
          );
        } catch (err) {
          const message = err instanceof Error ? err.message : String(err);
          sendText(
            res,
            500,
            JSON.stringify({ error: message }),
            'application/json; charset=utf-8',
          );
        }
        return;
      }

      const imgMatch = /^\/images\/([^/]+)\/([^/]+)\.png$/.exec(url.pathname);
      if (imgMatch) {
        const platform = imgMatch[1];
        const caseId = imgMatch[2];
        const filePath = path.join(
          options.artifactRoot,
          platform,
          'current',
          `${caseId}.png`,
        );
        await sendFile(res, filePath);
        return;
      }

      const refMatch = /^\/reference\/([^/]+)\.png$/.exec(url.pathname);
      if (refMatch) {
        const caseId = refMatch[1];
        const filePath = path.join(options.referencePath, `${caseId}.png`);
        await sendFile(res, filePath);
        return;
      }

      sendText(res, 404, 'Not found');
    } catch (error) {
      sendText(
        res,
        500,
        error instanceof Error ? error.message : String(error),
      );
    }
  });

  await new Promise<void>((resolve) =>
    server.listen(options.port, '127.0.0.1', resolve),
  );
  const initialCases = await freshCases();
  console.log(
    `Visual Snapshot Viewer running at http://127.0.0.1:${options.port}`,
  );
  console.log(
    `Serving ${initialCases.length} cases across ${PLATFORMS.length} platforms`,
  );
  console.log('Press Ctrl+C to stop');
}

async function buildCasesApi(
  cases: VisualCase[],
  artifactRoot: string,
  referencePath: string,
) {
  const result = [];
  for (const c of cases) {
    const images = expectedScreenshotFiles(c);
    const available: Record<string, boolean> = {};
    for (const platform of PLATFORMS) {
      const firstFile = path.join(artifactRoot, platform, 'current', images[0]);
      try {
        await fs.access(firstFile);
        available[platform] = true;
      } catch {
        available[platform] = false;
      }
    }
    let referenceAvailable = false;
    try {
      await fs.access(path.join(referencePath, images[0]));
      referenceAvailable = true;
    } catch {}
    result.push({
      id: c.id,
      description: c.description,
      width: c.width,
      height: c.height,
      images,
      available,
      referenceAvailable,
      stable: c.stable,
      tags: c.tags ?? [],
    });
  }
  return { platforms: PLATFORMS, cases: result };
}

function readBody(req: http.IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString()));
    req.on('error', reject);
  });
}

function buildJvmRust(): Promise<void> {
  const repoRoot = findRepoRoot();
  return runCommand('npm', ['run', 'build'], {
    cwd: path.join(repoRoot, 'crates/j2me-core'),
    label: 'j2me-web WASM build',
  });
}

function runCommand(
  command: string,
  args: string[],
  options: { cwd: string; label: string },
): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stderr = '';
    child.stdout.on('data', (chunk: Buffer) => {
      process.stdout.write(chunk);
    });
    child.stderr.on('data', (chunk: Buffer) => {
      const text = chunk.toString();
      stderr += text;
      process.stderr.write(text);
    });

    child.on('close', (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(
          new Error(
            `${options.label} exited with code ${code}${stderr ? `: ${stderr.slice(-500)}` : ''}`,
          ),
        );
      }
    });

    child.on('error', (err) => {
      reject(err);
    });
  });
}

function buildHtml(): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="google" content="notranslate">
<title>Visual Snapshot Viewer</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: #1a1a1a; color: #eee; padding: 16px; padding-top: 60px; }
h1 { font-size: 20px; margin-bottom: 16px; color: #fff; }
.filter-bar { position: fixed; top: 0; left: 0; right: 0; z-index: 100; background: #222; border-bottom: 1px solid #444; padding: 10px 16px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.filter-bar label { font-size: 13px; color: #ccc; cursor: pointer; padding: 4px 10px; border-radius: 4px; border: 1px solid #555; }
.filter-bar input[type="radio"],
.filter-bar input.tag-filter { display: none; }
.filter-bar input[type="radio"]:checked + label,
.filter-bar input.tag-filter:checked + label { background: #3a3a3a; color: #fff; border-color: #888; }
.filter-bar .separator { width: 1px; height: 20px; background: #555; margin: 0 4px; }
.filter-bar .platform-toggle { font-size: 13px; color: #ccc; cursor: pointer; display: flex; align-items: center; gap: 4px; }
.filter-bar .platform-toggle input[type="checkbox"] { cursor: pointer; }
.filter-bar .filter-group { display: inline-flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.filter-bar .filter-title { color: #888; font-size: 12px; text-transform: uppercase; letter-spacing: 0.4px; }
.case { margin-bottom: 24px; border: 1px solid #333; border-radius: 8px; overflow: hidden; }
.case-header { background: #252525; padding: 8px 12px; font-size: 14px; font-weight: 600; border-bottom: 1px solid #333; }
.case-header.is-stable { background: #1a3a2a; }
.case-header span { color: #888; font-weight: 400; margin-left: 8px; }
.copy-btn { margin-left: 6px; background: #444; border: none; color: #ccc; font-size: 11px; padding: 2px 6px; border-radius: 3px; cursor: pointer; vertical-align: middle; }
.copy-btn:hover { background: #555; color: #fff; }
.copy-btn.copied { background: #2a6; color: #fff; }
.stable-check { margin-left: 8px; vertical-align: middle; cursor: pointer; }
.stable-check:disabled { opacity: 0.6; cursor: wait; }
.stable-label { margin-left: 2px; font-size: 11px; color: #aaa; vertical-align: middle; cursor: pointer; }
.case-grid { display: flex; gap: 1px; overflow-x: auto; }
.case-col { border-right: 1px solid #333; padding: 4px; min-width: calc((100% - 2px) / 3); max-width: calc((100% - 2px) / 3); flex-shrink: 0; }
.case-col:last-child { border-right: none; }
.case-col-title { font-size: 11px; text-align: center; color: #aaa; padding: 4px 0; text-transform: uppercase; letter-spacing: 0.5px; height: 28px; display: flex; align-items: center; justify-content: center; }
.case-col img { width: 100%; height: auto; display: block; background: #fff; aspect-ratio: var(--case-ratio, 640/720); margin-bottom: 4px; }
.case-col img:last-child { margin-bottom: 0; }
.case-col .no-image { width: 100%; aspect-ratio: 640/720; display: flex; align-items: center; justify-content: center; background: #2a2a2a; color: #666; font-size: 12px; }
.recapture-btn { background: #444; border: 1px solid #555; color: #ccc; font-size: 11px; padding: 2px 6px; border-radius: 3px; cursor: pointer; margin-left: 6px; vertical-align: middle; }
.recapture-btn:hover { background: #555; color: #fff; }
.recapture-btn:disabled { opacity: 0.6; cursor: wait; }
.recapture-btn.error { border-color: #c44; color: #f88; }
</style>
</head>
<body>
<div class="filter-bar">
  <div class="filter-group">
    <input type="radio" name="filter" id="filter-all" value="all" checked><label for="filter-all">All</label>
    <input type="radio" name="filter" id="filter-stable" value="stable"><label for="filter-stable">Stable</label>
    <input type="radio" name="filter" id="filter-unstable" value="unstable"><label for="filter-unstable">Unstable</label>
  </div>
  <div class="separator"></div>
  <div class="filter-group" id="tag-filters"><span class="filter-title">Tags</span></div>
  <div style="flex:1;"></div>
  <span id="platform-toggles" style="display:inline-flex;gap:8px;align-items:center;"></span>
</div>
<h1>Visual Snapshot Viewer</h1>
<div id="root"></div>
<script>
(async () => {
  const res = await fetch('/api/cases');
  const data = await res.json();
  const root = document.getElementById('root');
  const caseEls = [];
  const savedFilter = sessionStorage.getItem('viewer-filter') || 'all';
  const savedTagFilter = sessionStorage.getItem('viewer-tag-filter') || 'all';
  if (savedFilter === 'stable' || savedFilter === 'unstable') {
    const radio = document.getElementById('filter-' + savedFilter);
    if (radio) radio.checked = true;
  }

  const allTags = Array.from(new Set(data.cases.flatMap(function(c) { return c.tags || []; }))).sort();
  const tagFilterContainer = document.getElementById('tag-filters');
  function addTagFilter(id, value, label, checked) {
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'tag-filter';
    input.id = id;
    input.value = value;
    input.className = 'tag-filter';
    input.checked = checked;
    const labelEl = document.createElement('label');
    labelEl.setAttribute('for', id);
    labelEl.textContent = label;
    tagFilterContainer.appendChild(input);
    tagFilterContainer.appendChild(labelEl);
  }
  addTagFilter('tag-filter-all', 'all', 'All tags', !allTags.includes(savedTagFilter));
  for (const tag of allTags) {
    addTagFilter('tag-filter-' + tag.replace(/[^a-zA-Z0-9_-]/g, '-'), tag, tag, savedTagFilter === tag);
  }

  // Platform visibility (including Reference)
  const allColumns = ['reference'].concat(data.platforms);
  const defaultCols = allColumns;
  const savedCols = JSON.parse(sessionStorage.getItem('viewer-columns') || 'null') || defaultCols;
  const visibleColumns = new Set(savedCols);

  // Build platform toggle checkboxes
  const toggleContainer = document.getElementById('platform-toggles');
  const columnLabels = { reference: 'Reference', 'web-webgpu': 'WebGPU' };
  for (const col of allColumns) {
    const lbl = document.createElement('label');
    lbl.className = 'platform-toggle';
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = visibleColumns.has(col);
    cb.setAttribute('data-col', col);
    lbl.appendChild(cb);
    lbl.appendChild(document.createTextNode(columnLabels[col] || col));
    toggleContainer.appendChild(lbl);
  }

  function updateColumnVisibility() {
    const allCols = document.querySelectorAll('.case-col');
    for (const col of allCols) {
      const colKey = col.getAttribute('data-col');
      col.style.display = visibleColumns.has(colKey) ? '' : 'none';
    }
    sessionStorage.setItem('viewer-columns', JSON.stringify(Array.from(visibleColumns)));
  }

  for (const c of data.cases) {
    const div = document.createElement('div');
    div.className = 'case';
    div.id = c.id;
    div.style.setProperty('--case-ratio', c.width + '/' + c.height);
    div.setAttribute('data-stable', c.stable ? 'true' : 'false');
    div.setAttribute('data-tags', (c.tags || []).join(' '));
    let grid = '';
    // Reference column
    const refImgHtml = c.referenceAvailable
      ? c.images.map(function(img) { return '<img src="/reference/' + img + '" loading="lazy" onerror="this.style.display=&quot;none&quot;">'; }).join('')
      : '<div class="no-image">No reference</div>';
    grid += '<div class="case-col" data-col="reference" style="' + (visibleColumns.has('reference') ? '' : 'display:none') + '"><div class="case-col-title">Reference</div>' + refImgHtml + '</div>';
    for (const p of data.platforms) {
      const hidden = visibleColumns.has(p) ? '' : 'display:none';
      const hasImg = c.available[p];
      const imgHtml = hasImg
        ? c.images.map(function(img) { return '<img src="/images/' + p + '/' + img + '" loading="lazy" onerror="this.style.display=&quot;none&quot;">'; }).join('')
        : '<div class="no-image">No image</div>';
      grid += '<div class="case-col" data-col="' + p + '" style="' + hidden + '"><div class="case-col-title">' + p + ' <button class="recapture-btn" data-case="' + c.id + '" data-platform="' + p + '">Recapture</button></div>' + imgHtml + '</div>';
    }

    const checkedAttr = c.stable ? ' checked' : '';
    const headerClass = 'case-header' + (c.stable ? ' is-stable' : '');
    div.innerHTML = '<div class="' + headerClass + '">' + c.id + '<button class="copy-btn" data-id="' + c.id + '">Copy</button><input type="checkbox" class="stable-check" id="stable-' + c.id + '" data-case="' + c.id + '"' + checkedAttr + '><label class="stable-label" for="stable-' + c.id + '">Stable</label>' + (c.description ? '<span>' + c.description + '</span>' : '') + '</div><div class="case-grid">' + grid + '</div>';
    root.appendChild(div);
    caseEls.push({ el: div, grid: div.querySelector('.case-grid'), tags: c.tags || [] });
  }
  applyFilter();

  // Platform toggle handler
  toggleContainer.addEventListener('change', function(e) {
    const cb = e.target;
    if (!cb || cb.type !== 'checkbox') return;
    const col = cb.getAttribute('data-col');
    if (cb.checked) { visibleColumns.add(col); } else { visibleColumns.delete(col); }
    updateColumnVisibility();
  });

  root.addEventListener('click', (e) => {
    const btn = e.target;
    if (btn && btn.classList && btn.classList.contains('copy-btn')) {
      const id = btn.getAttribute('data-id');
      navigator.clipboard.writeText(id).then(() => {
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        setTimeout(() => { btn.textContent = 'Copy'; btn.classList.remove('copied'); }, 1500);
      });
    }
    if (btn && btn.classList && btn.classList.contains('recapture-btn')) {
      const caseId = btn.getAttribute('data-case');
      const platform = btn.getAttribute('data-platform');
      btn.disabled = true;
      btn.textContent = 'Capturing...';
      btn.classList.remove('error');
      fetch('/api/recapture', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ caseId: caseId, platform: platform })
      }).then(function(r) { return r.json(); }).then(function(d) {
        if (d.ok) {
          btn.textContent = 'Done!';
          setTimeout(function() { location.reload(); }, 500);
        } else {
          btn.textContent = 'Failed';
          btn.classList.add('error');
          btn.title = d.error || 'Unknown error';
          btn.disabled = false;
          setTimeout(function() { btn.textContent = 'Recapture'; btn.classList.remove('error'); btn.title = ''; }, 5000);
        }
      }).catch(function(err) {
        btn.textContent = 'Error';
        btn.classList.add('error');
        btn.title = err.message;
        btn.disabled = false;
        setTimeout(function() { btn.textContent = 'Recapture'; btn.classList.remove('error'); btn.title = ''; }, 5000);
      });
    }
  });
  root.addEventListener('change', (e) => {
    const cb = e.target;
    if (!cb || !cb.classList || !cb.classList.contains('stable-check')) return;
    const caseId = cb.getAttribute('data-case');
    const newStable = cb.checked;
    if (!newStable) {
      if (!confirm('Unmark "' + caseId + '" as stable? It will no longer be compared in e2e tests.')) {
        cb.checked = true;
        return;
      }
    }
    cb.disabled = true;
    fetch('/api/mark-stable', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ caseId: caseId, stable: newStable })
    }).then(function(r) { return r.json(); }).then(function(d) {
      if (d.ok) {
        const header = cb.closest('.case-header');
        const caseDiv = cb.closest('.case');
        if (newStable) {
          if (header) header.classList.add('is-stable');
        } else {
          if (header) header.classList.remove('is-stable');
        }
        if (caseDiv) caseDiv.setAttribute('data-stable', String(newStable));
        applyFilter();
      } else {
        cb.checked = !newStable;
        alert('Failed: ' + (d.error || 'unknown error'));
      }
      cb.disabled = false;
    }).catch(function(err) {
      cb.checked = !newStable;
      cb.disabled = false;
      alert('Error: ' + err.message);
    });
  });
  function applyFilter() {
    const checked = document.querySelector('input[name="filter"]:checked');
    const filter = checked ? checked.value : 'all';
    const checkedTag = document.querySelector('input[name="tag-filter"]:checked');
    const tagFilter = checkedTag ? checkedTag.value : 'all';
    for (const el of caseEls) {
      const isStable = el.el.getAttribute('data-stable') === 'true';
      const matchesStable = filter === 'all' || (filter === 'stable' ? isStable : !isStable);
      const matchesTag = tagFilter === 'all' || el.tags.includes(tagFilter);
      el.el.style.display = matchesStable && matchesTag ? '' : 'none';
    }
    sessionStorage.setItem('viewer-filter', filter);
    sessionStorage.setItem('viewer-tag-filter', tagFilter);
  }
  document.querySelector('.filter-bar').addEventListener('change', function(e) {
    if (e.target && (e.target.name === 'filter' || e.target.name === 'tag-filter')) applyFilter();
  });
  // Scroll to hash target after DOM is built
  if (location.hash) {
    const target = document.getElementById(location.hash.slice(1));
    if (target) {
      if (target.style.display === 'none') {
        target.style.display = '';
      }
      target.scrollIntoView({ behavior: 'instant', block: 'start' });
    }
  }
  let ticking = false;
  let scrollListenEnabled = false;
  setTimeout(function() { scrollListenEnabled = true; }, 200);
  window.addEventListener('scroll', () => {
    if (!scrollListenEnabled || ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      ticking = false;
      let closest = caseEls[0];
      let minDist = Infinity;
      for (const el of caseEls) {
        if (el.el.style.display === 'none') continue;
        const d = Math.abs(el.el.getBoundingClientRect().top);
        if (d < minDist) { minDist = d; closest = el; }
      }
      if (closest && closest.el.id && location.hash !== '#' + closest.el.id) {
        history.replaceState(null, '', '#' + closest.el.id);
      }
    });
  });
})();
</script>
</body>
</html>`;
}

async function sendFile(
  res: http.ServerResponse,
  filePath: string,
): Promise<void> {
  try {
    const stat = await fs.stat(filePath);
    if (!stat.isFile()) {
      sendText(res, 404, 'Not found');
      return;
    }
    res.writeHead(200, {
      'content-type': 'image/png',
      'content-length': stat.size,
      'cache-control': 'no-cache, no-store, must-revalidate',
    });
    createReadStream(filePath).pipe(res);
  } catch {
    sendText(res, 404, 'Not found');
  }
}

function sendText(
  res: http.ServerResponse,
  status: number,
  text: string,
  contentType = 'text/plain; charset=utf-8',
): void {
  res.writeHead(status, {
    'content-type': contentType,
    'cache-control': 'no-cache, no-store, must-revalidate',
  });
  res.end(text);
}

function parseArgs(args: string[]): CliOptions {
  const repoRoot = findRepoRoot();
  let casesPath: string | undefined;
  let artifactRoot: string | undefined;
  let referencePath: string | undefined;
  let port = 3456;

  const normalizedArgs = args[0] === '--' ? args.slice(1) : args;

  for (let i = 0; i < normalizedArgs.length; i++) {
    const arg = normalizedArgs[i];
    switch (arg) {
      case '--cases':
        casesPath = requireValue(normalizedArgs, ++i, arg);
        break;
      case '--out':
        artifactRoot = requireValue(normalizedArgs, ++i, arg);
        break;
      case '--reference':
        referencePath = requireValue(normalizedArgs, ++i, arg);
        break;
      case '--port':
        port = Number(requireValue(normalizedArgs, ++i, arg));
        if (!Number.isFinite(port) || port <= 0) {
          throw new Error('--port must be a positive number');
        }
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return {
    casesPath: resolveFromRepoRoot(casesPath, defaultCasesPath(repoRoot)),
    artifactRoot: resolveFromRepoRoot(
      artifactRoot,
      defaultArtifactRoot(repoRoot),
    ),
    referencePath: resolveFromRepoRoot(
      referencePath,
      defaultReferencePath(repoRoot),
    ),
    port,
  };
}

function requireValue(args: string[], index: number, name: string): string {
  const value = args[index];
  if (!value) {
    throw new Error(`${name} requires a value`);
  }
  return value;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
