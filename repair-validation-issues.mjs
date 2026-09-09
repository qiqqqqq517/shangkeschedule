/**
 * Targeted repair script for claude-style-page validation issues.
 * Fixes:
 *  1. import.html + semesters.html: inject missing Tailwind CDN + @theme inline + @layer base + semantic fallback
 *  2. components/course-edit/semesters: move custom <style> blocks from <head> to before </body>
 *  3. today.html + semesters.html: add data-nav-key to bottom nav items
 *  4. import.html: add body background class
 *
 * Usage: node repair-validation-issues.mjs <design-project-path>
 */
import fs from 'node:fs';
import path from 'node:path';

const designDir = process.argv[2];
if (!designDir) {
  console.error('Usage: node repair-validation-issues.mjs <design-project-path>');
  process.exit(1);
}

const pagesDir = path.join(designDir, 'pages');

// ── Infrastructure block to inject (prefixless, matches Claude CSS variables) ──
const INFRA_BLOCK = `    <script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4.3.1/dist/index.global.js"></script>
    <script src="https://unpkg.com/lucide@1.8.0/dist/umd/lucide.min.js"></script>
    <style type="text/tailwindcss">
  @theme inline {
    --color-background: var(--background);
    --color-foreground: var(--foreground);
    --color-card: var(--card);
    --color-card-foreground: var(--card-foreground);
    --color-popover: var(--popover);
    --color-popover-foreground: var(--popover-foreground);
    --color-primary: var(--primary);
    --color-primary-foreground: var(--primary-foreground);
    --color-secondary: var(--secondary);
    --color-secondary-foreground: var(--secondary-foreground);
    --color-muted: var(--muted);
    --color-muted-foreground: var(--muted-foreground);
    --color-accent: var(--accent);
    --color-accent-foreground: var(--accent-foreground);
    --color-destructive: var(--destructive);
    --color-destructive-foreground: var(--destructive-foreground);
    --color-border: var(--border);
    --color-input: var(--input);
    --color-ring: var(--ring);
    --color-chart-1: var(--chart-1);
    --color-chart-2: var(--chart-2);
    --color-chart-3: var(--chart-3);
    --color-chart-4: var(--chart-4);
    --color-chart-5: var(--chart-5);
    --color-sidebar: var(--sidebar);
    --color-sidebar-foreground: var(--sidebar-foreground);
    --color-sidebar-primary: var(--sidebar-primary);
    --color-sidebar-primary-foreground: var(--sidebar-primary-foreground);
    --color-sidebar-accent: var(--sidebar-accent);
    --color-sidebar-accent-foreground: var(--sidebar-accent-foreground);
    --radius-sm: var(--radius-sm);
    --radius-md: var(--radius-md);
    --radius-lg: var(--radius-xl);
  }
  @layer base {
    body { background: var(--background); color: var(--foreground); }
    td, th { word-break: break-all; word-break: auto-phrase; }
    th { white-space: nowrap; }
  }
    </style>
    <style id="semantic-token-fallback">
      .bg-background { background-color: var(--background); }
      .text-background { color: var(--background); }
      .border-background { border-color: var(--background); }
      .bg-foreground { background-color: var(--foreground); }
      .text-foreground { color: var(--foreground); }
      .border-foreground { border-color: var(--foreground); }
      .bg-card { background-color: var(--card); }
      .text-card { color: var(--card); }
      .border-card { border-color: var(--card); }
      .bg-card-foreground { background-color: var(--card-foreground); }
      .text-card-foreground { color: var(--card-foreground); }
      .border-card-foreground { border-color: var(--card-foreground); }
      .bg-popover { background-color: var(--popover); }
      .text-popover { color: var(--popover); }
      .border-popover { border-color: var(--popover); }
      .bg-popover-foreground { background-color: var(--popover-foreground); }
      .text-popover-foreground { color: var(--popover-foreground); }
      .border-popover-foreground { border-color: var(--popover-foreground); }
      .bg-primary { background-color: var(--primary); }
      .text-primary { color: var(--primary); }
      .border-primary { border-color: var(--primary); }
      .bg-primary-foreground { background-color: var(--primary-foreground); }
      .text-primary-foreground { color: var(--primary-foreground); }
      .border-primary-foreground { border-color: var(--primary-foreground); }
      .bg-secondary { background-color: var(--secondary); }
      .text-secondary { color: var(--secondary); }
      .border-secondary { border-color: var(--secondary); }
      .bg-secondary-foreground { background-color: var(--secondary-foreground); }
      .text-secondary-foreground { color: var(--secondary-foreground); }
      .border-secondary-foreground { border-color: var(--secondary-foreground); }
      .bg-muted { background-color: var(--muted); }
      .text-muted { color: var(--muted); }
      .border-muted { border-color: var(--muted); }
      .bg-muted-foreground { background-color: var(--muted-foreground); }
      .text-muted-foreground { color: var(--muted-foreground); }
      .border-muted-foreground { border-color: var(--muted-foreground); }
      .bg-accent { background-color: var(--accent); }
      .text-accent { color: var(--accent); }
      .border-accent { border-color: var(--accent); }
      .bg-accent-foreground { background-color: var(--accent-foreground); }
      .text-accent-foreground { color: var(--accent-foreground); }
      .border-accent-foreground { border-color: var(--accent-foreground); }
      .bg-destructive { background-color: var(--destructive); }
      .text-destructive { color: var(--destructive); }
      .border-destructive { border-color: var(--destructive); }
      .bg-destructive-foreground { background-color: var(--destructive-foreground); }
      .text-destructive-foreground { color: var(--destructive-foreground); }
      .border-destructive-foreground { border-color: var(--destructive-foreground); }
      .bg-border { background-color: var(--border); }
      .text-border { color: var(--border); }
      .border-border { border-color: var(--border); }
      .bg-input { background-color: var(--input); }
      .text-input { color: var(--input); }
      .border-input { border-color: var(--input); }
      .bg-ring { background-color: var(--ring); }
      .text-ring { color: var(--ring); }
      .border-ring { border-color: var(--ring); }
      .bg-chart-1 { background-color: var(--chart-1); }
      .text-chart-1 { color: var(--chart-1); }
      .border-chart-1 { border-color: var(--chart-1); }
      .bg-chart-2 { background-color: var(--chart-2); }
      .text-chart-2 { color: var(--chart-2); }
      .border-chart-2 { border-color: var(--chart-2); }
      .bg-chart-3 { background-color: var(--chart-3); }
      .text-chart-3 { color: var(--chart-3); }
      .border-chart-3 { border-color: var(--chart-3); }
      .bg-chart-4 { background-color: var(--chart-4); }
      .text-chart-4 { color: var(--chart-4); }
      .border-chart-4 { border-color: var(--chart-4); }
      .bg-chart-5 { background-color: var(--chart-5); }
      .text-chart-5 { color: var(--chart-5); }
      .border-chart-5 { border-color: var(--chart-5); }
      .bg-sidebar { background-color: var(--sidebar); }
      .text-sidebar { color: var(--sidebar); }
      .border-sidebar { border-color: var(--sidebar); }
      .bg-sidebar-foreground { background-color: var(--sidebar-foreground); }
      .text-sidebar-foreground { color: var(--sidebar-foreground); }
      .border-sidebar-foreground { border-color: var(--sidebar-foreground); }
      .bg-sidebar-primary { background-color: var(--sidebar-primary); }
      .text-sidebar-primary { color: var(--sidebar-primary); }
      .border-sidebar-primary { border-color: var(--sidebar-primary); }
      .bg-sidebar-primary-foreground { background-color: var(--sidebar-primary-foreground); }
      .text-sidebar-primary-foreground { color: var(--sidebar-primary-foreground); }
      .border-sidebar-primary-foreground { border-color: var(--sidebar-primary-foreground); }
      .bg-sidebar-accent { background-color: var(--sidebar-accent); }
      .text-sidebar-accent { color: var(--sidebar-accent); }
      .border-sidebar-accent { border-color: var(--sidebar-accent); }
      .bg-sidebar-accent-foreground { background-color: var(--sidebar-accent-foreground); }
      .text-sidebar-accent-foreground { color: var(--sidebar-accent-foreground); }
      .border-sidebar-accent-foreground { border-color: var(--sidebar-accent-foreground); }
    </style>
`;

