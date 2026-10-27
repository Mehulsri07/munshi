import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { makeSendWhatsApp } from '../whatsapp.ts';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity, confirmOpportunity } from '../opportunities.ts';
import { AT, fresh, deps } from './helpers.ts';

const realFetch = globalThis.fetch;
let bodies: any[] = [];
const stubFetch = () => {
  bodies = [];
  globalThis.fetch = (async (_url: any, init: any) => { bodies.push(JSON.parse(init.body)); return new Response('{}', { status: 200 }); }) as any;
};
afterEach(() => { globalThis.fetch = realFetch; });

const ENV = { WA_TOKEN: 't', WA_PHONE_ID: 'p' };
const PARAMS = ['Sharma ji', '₹450', 'https://demo.test/pay/ab12'];

test('not configured: nothing is sent', async () => {
  assert.deepEqual(await makeSendWhatsApp({})('+911', 'hi', PARAMS), { sent: false, error: 'not_configured' });
});

test('with WA_TEMPLATE the reminder goes as that template with name, amount and link', async () => {
  stubFetch();
  assert.deepEqual(await makeSendWhatsApp({ ...ENV, WA_TEMPLATE: 'udhaar_reminder' })('+91 98765 43210', 'free text', PARAMS), { sent: true });
  assert.deepEqual(bodies[0], {
    messaging_product: 'whatsapp', to: '919876543210', type: 'template',
    template: { name: 'udhaar_reminder', language: { code: 'en' }, components: [{ type: 'body', parameters: PARAMS.map(text => ({ type: 'text', text })) }] },
  });
});

test('without a template, or without params, it sends free-form text', async () => {
  stubFetch();
  await makeSendWhatsApp(ENV)('+911', 'free text', PARAMS);
  await makeSendWhatsApp({ ...ENV, WA_TEMPLATE: 'udhaar_reminder', WA_TEMPLATE_LANG: 'hi' })('+911', 'free text');
  assert.deepEqual(bodies.map(b => b.type), ['text', 'text']);
  assert.equal(bodies[0].text.body, 'free text');
});

test('the udhaar reminder hands the template its name, amount and pay link', async () => {
  const calls: unknown[][] = [];
  const db = fresh();
  const opp = nextOpportunity(db, HERO_ID, AT)!;
  const r = await confirmOpportunity(db, opp.id, { ...deps, sendWhatsApp: async (...a) => { calls.push(a); return { sent: true }; } }, AT);
  assert.deepEqual(calls[0][2], ['Sharma ji', '₹450', r.payUrl]);
});
