import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const candidateRoot = process.argv[2];
if (!candidateRoot || process.argv.length !== 3 || !fs.statSync(candidateRoot, { throwIfNoEntry: false })?.isDirectory()) {
  console.error('usage: sanitize-public-markdown-links.mjs <candidate-package-root>');
  process.exit(2);
}

const root = path.resolve(candidateRoot);
const scriptPath = path.resolve(fileURLToPath(import.meta.url));
const markdownFiles = [];
let sanitizedLinks = 0;

function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (fullPath === scriptPath) continue;
    if (entry.isDirectory()) walk(fullPath);
    else if (entry.isFile() && entry.name.endsWith('.md')) markdownFiles.push(fullPath);
  }
}

function isExternalTarget(target) {
  return !target || target.startsWith('/') || target.startsWith('#') || /^[a-z][a-z0-9+.-]*:/i.test(target);
}

function sanitizeFile(filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const sanitized = source.replace(/(?<!\!)\[([^\]]+)\]\(([^)\n]+)\)/g, (link, label, rawTarget) => {
    const target = rawTarget.trim().replace(/^<|>$/g, '').split(/[?#]/, 1)[0];
    if (isExternalTarget(target)) return link;

    let decodedTarget = target;
    try {
      decodedTarget = decodeURIComponent(target);
    } catch {
      // Keep the original target for the existence check when decoding fails.
    }

    const resolved = path.resolve(path.dirname(filePath), decodedTarget);
    const isInsideCandidate = resolved === root || resolved.startsWith(`${root}${path.sep}`);
    if (isInsideCandidate && fs.existsSync(resolved)) return link;

    sanitizedLinks += 1;
    return label;
  });

  if (sanitized !== source) fs.writeFileSync(filePath, sanitized);
}

walk(root);
for (const filePath of markdownFiles) sanitizeFile(filePath);

console.log(`PUBLIC_MARKDOWN_LINKS_PASS (${markdownFiles.length} markdown files, ${sanitizedLinks} internal links omitted)`);