const results = [];

function read(file) {
  return fs.readFileSync(path.join(pagesDir, file), 'utf8');
}
function write(file, content) {
  fs.writeFileSync(path.join(pagesDir, file), content, 'utf8');
}

// ── Fix 1 & 4: import.html — inject infra + add body bg ──
{
  let html = read('import.html');
  let changed = false;

  if (!html.includes('@tailwindcss/browser')) {
    html = html.replace('</head>', INFRA_BLOCK + '</head>');
    changed = true;
    results.push('import.html: injected Tailwind CDN + @theme + @layer base + semantic fallback');
  }

  if (html.includes('<body class="min-h-screen font-sans antialiased">')) {
    html = html.replace(
      '<body class="min-h-screen font-sans antialiased">',
      '<body class="min-h-screen font-sans antialiased bg-background text-foreground">'
    );
    changed = true;
    results.push('import.html: added body background + foreground classes');
  }

  // Move custom styles from head to body end
  const headMatch = html.match(/<head>([\s\S]*?)<\/head>/);
  if (headMatch) {
    const headContent = headMatch[1];
    const styleRegex = /<style(?=[ >])([^>]*)>([\s\S]*?)<\/style>/g;
    let m;
    const blocksToMove = [];
    while ((m = styleRegex.exec(headContent)) !== null) {
      const attrs = m[1];
      const isThemeVars = attrs.includes('id="theme-vars"');
      const isTailwind = attrs.includes('type="text/tailwindcss"');
      const isSemantic = attrs.includes('id="semantic-token-fallback"');
      const isComponentVars = attrs.includes('id="component-vars"');
      if (!isThemeVars && !isTailwind && !isSemantic && !isComponentVars) {
        blocksToMove.push(m[0]);
      }
    }
    for (const block of blocksToMove) {
      html = html.replace(block, '');
      html = html.replace('</body>', `    ${block}\n</body>`);
      changed = true;
      results.push('import.html: moved custom <style> from <head> to before </body>');
    }
  }

  // Add data-nav-key to bottom nav items
  if (html.includes('bottom-nav') && !html.includes('data-nav-key=')) {
    const navItemRegex = /(<div class="nav-item[^"]*")(>)/g;
    const keys = ['today', 'schedule', 'import', 'profile'];
    let idx = 0;
    html = html.replace(navItemRegex, (match, p1, p2) => {
      const key = keys[idx++] || `nav-${idx}`;
      return `${p1} data-nav-key="${key}"${p2}`;
    });
    if (idx > 0) {
      changed = true;
      results.push(`import.html: added data-nav-key to ${idx} nav items`);
    }
  }

  if (changed) write('import.html', html);
}

