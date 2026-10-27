import { useCallback, useEffect, useState } from 'react';
import { api, onEvents, rupees, type Impact as ImpactData } from './api.ts';
import { Nav, Rupees, useCountUp } from './ui.tsx';

const SCALES = [1, 1_000, 1_00_000, 10_00_000];
const count = (n: number) => n.toLocaleString('en-IN');

// What the week's money is made of: one money colour, parts told apart by label and a gap.
function MoneyMix({ week }: { week: ImpactData['week'] }) {
  const parts = [['Udhaar', week.recovered], ['Group savings', week.groupSavings], ['Win-back', week.winbackSales]] as const;
  const sum = parts.reduce((s, [, v]) => s + v, 0);
  return (
    <div className="mix" role="img" aria-label={parts.map(([l, v]) => `${l} ${Math.round((v / sum) * 100)}%`).join(', ')}>
      <div className="mix-bar">{parts.filter(([, v]) => v > 0).map(([l, v]) => <i key={l} style={{ flex: v }} />)}</div>
      <div className="mix-legend">{parts.filter(([, v]) => v > 0).map(([l, v]) => (
        <span key={l}><b className="num">{Math.round((v / sum) * 100)}%</b> {l}</span>
      ))}</div>
    </div>
  );
}

export function Impact() {
  const [n, setN] = useState(1_00_000);
  const [data, setData] = useState<ImpactData | null>(null);
  const income = useCountUp(data?.projected.weeklyIncome ?? 0, 1100);
  const [failed, setFailed] = useState(false);
  const load = useCallback(() => api.impact(n).then(d => { setData(d); setFailed(false); }).catch(() => setFailed(true)), [n]);

  useEffect(() => {
    void load();
    return onEvents(e => { if (e.type === 'ledger') void load(); });
  }, [load]);

  const w = data?.week;
  // Green is money in; loans are borrowed, so they render white like counts.
  const rows: Array<[string, number | undefined, 'money' | 'loan' | 'count']> = [
    ['Udhaar recovered', w?.recovered, 'money'],
    ['Group-buy savings', w?.groupSavings, 'money'],
    ['Win-back sales', w?.winbackSales, 'money'],
    ['Merchant loans disbursed', w?.loanGmv, 'loan'],
    ['Customers reached with app offers', w?.offersDelivered, 'count'],
    ['Actions approved by the merchant', w?.actions, 'count'],
  ];
  const fmt = (v: number, kind: 'money' | 'loan' | 'count') => (kind === 'count' ? count(v) : rupees(v));

  return (
    <div className="shell">
      <Nav current="paytm" />
      <div className="masthead">
        <div className="wrap">
          <h1>If {count(n)} {n === 1 ? 'merchant' : 'merchants'} had Ramesh Kirana’s week with Munshi</h1>
          <span className="big-amt long num">{data ? <Rupees value={rupees(income)} /> : '₹—'}</span>
          <p>Extra money in merchants’ hands per week: udhaar recovered, group savings and win-back sales.</p>
          {data && data.perMerchant > 0 && <MoneyMix week={data.week} />}
          <div className="picker" role="group" aria-label="Number of merchants">
            {SCALES.map(s => <button key={s} aria-pressed={s === n} onClick={() => setN(s)}>{count(s)}</button>)}
          </div>
        </div>
      </div>
      <div className="wrap">
        {failed && <p className="empty">Impact could not load. Is the server running?</p>}
        <div className="impact-head"><span>This week</span><span>Ramesh Kirana</span><span>× {count(n)}</span></div>
        <ul className="rows impact">
          {rows.map(([label, v, kind]) => (
            <li key={label}>
              <span className="label">{label}</span>
              <span className="here num">{v === undefined ? '—' : fmt(v, kind)}</span>
              <span className={`amt num${kind === 'money' ? '' : ' plain'}`}>{v === undefined ? '—' : fmt(v * n, kind)}</span>
            </li>
          ))}
        </ul>
        <p className="footnote">A projection of this demo’s synthetic week multiplied by the merchant count. It is not a forecast and uses no real Paytm data.</p>
      </div>
    </div>
  );
}
