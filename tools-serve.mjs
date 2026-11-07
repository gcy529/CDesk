import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const ROOT = fileURLToPath(new URL('.', import.meta.url));
const PORT = Number(process.argv[2] || 8890);
const MIME = { '.html': 'text/html;charset=utf-8', '.png': 'image/png', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' };
createServer(async (req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]);
  if (p === '/') p = '/preview-desk.html';
  try {
    const data = await readFile(join(ROOT, p));
    res.writeHead(200, { 'Content-Type': MIME[extname(p)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  } catch (e) { res.writeHead(404); res.end('404'); }
}).listen(PORT, () => { console.log('preview: http://127.0.0.1:' + PORT); });