// ── Fix 1 & 2 & 3: semesters.html — inject infra + move style + fix nav ──
{
  let html = read('semesters.html');
  let changed = false;

  if (!html.includes('@tailwindcss/browser')) {
    html = html.replace('</head>', INFRA_BLOCK + '</head>');
    changed = true;
    results.push('semesters.html: injected Tailwind CDN + @theme + @layer base + semantic fallback');
  }

  const headMatch = html.match(/<head>([\s\S]*?)<\/head>/);
  if (headMatch) {
    const headContent = headMatch[1];
    const styleRegex = /<style(?=[ >])([^>]*)>([\s\S]*?)<\/style>/g;
    let m;
    const blocksToMove = [];
    while ((m = styleRegex.exec(headContent)) !== null) {
      const attrs = m[1];
      const isThemeVars = attrs.includes('id="theme-vars"');
      const isTailwind = attrs.includes('type="text/tailwindcss"');
      const isSemantic = attrs.includes('id="semantic-token-fallback"');
      if (!isThemeVars && !isTailwind && !isSemantic) {
        blocksToMove.push(m[0]);
      }
    }
    for (const block of blocksToMove) {
      html = html.replace(block, '');
      html = html.replace('</body>', `    ${block}\n</body>`);
      changed = true;
      results.push('semesters.html: moved custom <style> from <head> to before </body>');
    }
  }

  if (html.includes('bottom-nav') && !html.includes('data-nav-key=')) {
    const navItemRegex = /(<div class="nav-item[^"]*")(>)/g;
    const keys = ['today', 'schedule', 'import', 'profile'];
    let idx = 0;
    html = html.replace(navItemRegex, (match, p1, p2) => {
      const key = keys[idx++] || `nav-${idx}`;
      return `${p1} data-nav-key="${key}"${p2}`;
    });
    if (idx > 0) {
      changed = true;
      results.push(`semesters.html: added data-nav-key to ${idx} nav items`);
    }
  }

  if (changed) write('semesters.html', html);
}

