import { useCallback, useEffect, useState } from 'react';
import { api, onEvents, rupees, type Ledger as LedgerData } from './api.ts';
import { Nav, Rupees, useCountUp } from './ui.tsx';

const KIND_LABEL: Record<string, string> = {
  udhaar_recovered: 'Udhaar wapas',
  group_saving: 'Group order bachat',
  winback_sale: 'Win-back bikri',
};
const when = (ts: string) => new Date(ts).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Kolkata' });

// Results-board strip: the week's money by kind, each figure rolling up as it lands.
function Score({ label, amount }: { label: string; amount: number }) {
  const shown = useCountUp(amount);
  return <div className="score-cell"><span>{label}</span><b className="num">{rupees(shown)}</b></div>;
}

function ScoreStrip({ items }: { items: LedgerData['items'] }) {
  const sum = (kind: string) => items.filter(i => i.kind === kind).reduce((s, i) => s + i.amount, 0);
  return (
    <div className="score" role="group" aria-label="Is hafte, kaam ke hisaab se">
      <div className="score-head">Is hafte</div>
      <Score label="Udhaar wapas" amount={sum('udhaar_recovered')} />
      <Score label="Group bachat" amount={sum('group_saving')} />
      <Score label="Win-back bikri" amount={sum('winback_sale')} />
    </div>
  );
}

export function Ledger() {
  const [data, setData] = useState<LedgerData | null>(null);
  const total = useCountUp(data?.total ?? 0);
  const [failed, setFailed] = useState(false);
  const load = useCallback(() => api.ledger().then(d => { setData(d); setFailed(false); }).catch(() => setFailed(true)), []);

  useEffect(() => {
    void load();
    return onEvents(e => { if (e.type === 'ledger') void load(); });
  }, [load]);

  return (
    <div className="shell">
      <Nav current="ledger" />
      <div className="masthead">
        <div className="wrap">
          <h1>Is hafte Munshi ne aapko dilaye</h1>
          <span className="big-amt num">{data ? <Rupees value={rupees(total)} /> : '₹—'}</span>
          <p>Har rupaya us kaam se juda hai jisse woh aaya.</p>
          {data && <ScoreStrip items={data.items} />}
        </div>
      </div>
      <div className="wrap">
        <h2 className="section-title">Hisaab</h2>
        {failed && <p className="empty">Hisaab load nahi hua. Server chal raha hai? Page dobara kholiye.</p>}
        {data && data.items.length === 0 && <p className="empty">Abhi tak kuch nahi. Soundbox par pehla “Haan” boliye.</p>}
        {data && data.items.length > 0 && (
          <ul className="rows">
            {data.items.map((item, k) => (
              <li key={`${item.ts}-${k}`}>
                <span className="label">{item.label}</span>
                <span className="amt num">+{rupees(item.amount)}</span>
                <span className="meta"><span className="num">{when(item.ts)}</span><span className="kind">{KIND_LABEL[item.kind] ?? item.kind}</span></span>
              </li>
            ))}
          </ul>
        )}

        {data?.loan && (
          <>
            <h2 className="section-title">Loan (kamaai mein nahi gina)</h2>
            <ul className="rows">
              <li>
                <span className="label">Diwali stock ke liye loan</span>
                <span className="amt plain num">{rupees(data.loan.principal)}</span>
                <span className="meta num">
                  <span>Roz {rupees(data.loan.dailyDeduction)} settlement se</span>
                  <span>{data.loan.tenureDays} din</span>
                  <span>Kul kharcha {rupees(data.loan.totalCost)}</span>
                </span>
              </li>
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
