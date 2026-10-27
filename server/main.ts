import { openDb } from './db.ts';
import { seed } from './seed.ts';
import { now } from './clock.ts';
import { makeSendWhatsApp } from './whatsapp.ts';
import { createApp, type AppDeps } from './app.ts';

try { process.loadEnvFile(); } catch { /* .env is optional */ }
const env = process.env;

const deps: AppDeps = {
  sendWhatsApp: makeSendWhatsApp(env),
  publicUrl: (env.PUBLIC_URL ?? `http://localhost:${env.PORT ?? 3000}`).replace(/\/$/, ''),
  payLinkTtlMin: Number(env.PAY_LINK_TTL_MIN) || 60,
  demoKey: env.DEMO_KEY,
  demoPhone: env.DEMO_CUSTOMER_PHONE,
  waVerifyToken: env.WA_VERIFY_TOKEN,
};

const db = openDb(env.MUNSHI_DB ?? 'munshi.db');
seed(db, now(), { demoPhone: deps.demoPhone });

const port = Number(env.PORT ?? 3000);
createApp(db, deps).listen(port, () => {
  console.log(`Munshi on http://localhost:${port}  (public: ${deps.publicUrl}, whatsapp: ${env.WA_TOKEN ? 'on' : 'off'})`);
});
