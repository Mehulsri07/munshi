import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HERO_ID } from '../seed.ts';
import { refreshOpportunities } from '../detectors.ts';
import { getOpportunity } from '../opportunities.ts';
import { template } from '../phrase.ts';
import { DECLINED, NOT_UNDERSTOOD } from '../lines.ts';
import { spokenFor, toSpoken } from '../spoken.ts';
import { AT, fresh } from './helpers.ts';

const DEVANAGARI = /[ऀ-ॿ]/;
const nums = (s: string) => (s.match(/\d[\d,]*/g) ?? []).map(n => n.replace(/,/g, '')).sort();

test('every proposal has Devanagari speech with exactly the template\'s numbers', () => {
  const db = fresh();
  refreshOpportunities(db, HERO_ID, AT);
  for (const { id } of db.prepare('SELECT id FROM opportunities').all() as any[]) {
    const opp = getOpportunity(db, id)!;
    const s = spokenFor(opp);
    assert.match(s, DEVANAGARI, opp.type);
    assert.deepEqual(nums(s), nums(template(opp)), opp.type);
  }
});

test('udhaar speech reads naturally', () => {
  const db = fresh();
  refreshOpportunities(db, HERO_ID, AT);
  const id = (db.prepare("SELECT id FROM opportunities WHERE type='udhaar_overdue' AND json_extract(payload,'$.amount')=450").get() as any).id;
  assert.equal(spokenFor(getOpportunity(db, id)!), 'Sharma ji का ₹450 उधार 12 दिन से बाकी है। रिमाइंडर भेज दूँ?');
});

test('toSpoken knows the lines Munshi says after an action or a payment', () => {
  const cases: Array<[string, string]> = [
    ['Sharma ji ne ₹450 chuka diye', 'Sharma ji ने ₹450 चुका दिए'],
    ['₹120 prapt hue', '₹120 प्राप्त हुए'],
    ['Sharma ji ko reminder bhej diya.', 'Sharma ji को रिमाइंडर भेज दिया।'],
    ['Jud gaye! 4 dukaanein, 8% bachat, ₹143 bache.', 'जुड़ गए! 4 दुकानें, 8% बचत, ₹143 बचे।'],
    ['₹50,000 aapke khaate mein aa gaye. Roz ₹570 settlement se katega.', '₹50,000 आपके खाते में आ गए। रोज़ ₹570 सेटलमेंट से कटेगा।'],
    ['Dhanyavaad! Yeh grahak ki 10vi khareed hai, agli baar ₹20 off.', 'धन्यवाद! यह ग्राहक की 10वीं खरीद है, अगली बार ₹20 की छूट।'],
    ['Ek purana grahak ₹15 offer se lauta! ₹340 ki khareed.', 'एक पुराना ग्राहक ₹15 ऑफ़र से लौटा! ₹340 की खरीद।'],
    ['Shelf dekh li: Surf Excel 1kg aur Parle-G kam hain.', 'शेल्फ़ देख ली: Surf Excel 1kg और Parle-G कम हैं।'],
    [DECLINED, 'ठीक है, रहने देते हैं।'],
    [NOT_UNDERSTOOD, 'माफ़ कीजिए, समझ नहीं आया। हाँ या नहीं दबाइए।'],
  ];
  for (const [roman, dev] of cases) assert.equal(toSpoken(roman), dev, roman);
});

test('toSpoken returns undefined for free text it does not know', () => {
  assert.equal(toSpoken('Aaj bikri achhi rahi, ₹3,200 aaye.'), undefined);
});
