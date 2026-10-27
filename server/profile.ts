import type { DB } from './db.ts';

export type Profile = { shopName: string; locality: string; category: string; ownerName: string; addressAs: string; phone: string | null };
export class ProfileError extends Error {}

// The seeded hero shop until the merchant confirms their own details in onboarding.
const DEFAULT_OWNER = 'Ramesh Gupta';
const DEFAULT_ADDRESS = 'Ramesh ji';
const MAX = 60;

export function getProfile(db: DB, merchantId: string): Profile {
  const p = db.prepare('SELECT * FROM profile WHERE merchant_id=?').get(merchantId) as any;
  if (p) return { shopName: p.shop_name, locality: p.locality, category: p.category, ownerName: p.owner_name, addressAs: p.address_as, phone: p.phone ?? null };
  const m = db.prepare('SELECT name, locality, category FROM merchants WHERE id=?').get(merchantId) as any;
  return { shopName: m.name, locality: m.locality ?? '', category: m.category, ownerName: DEFAULT_OWNER, addressAs: DEFAULT_ADDRESS, phone: null };
}

// Ten digits, with or without +91, spaces or dashes; returned as +91XXXXXXXXXX.
export function normalisePhone(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const d = raw.replace(/[\s-]/g, '').replace(/^\+?91(?=\d{10}$)/, '');
  return /^\d{10}$/.test(d) ? '+91' + d : null;
}

export function saveProfile(db: DB, merchantId: string, input: any, at: Date): Profile {
  const s = (k: string) => (typeof input?.[k] === 'string' ? input[k].trim() : '');
  const phone = input?.phone == null || input.phone === '' ? null : normalisePhone(input.phone);
  const p: Profile = { shopName: s('shopName'), locality: s('locality'), category: s('category'), ownerName: s('ownerName'), addressAs: s('addressAs'), phone };
  const tooLong = [p.shopName, p.locality, p.category, p.ownerName, p.addressAs].some(v => v.length > MAX);
  if (!p.shopName || !p.addressAs || tooLong || (input?.phone && !phone)) throw new ProfileError('bad_profile');
  db.prepare(`INSERT INTO profile (merchant_id,shop_name,locality,category,owner_name,address_as,phone,updated_at) VALUES (?,?,?,?,?,?,?,?)
    ON CONFLICT(merchant_id) DO UPDATE SET shop_name=excluded.shop_name, locality=excluded.locality, category=excluded.category,
      owner_name=excluded.owner_name, address_as=excluded.address_as, phone=excluded.phone, updated_at=excluded.updated_at`)
    .run(merchantId, p.shopName, p.locality, p.category, p.ownerName, p.addressAs, p.phone, at.toISOString());
  return p;
}
