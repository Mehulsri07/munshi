import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { nextOpportunity } from '../opportunities.ts';
import { phraseFull, template } from '../phrase.ts';
import { spokenFor } from '../spoken.ts';
import { AT, fresh } from './helpers.ts';

test('the server speaks the template line and its Devanagari', () => {
  const opp = nextOpportunity(fresh(), HERO_ID, AT)!;
  assert.deepEqual(phraseFull(opp), { line: template(opp), spoken: spokenFor(opp) });
  assert.equal(phraseFull(opp).line, 'Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?');
});
