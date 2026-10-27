export type SendWhatsApp = (to: string, text: string, params?: string[]) => Promise<{ sent: boolean; error?: string }>;

// Meta WhatsApp Cloud API. A business-started message needs an approved template (WA_TEMPLATE, body
// parameters in order); without one, free-form text reaches only numbers that messaged us in the last
// 24 hours. Never throws: callers fall back to the QR.
export function makeSendWhatsApp(env: { WA_TOKEN?: string; WA_PHONE_ID?: string; WA_GRAPH_VERSION?: string; WA_TEMPLATE?: string; WA_TEMPLATE_LANG?: string }): SendWhatsApp {
  const { WA_TOKEN, WA_PHONE_ID, WA_GRAPH_VERSION = 'v23.0', WA_TEMPLATE, WA_TEMPLATE_LANG = 'en' } = env;
  return async (to, text, params) => {
    if (!WA_TOKEN || !WA_PHONE_ID) return { sent: false, error: 'not_configured' };
    const message = WA_TEMPLATE && params?.length
      ? { type: 'template', template: { name: WA_TEMPLATE, language: { code: WA_TEMPLATE_LANG }, components: [{ type: 'body', parameters: params.map(t => ({ type: 'text', text: t })) }] } }
      : { type: 'text', text: { body: text, preview_url: true } };
    try {
      const res = await fetch(`https://graph.facebook.com/${WA_GRAPH_VERSION}/${WA_PHONE_ID}/messages`, {
        method: 'POST',
        headers: { authorization: `Bearer ${WA_TOKEN}`, 'content-type': 'application/json' },
        body: JSON.stringify({ messaging_product: 'whatsapp', to: to.replace(/\D/g, ''), ...message }),
        signal: AbortSignal.timeout(5000),
      });
      return res.ok ? { sent: true } : { sent: false, error: `http_${res.status}: ${(await res.text()).slice(0, 200)}` };
    } catch (e) {
      return { sent: false, error: String(e) };
    }
  };
}