// ── Fix 2: components.html — move custom style from head to body ──
{
  let html = read('components.html');
  let changed = false;
  const headMatch = html.match(/<head>([\s\S]*?)<\/head>/);
  if (headMatch) {
    const headContent = headMatch[1];
    const styleRegex = /<style(?=[ >])([^>]*)>([\s\S]*?)<\/style>/g;
    let m;
    const blocksToMove = [];
    while ((m = styleRegex.exec(headContent)) !== null) {
      const attrs = m[1];
      const isThemeVars = attrs.includes('id="theme-vars"');
      const isTailwind = attrs.includes('type="text/tailwindcss"');
      const isSemantic = attrs.includes('id="semantic-token-fallback"');
      if (!isThemeVars && !isTailwind && !isSemantic) {
        blocksToMove.push(m[0]);
      }
    }
    for (const block of blocksToMove) {
      html = html.replace(block, '');
      html = html.replace('</body>', `    ${block}\n</body>`);
      changed = true;
      results.push('components.html: moved custom <style> from <head> to before </body>');
    }
    if (changed) write('components.html', html);
  }
}

// ── Fix 2: course-edit.html — move custom style from head to body ──
{
  let html = read('course-edit.html');
  let changed = false;
  const headMatch = html.match(/<head>([\s\S]*?)<\/head>/);
  if (headMatch) {
    const headContent = headMatch[1];
    const styleRegex = /<style(?=[ >])([^>]*)>([\s\S]*?)<\/style>/g;
    let m;
    const blocksToMove = [];
    while ((m = styleRegex.exec(headContent)) !== null) {
      const attrs = m[1];
      const isThemeVars = attrs.includes('id="theme-vars"');
      const isTailwind = attrs.includes('type="text/tailwindcss"');
      const isSemantic = attrs.includes('id="semantic-token-fallback"');
      if (!isThemeVars && !isTailwind && !isSemantic) {
        blocksToMove.push(m[0]);
      }
    }
    for (const block of blocksToMove) {
      html = html.replace(block, '');
      html = html.replace('</body>', `    ${block}\n</body>`);
      changed = true;
      results.push('course-edit.html: moved custom <style> from <head> to before </body>');
    }
    if (changed) write('course-edit.html', html);
  }
}

// ── Fix 3: today.html — add data-nav-key ──
{
  let html = read('today.html');
  let changed = false;
  if (html.includes('bottom-nav') && !html.includes('data-nav-key=')) {
    const navItemRegex = /(<div class="nav-item[^"]*")(>)/g;
    const keys = ['today', 'schedule', 'import', 'profile'];
    let idx = 0;
    html = html.replace(navItemRegex, (match, p1, p2) => {
      const key = keys[idx++] || `nav-${idx}`;
      return `${p1} data-nav-key="${key}"${p2}`;
    });
    if (idx > 0) {
      changed = true;
      results.push(`today.html: added data-nav-key to ${idx} nav items`);
      write('today.html', html);
    }
  }
}

console.log('=== Repair Results ===');
for (const r of results) console.log('  OK', r);
console.log(`Total: ${results.length} fix(es)`);
