import { useCallback, useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { api, onEvents, rupees, ApiError, type ActionResult, type Ledger, type ShelfResult } from './api.ts';
import { speak, listen, canListen, chime } from './speech.ts';
import { Caption, Clock, OnAir, Ticker, useCountUp, Wordmark, Rupees, MicIcon, CameraIcon, CheckIcon, CoinIcon, type AirMode, type Tone } from './ui.tsx';

type Answer = { kind: 'yes' } | { kind: 'no' } | { kind: 'text'; text: string } | { kind: 'skip' };
const OOPS = 'Kuch gadbad hui. Dobara koshish kijiye.';

// Phone photos are large; a 1280px JPEG is plenty to read a shelf and uploads fast.
async function photoToJpeg(file: File): Promise<string> {
  const bmp = await createImageBitmap(file);
  const scale = Math.min(1, 1280 / Math.max(bmp.width, bmp.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bmp.width * scale);
  canvas.height = Math.round(bmp.height * scale);
  canvas.getContext('2d')!.drawImage(bmp, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL('image/jpeg', 0.82).split(',')[1];
}

export function Soundbox() {
  const [started, setStarted] = useState(false);
  const [caption, setCaption] = useState({ text: 'Namaste! Munshi taiyaar hai.', tone: 'info' as Tone, n: 0 });
  const [pending, setPending] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [talking, setTalking] = useState(false);
  const [listening, setListening] = useState(false);
  const [pay, setPay] = useState<{ url: string; name?: string; amount?: number } | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [band, setBand] = useState<{ text: string; n: number } | null>(null);
  const [live, setLive] = useState(false);
  const [heard, setHeard] = useState('');
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const weekShown = useCountUp(ledger?.total ?? 0);
  const answerRef = useRef<((a: Answer) => void) | null>(null);
  const runningRef = useRef(false);

  const say = useCallback(async (text: string, tone: Tone) => {
    setCaption(c => ({ text, tone, n: c.n + 1 }));
    setTalking(true);
    await speak(text);
    setTalking(false);
  }, []);

  const showResult = (r: ActionResult) => {
    if (r.payUrl) setPay({ url: r.payUrl, name: r.payFor?.name, amount: r.payFor?.amount });
  };

  // Ask one proposal (speaking it unless already spoken), wait for the merchant, act on the answer.
  // A pay QR from an earlier answer stays up until that link is paid ('paid' event).
  const resolveProposal = useCallback(async (id: number, line: string | null): Promise<'skipped' | 'done'> => {
    // Accept an answer while the question is still being spoken: shopkeepers answer early.
    const answered = new Promise<Answer>(res => { answerRef.current = res; });
    setPending(id);
    if (line) void say(line, 'ask');
    const answer = await answered;
    answerRef.current = null;
    setPending(null);
    if (answer.kind === 'skip') return 'skipped';
    setBusy(true);
    let r: ActionResult;
    let tone: Tone = answer.kind === 'no' ? 'decline' : 'info';
    try {
      r = answer.kind === 'yes' ? await api.confirm(id) : answer.kind === 'no' ? await api.decline(id) : await api.converse(answer.text);
    } catch {
      r = { line: OOPS };
      tone = 'decline';
    }
    setBusy(false);
    showResult(r);
    await say(r.line, tone);
    if (answer.kind === 'text' && r.opportunityId && r.opportunityId !== id && !r.payUrl) await resolveProposal(r.opportunityId, null);
    return 'done';
  }, [say]);

  // Run proposals one after another; scope 'brief' is the morning list, 'all' includes credit and win-back.
  const runSession = useCallback(async (opts: { greet: boolean; scope: 'brief' | 'all'; max: number; interrupt?: boolean }) => {
    if (runningRef.current) return;
    runningRef.current = true;
    try {
      if (opts.greet) await say('Suprabhat Ramesh ji! Aaj ke kaam sunaata hoon.', 'info');
      let handled = 0;
      while (handled < opts.max) {
        const next = await api.next({ scope: opts.scope, interrupt: opts.interrupt && handled === 0 });
        if (!next.id) break;
        // A shelf reading skips the pending question; that is not a handled item,
        // and the next round picks up the restock offer the shelf just proposed.
        if (await resolveProposal(next.id, next.line ?? '') === 'done') handled++;
      }
      if (opts.greet) await say(handled ? 'Bas itna hi. Aapka din shubh ho!' : 'Aaj koi naya kaam nahi. Aapka din shubh ho!', 'info');
    } catch {
      await say(OOPS, 'decline');
    } finally {
      runningRef.current = false;
    }
  }, [say, resolveProposal]);

  // A shelf reading interrupts whatever is pending: say what was seen, then offer the restock.
  const onShelf = useCallback(async (r: ShelfResult) => {
    if (runningRef.current) {
      answerRef.current?.({ kind: 'skip' });
      await say(r.line, 'info');
      return; // the running session picks up the proposed restock next
    }
    runningRef.current = true;
    try {
      await say(r.line, 'info');
      if (r.next) await resolveProposal(r.next.id, r.next.line);
    } finally {
      runningRef.current = false;
    }
  }, [say, resolveProposal]);

  const answer = (a: Answer) => answerRef.current?.(a);

  const onMic = async () => {
    if (listening) return;
    setListening(true);
    setHeard('');
    const text = (await listen()).trim();
    setListening(false);
    if (!text) { setHeard('Sunaai nahi diya. Haan ya Nahi dabaiye.'); return; }
    setHeard(text);
    if (answerRef.current) return answer({ kind: 'text', text });
    if (runningRef.current) return;
    runningRef.current = true;
    try {
      setBusy(true);
      const r = await api.converse(text).catch(() => ({ line: OOPS }) as ActionResult);
      setBusy(false);
      showResult(r);
      await say(r.line, 'info');
      if (r.opportunityId && !r.payUrl) await resolveProposal(r.opportunityId, null);
    } finally {
      runningRef.current = false;
    }
  };

  const onPhoto = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    try {
      const r = await api.shelf(await photoToJpeg(file), 'image/jpeg');
      setBusy(false);
      await onShelf(r);
    } catch (e) {
      setBusy(false);
      // 503 (no vision) and 422 (unreadable photo) carry a line meant for the merchant.
      await say(e instanceof ApiError && (e.status === 503 || e.status === 422) ? e.message : OOPS, 'decline');
    }
  };

  useEffect(() => {
    if (!pay) { setQr(null); return; }
    QRCode.toDataURL(pay.url, { margin: 1, width: 360, color: { dark: '#1A1815', light: '#ffffff' } }).then(setQr);
  }, [pay]);

  useEffect(() => {
    if (!started) return;
    const loadWeek = () => api.ledger().then(setLedger).catch(() => {});
    void loadWeek();
    const off = onEvents(e => {
      if (e.type === 'announce') {
        setBand(b => ({ text: e.text, n: (b?.n ?? 0) + 1 }));
        void speak(e.text);
      }
      if (e.type === 'reset') location.reload(); // back to the gate, nothing stale left pending
      if (e.type === 'ledger') void loadWeek();
      if (e.type === 'paid') setPay(p => (p && p.url.endsWith(`/${e.ref}`) ? null : p));
      if (e.type === 'brief') void runSession({ greet: true, scope: 'brief', max: 3 });
      if (e.type === 'nudge') void runSession({ greet: false, scope: 'all', max: 1 });
      if (e.type === 'shelf') void onShelf(e);
    }, setLive);
    // Speak up on its own when the shop is quiet (the server decides what quiet means).
    // Opt-in with ?idle: on stage the operator cues every beat, and a stray tick would jump the script.
    const idleOn = new URLSearchParams(location.search).has('idle');
    const idle = idleOn ? setInterval(() => { void runSession({ greet: false, scope: 'all', max: 1, interrupt: true }); }, 60_000) : undefined;
    return () => { off(); clearInterval(idle); };
  }, [started, runSession, onShelf]);

  useEffect(() => {
    if (!band) return;
    const t = setTimeout(() => setBand(null), 6500);
    return () => clearTimeout(t);
  }, [band]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!answerRef.current || e.target instanceof HTMLInputElement) return;
      if (e.key === 'h' || e.key === 'H') answer({ kind: 'yes' });
      if (e.key === 'n' || e.key === 'N') answer({ kind: 'no' });
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  if (!started) {
    return (
      <main className="gate">
        <div className="gate-mark">
          <span className="kicker">Ramesh Kirana · Aliganj</span>
          <h1>Munshi</h1>
          <p>Paise ka kaam bataata hai, aapki haan par karta hai.</p>
        </div>
        <div className="gate-cta">
          <div className="dock">
            <button className="btn btn-ask btn-wide" autoFocus onClick={() => { chime(); setStarted(true); void runSession({ greet: true, scope: 'brief', max: 3 }); }}>
              Dukaan kholo
            </button>
            <small>Tap karte hi Munshi bolna shuru karega.</small>
          </div>
        </div>
      </main>
    );
  }

  const air: AirMode = listening ? 'listen' : busy ? 'busy' : talking ? 'talk' : pending ? 'ask' : 'idle';
  const ref = pay?.url.split('/').pop();
  const today = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Asia/Kolkata' });

  return (
    <div className="soundbox">
      <header className="bar">
        <Wordmark />
        <span className="spacer" />
        {ledger && <span className="week-total num">Is hafte <b>{rupees(weekShown)}</b></span>}
        <span className="live" data-on={live}><i /><span className="sr-only">{live ? 'Live' : 'Offline'}</span></span>
        <Clock />
      </header>

      <main className="stage">
        <div className="stage-head">
          <OnAir mode={air} />
          <div className="slate"><b>Ramesh Kirana</b><span>Aliganj, Lucknow · {today}</span></div>
        </div>

        <div className="stage-body">
          <Caption key={caption.n} text={caption.text} tone={caption.tone} run step={talking ? 300 : 70} />
        </div>

        <div style={{ display: 'grid', gap: 12 }}>
          {qr && pay && (
            <div className="qr">
              <img src={qr} alt={`${pay.name ?? 'Grahak'} ke liye bhugtaan QR`} />
              <div>
                <strong>{pay.name ? `${pay.name}, scan karke chukayein` : 'Scan karke chukayein'}</strong>
                {pay.amount !== undefined && <span className="qr-amt num"><Rupees value={rupees(pay.amount)} /></span>}
                <a href={pay.url} target="_blank" rel="noreferrer">Link {ref}</a>
              </div>
            </div>
          )}
          <div className="dock">
            <div className="heard" aria-live="polite">{heard && (heard.startsWith('Sunaai') ? heard : <>Aapne kaha: <q>{heard}</q></>)}</div>
            <div className="answers">
              <button className="btn btn-ask" disabled={!pending} onClick={() => answer({ kind: 'yes' })}><CheckIcon />Haan</button>
              <button className="btn btn-ghost" disabled={!pending} onClick={() => answer({ kind: 'no' })}>Nahi</button>
            </div>
            <div className="tools-row">
              <div className="tool">
                <button className="round" data-on={listening} disabled={!canListen() || busy} onClick={onMic} aria-label="Bolkar poochhiye">
                  <MicIcon />
                </button>
                <span>{canListen() ? 'Boliye' : 'Mic nahi'}</span>
              </div>
              <div className="tool">
                <label className="round" aria-disabled={busy}>
                  <CameraIcon />
                  <input className="sr-only" type="file" accept="image/*" capture="environment" disabled={busy}
                    aria-label="Shelf ki photo bhejiye" onChange={e => { void onPhoto(e.target.files?.[0]); e.target.value = ''; }} />
                </label>
                <span>Shelf photo</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      {ledger && <Ticker items={ledger.items} total={ledger.total} />}

      {band && (
        <div className="band" key={band.n} role="status"><CoinIcon />{band.text}</div>
      )}
    </div>
  );
}
