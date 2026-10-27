import { DatabaseSync } from 'node:sqlite';

export type DB = DatabaseSync;

const SCHEMA = `
CREATE TABLE IF NOT EXISTS merchants (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, locality TEXT,
  lat REAL NOT NULL, lng REAL NOT NULL, language TEXT NOT NULL DEFAULT 'hi'
);
-- What the merchant confirmed in onboarding. Not in the seed's TABLES: a demo reset keeps it.
CREATE TABLE IF NOT EXISTS profile (
  merchant_id TEXT PRIMARY KEY, shop_name TEXT NOT NULL, locality TEXT NOT NULL, category TEXT NOT NULL,
  owner_name TEXT NOT NULL, address_as TEXT NOT NULL, phone TEXT, updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS payments (
  id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, amount INTEGER NOT NULL, ts TEXT NOT NULL,
  payer_hash TEXT NOT NULL, counterparty TEXT NOT NULL CHECK (counterparty IN ('customer','distributor'))
);
CREATE INDEX IF NOT EXISTS payments_merchant_ts ON payments(merchant_id, ts);
CREATE TABLE IF NOT EXISTS settlements (merchant_id TEXT NOT NULL, date TEXT NOT NULL, amount INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS khata (
  id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, customer_name TEXT NOT NULL, phone TEXT NOT NULL,
  amount INTEGER NOT NULL, given_on TEXT NOT NULL, status TEXT NOT NULL CHECK (status IN ('open','paid'))
);
CREATE TABLE IF NOT EXISTS opportunities (
  id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, type TEXT NOT NULL, key TEXT NOT NULL,
  payload TEXT NOT NULL, est_value INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('new','proposed','accepted','declined','done')),
  created_at TEXT NOT NULL, UNIQUE (merchant_id, type, key)
);
CREATE TABLE IF NOT EXISTS actions (
  id INTEGER PRIMARY KEY, opportunity_id INTEGER NOT NULL, tool TEXT NOT NULL, args TEXT NOT NULL,
  result TEXT, ts TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS pay_links (
  ref TEXT PRIMARY KEY, merchant_id TEXT NOT NULL, khata_id INTEGER NOT NULL, action_id INTEGER NOT NULL,
  amount INTEGER NOT NULL, status TEXT NOT NULL CHECK (status IN ('pending','paid')), paid_at TEXT, expires_at TEXT, expired_at TEXT
);
CREATE TABLE IF NOT EXISTS ledger (
  id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, action_id INTEGER, kind TEXT NOT NULL,
  amount INTEGER NOT NULL, label TEXT NOT NULL, ts TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS inventory (
  merchant_id TEXT NOT NULL, sku TEXT NOT NULL, name TEXT NOT NULL, unit_price INTEGER NOT NULL,
  daily_units INTEGER NOT NULL, est_qty INTEGER NOT NULL, reorder_point INTEGER NOT NULL, updated_at TEXT NOT NULL,
  PRIMARY KEY (merchant_id, sku)
);
CREATE TABLE IF NOT EXISTS distributors (id TEXT PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS distributor_skus (distributor_id TEXT NOT NULL, sku TEXT NOT NULL, unit_price INTEGER NOT NULL, tiers TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS group_orders (
  id INTEGER PRIMARY KEY, distributor_id TEXT NOT NULL, sku TEXT NOT NULL, close_at TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('open','closed'))
);
CREATE TABLE IF NOT EXISTS group_members (group_id INTEGER NOT NULL, merchant_id TEXT NOT NULL, qty INTEGER NOT NULL, UNIQUE (group_id, merchant_id));
CREATE TABLE IF NOT EXISTS orders (
  id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, distributor_id TEXT NOT NULL, sku TEXT NOT NULL, qty INTEGER NOT NULL,
  total INTEGER NOT NULL, discount_pct INTEGER NOT NULL DEFAULT 0, group_id INTEGER, due_on TEXT,
  status TEXT NOT NULL CHECK (status IN ('placed','due','paid')), ts TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS loyalty_rules (merchant_id TEXT PRIMARY KEY, visits_required INTEGER NOT NULL, reward_amount INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS winback_campaigns (id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, action_id INTEGER, offer INTEGER NOT NULL, count INTEGER NOT NULL, ts TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS winback_targets (campaign_id INTEGER NOT NULL, payer_hash TEXT NOT NULL, returned_at TEXT);
CREATE TABLE IF NOT EXISTS loans (
  id INTEGER PRIMARY KEY, merchant_id TEXT NOT NULL, action_id INTEGER, principal INTEGER NOT NULL, total_cost INTEGER NOT NULL,
  daily_deduction INTEGER NOT NULL, tenure_days INTEGER NOT NULL, status TEXT NOT NULL, ts TEXT NOT NULL
);
`;

export const TABLES = [
  'merchants', 'payments', 'settlements', 'khata', 'opportunities', 'actions', 'pay_links', 'ledger',
  'inventory', 'distributors', 'distributor_skus', 'group_orders', 'group_members', 'orders',
  'loyalty_rules', 'winback_campaigns', 'winback_targets', 'loans',
];

// All-or-nothing for a group of synchronous writes.
export function tx<T>(db: DB, fn: () => T): T {
  db.exec('BEGIN');
  try {
    const out = fn();
    db.exec('COMMIT');
    return out;
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
}

export function openDb(path: string): DB {
  const db = new DatabaseSync(path);
  db.exec(SCHEMA);
  // Files made before pay links could expire: add the columns (CREATE IF NOT EXISTS keeps old tables as they were).
  const cols = (db.prepare('PRAGMA table_info(pay_links)').all() as any[]).map(c => c.name);
  for (const c of ['expires_at', 'expired_at']) if (!cols.includes(c)) db.exec(`ALTER TABLE pay_links ADD COLUMN ${c} TEXT`);
  return db;
}
