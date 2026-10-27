// Free public address for the demo: a Cloudflare Quick Tunnel, then the server behind it with PUBLIC_URL set.
// Quick Tunnels change address on every start (re-pair the phone from /control) and do not carry SSE
// (the app long-polls). With TUNNEL_NAME and PUBLIC_URL set, runs that named tunnel instead.
import { spawn, spawnSync, type ChildProcess } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { tunnelUrl } from '../server/tunnel.ts';

try { process.loadEnvFile(); } catch { /* .env is optional */ }
const env = process.env;
const port = env.PORT ?? '3000';

if (spawnSync('cloudflared', ['--version']).error) {
  console.error('cloudflared not found. Install it: winget install Cloudflare.cloudflared  (macOS: brew install cloudflared)');
  process.exit(1);
}
if (env.TUNNEL_NAME && !env.PUBLIC_URL) {
  console.error('TUNNEL_NAME needs PUBLIC_URL: the https hostname routed to that tunnel.');
  process.exit(1);
}

const demoKey = env.DEMO_KEY || randomBytes(9).toString('base64url');
const args = env.TUNNEL_NAME ? ['tunnel', 'run', env.TUNNEL_NAME] : ['tunnel', '--no-autoupdate', '--url', `http://localhost:${port}`];
const tunnel = spawn('cloudflared', args, { stdio: ['ignore', 'pipe', 'pipe'] });
let server: ChildProcess | undefined;

function startServer(publicUrl: string) {
  if (server) return;
  console.log(`\nPublic: ${publicUrl}\nControl (pair the phone here): ${publicUrl}/control?key=${demoKey}\n`);
  server = spawn(process.execPath, ['--experimental-strip-types', '--no-warnings', 'server/main.ts'], {
    stdio: 'inherit',
    env: { ...env, PUBLIC_URL: publicUrl, DEMO_KEY: demoKey, PORT: port },
  });
  server.on('exit', code => { tunnel.kill(); process.exit(code ?? 0); });
}

const scan = (chunk: Buffer) => { const url = tunnelUrl(chunk.toString()); if (url && !env.TUNNEL_NAME) startServer(url); };
tunnel.stdout.on('data', scan);
tunnel.stderr.on('data', scan);
tunnel.on('exit', code => { console.error(`cloudflared exited (${code})`); server?.kill(); process.exit(1); });
if (env.TUNNEL_NAME) startServer(env.PUBLIC_URL!.replace(/\/$/, ''));
process.on('SIGINT', () => { tunnel.kill(); server?.kill(); process.exit(0); });
