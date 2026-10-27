import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity } from '../opportunities.ts';
import { classifyReply, converse } from '../agent.ts';
import { AT, fresh, deps } from './helpers.ts';

test('classifyReply handles Roman and Devanagari', () => {
  for (const t of ['haan', 'Haan ji', 'हाँ', 'हां जी', 'theek hai', 'bhej do']) assert.equal(classifyReply(t), 'yes', t);
  for (const t of ['nahi', 'नहीं', 'rehne do']) assert.equal(classifyReply(t), 'no', t);
  for (const t of ['kitna udhaar hai?', 'haan par kal bhejna']) assert.equal(classifyReply(t), 'other', t);
});

test('classifyReply accepts natural spoken variants of yes and no', () => {
  for (const t of ['हाँ भेज दो', 'हां हां', 'ओके', 'हाँ,', 'Haan, bhej do!', 'ok ji', 'kar do', 'ठीक है जी', 'bhej do na', 'भेज दो ना']) assert.equal(classifyReply(t), 'yes', t);
  for (const t of ['नहीं चाहिए', 'nahi chahiye', 'na na', 'mat bhejo', 'अभी नहीं', 'नहीं।']) assert.equal(classifyReply(t), 'no', t);
  for (const t of ['haan nahi', 'haan par kal bhejna', 'pehle Pinky ko bhejo']) assert.equal(classifyReply(t), 'other', t);
});

test('converse yes confirms the proposal; no declines', async () => {
  const db = fresh();
  nextOpportunity(db, HERO_ID, AT);
  assert.equal((await converse(db, HERO_ID, 'हाँ', AT, deps)).line, 'Sharma ji ko reminder bhej diya.');
  nextOpportunity(db, HERO_ID, AT);
  assert.equal((await converse(db, HERO_ID, 'nahi', AT, deps)).line, 'Theek hai, rehne dete hain.');
});

test('converse answers anything but yes or no with the fallback line', async () => {
  assert.equal((await converse(fresh(), HERO_ID, 'kitna udhaar hai?', AT, deps)).line, 'Maaf kijiye, samajh nahi aaya. Haan ya Nahi dabaiye.');
});
