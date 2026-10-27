import type { OppRow } from './opportunities.ts';
import { DECLINED, NOT_UNDERSTOOD, SHELF_IN_APP } from './lines.ts';

// A Hindi voice reads Devanagari well and Roman Hinglish badly, so the app speaks these
// when present. Names and product names stay in Roman (the voice reads them as English),
// and every digit and ₹ figure is copied exactly from the Roman line.

const rs = (n: number) => `₹${n.toLocaleString('en-IN')}`;

const proposals: Record<string, (p: any) => string> = {
  udhaar_overdue: p => p.relink
    ? `${p.customerName} का पहला लिंक खत्म हो गया। ₹${p.amount} अभी बाकी है। नया लिंक भेज दूँ?`
    : `${p.customerName} का ₹${p.amount} उधार ${p.days} दिन से बाकी है। रिमाइंडर भेज दूँ?`,
  stock_low: p => `${p.name} खत्म होने वाला है, सिर्फ़ ${p.estQty} बचे। ${p.distributor} से ${p.qty} मँगवा दूँ? ₹${p.total}।`,
  group_buy_open: p => `${p.name} का ग्रुप ऑर्डर चल रहा है: आप जुड़ें तो ${p.members} दुकानें, ${p.pct}% बचत, ₹${p.saving} बचेंगे। जुड़ जाऊँ?`,
  local_demand_spike: p => `आपके मोहल्ले की ${p.shops} दुकानों में ${p.label} की बिक्री ${p.uplift}% ऊपर है। ${p.name} का स्टॉक बढ़ाऊँ? ${p.qty} पैक, ₹${p.total}।`,
  lapsed_regulars: p => `${p.count} पक्के ग्राहक 3 हफ़्ते से नहीं आए। उन्हें ₹${p.offer} का ऑफ़र भेजूँ? Paytm ऐप से जाएगा, नाम आपको नहीं दिखेगा।`,
  cash_gap: p => `${p.dueDays} दिन में दिवाली स्टॉक का ${rs(p.due)} का बिल है, पैसे ${rs(p.gap)} कम पड़ेंगे। ${rs(p.principal)} का लोन ले लें? असली खर्च ${rs(p.cost)}, रोज़ ${rs(p.daily)} सेटलमेंट से कटेगा।`,
};

export const spokenFor = (opp: OppRow): string => proposals[opp.type](opp.payload);

const R = '(₹[\\d,]+)';
const lines: Array<[RegExp, string | ((...m: string[]) => string)]> = [
  [new RegExp(`^(.+) ne ${R} chuka diye$`), '$1 ने $2 चुका दिए'],
  [/^(.+) ka pay link khatam ho gaya\.$/, '$1 का पे लिंक खत्म हो गया।'],
  [new RegExp(`^${R} prapt hue$`), '$1 प्राप्त हुए'],
  [/^(.+) ko reminder bhej diya\.$/, '$1 को रिमाइंडर भेज दिया।'],
  [/^WhatsApp nahi gaya\. Yeh QR (.+) ko dikha dijiye\.$/, 'व्हाट्सऐप नहीं गया। यह QR $1 को दिखा दीजिए।'],
  [new RegExp(`^(.+) ko (\\d+) (.+) ka order bhej diya, ${R}\\.$`), '$1 को $2 $3 का ऑर्डर भेज दिया, $4।'],
  [new RegExp(`^Jud gaye! (\\d+) dukaanein, (\\d+)% bachat, ${R} bache\\.$`), 'जुड़ गए! $1 दुकानें, $2% बचत, $3 बचे।'],
  [new RegExp(`^(\\d+) purane grahakon ko Paytm app par ${R} ka offer bhej diya\\. Unki pehchaan aapko nahi dikhayi jaati\\.$`), '$1 पुराने ग्राहकों को Paytm ऐप पर $2 का ऑफ़र भेज दिया। उनकी पहचान आपको नहीं दिखाई जाती।'],
  [new RegExp(`^${R} aapke khaate mein aa gaye\\. Roz ${R} settlement se katega\\.$`), '$1 आपके खाते में आ गए। रोज़ $2 सेटलमेंट से कटेगा।'],
  [new RegExp(`^Dhanyavaad! Yeh grahak ki (\\d+)vi khareed hai, agli baar ${R} off\\.$`), 'धन्यवाद! यह ग्राहक की $1वीं खरीद है, अगली बार $2 की छूट।'],
  [new RegExp(`^Ek purana grahak ${R} offer se lauta! ${R} ki khareed\\.$`), 'एक पुराना ग्राहक $1 ऑफ़र से लौटा! $2 की खरीद।'],
  [/^Shelf dekh li: (.+) kam hain\.$/, (_m, names) => `शेल्फ़ देख ली: ${names.replace(/ aur /g, ' और ')} कम हैं।`],
  [/^Shelf dekh li, sab theek hai\.$/, 'शेल्फ़ देख ली, सब ठीक है।'],
];

const exact: Record<string, string> = {
  [DECLINED]: 'ठीक है, रहने देते हैं।',
  [NOT_UNDERSTOOD]: 'माफ़ कीजिए, समझ नहीं आया। हाँ या नहीं दबाइए।',
  [SHELF_IN_APP]: 'शेल्फ़ की फ़ोटो Munshi ऐप से खींचिए।',
};

export function toSpoken(text: string): string | undefined {
  if (exact[text]) return exact[text];
  for (const [re, out] of lines) {
    if (re.test(text)) return typeof out === 'string' ? text.replace(re, out) : text.replace(re, out as any);
  }
  return undefined;
}
