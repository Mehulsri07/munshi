// cloudflared prints the Quick Tunnel address once, inside a box in its log. Its own API host
// (api.trycloudflare.com) shows up in error lines and is not ours.
export const tunnelUrl = (text: string): string | null => text.match(/https:\/\/(?!api\.)[a-z0-9-]+\.trycloudflare\.com/)?.[0] ?? null;
