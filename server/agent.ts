import type { DB } from './db.ts';
import type { ActionResult, Deps } from './actions.ts';
import { confirmOpportunity, declineOpportunity, getProposed } from './opportunities.ts';
import { DECLINED, NOT_UNDERSTOOD } from './lines.ts';

// A reply is yes (or no) when every word is a yes-word or a polite filler and at least one
// is a real yes. "हाँ भेज दो", "ok ji", "mat bhejo" all count; "haan par kal" is for the phone's model.
const words = (s: string) => new Set(s.split(' '));
const YES_CORE = words('haan haa han ha hanji haanji yes ok okay theek thik zaroor bhej bhejo kar karo हाँ हां हा ओके ठीक ज़रूर जरूर भेज भेजो कर करो');
const YES_FILLER = words('ji जी do दो de दे dijiye दीजिए hai है please');
const NO_CORE = words('nahi nahin na no mat nako rehne नहीं नही ना मत रहने');
const NO_FILLER = words('ji जी chahiye चाहिए abhi अभी do दो bhej bhejo kar karo भेज भेजो कर करो hai है');

const allOf = (tokens: string[], core: Set<string>, filler: Set<string>) =>
  tokens.some(t => core.has(t)) && tokens.every(t => core.has(t) || filler.has(t));

export { DECLINED, NOT_UNDERSTOOD };

export function classifyReply(text: string): 'yes' | 'no' | 'other' {
  const tokens = text.normalize('NFC').toLowerCase().replace(/[\p{P}\p{S}]/gu, ' ').split(/\s+/).filter(Boolean);
  // A trailing "na" after other words is a softener ("bhej do na" = please send), not a no.
  if (tokens.length > 1 && ['na', 'ना'].includes(tokens[tokens.length - 1])) tokens.pop();
  if (!tokens.length) return 'other';
  if (allOf(tokens, NO_CORE, NO_FILLER)) return 'no';
  return allOf(tokens, YES_CORE, YES_FILLER) ? 'yes' : 'other';
}

type Reply = ActionResult & { opportunityId?: number };

// Yes or no on the open proposal. Anything else is answered on the phone (POST /api/propose for actions).
export async function converse(db: DB, merchantId: string, text: string, at: Date, deps: Deps): Promise<Reply> {
  const intent = classifyReply(text);
  const proposed = getProposed(db, merchantId);
  if (proposed && intent === 'yes') return { ...(await confirmOpportunity(db, proposed.id, deps, at)), opportunityId: proposed.id };
  if (proposed && intent === 'no') { declineOpportunity(db, proposed.id); return { line: DECLINED, opportunityId: proposed.id }; }
  return { line: NOT_UNDERSTOOD };
}
