/**
 * MessageAI – Cloud Functions.
 *
 * Contains Firestore-triggered functions that send FCM push notifications for
 * new messages (direct and group) and updates presence on account deletion.
 */
import * as admin from 'firebase-admin';
// import { user, UserRecord } from 'firebase-functions/v1/auth';
import { defineSecret, defineSecret as defineSecretCF, defineString } from 'firebase-functions/params';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { onRequest } from 'firebase-functions/v2/https';
import crypto from 'node:crypto';

admin.initializeApp();

/** Send push notification on new direct message. */
export const sendPushNotification = onDocumentCreated(
  'chats/{chatId}/messages/{messageId}',
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data() as any;
    const chatId = event.params.chatId as string;

    console.log(`New message in chat ${chatId}, sending push notification`);

    // Get chat document to find recipients
    const chatDoc = await admin.firestore().collection('chats').doc(chatId).get();
    if (!chatDoc.exists) {
      console.log('Chat document not found');
      return;
    }

    const chatData = chatDoc.data() as any;
    const participants: string[] = chatData?.participants || [];
    const senderId: string = message.senderId;

    // Get recipient FCM tokens (exclude sender)
    const recipients = participants.filter((id) => id !== senderId);
    if (recipients.length === 0) {
      console.log('No recipients found');
      return;
    }

    // Get sender info
    const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
    const senderName = senderDoc.data()?.displayName || 'Someone';

    // Get FCM tokens
    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore().collection('users').doc(recipientId).get();
      const fcmToken = userDoc.data()?.fcmToken as string | undefined;
      if (fcmToken) {
        console.log(`Found FCM token for recipient ${recipientId}`);
        tokens.push(fcmToken);
      } else {
        console.log(`No FCM token for recipient ${recipientId}`);
      }
    }
    if (tokens.length === 0) {
      console.log('No valid FCM tokens found');
      return;
    }

    // Send notification using v1 API
    const messagePayload = {
      notification: {
        title: senderName,
        body: message.text || '📷 Image',
      },
      data: {
        chatId,
        messageId: event.params.messageId as string,
        type: 'message',
      },
      tokens,
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(messagePayload);
      console.log(`Successfully sent ${response.successCount} notifications, ${response.failureCount} failed`);
      
      // Log any failures
      if (response.failureCount > 0) {
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            console.error(`Failed to send to token ${tokens[idx]}: ${resp.error?.message}`);
          }
        });
      }
    } catch (error) {
      console.error('Error sending push notification:', error);
    }
  }
);

// --- Embedding on write -------------------------------------------------------
const OPENAI_API_KEY_EMBED = defineSecretCF('OPENAI_API_KEY');

async function embedText(text: string): Promise<number[]> {
  const fetchFn: any = (globalThis as any).fetch;
  const res = await fetchFn('https://api.openai.com/v1/embeddings', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${OPENAI_API_KEY_EMBED.value()}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: 'text-embedding-3-small',
      input: text,
    }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(JSON.stringify(data));
  const vec: number[] = data?.data?.[0]?.embedding || [];
  return vec;
}

function chunkText(text: string, maxLen = 700): string[] {
  const chunks: string[] = [];
  let i = 0;
  while (i < text.length) {
    chunks.push(text.slice(i, i + maxLen));
    i += maxLen;
  }
  return chunks;
}

export const embedOnMessageWrite = onDocumentCreated({ document: 'chats/{chatId}/messages/{messageId}', secrets: [OPENAI_API_KEY_EMBED] }, async (event) => {
  const snap = event.data;
  if (!snap) return;
  const msg = snap.data() as any;
  const chatId = event.params.chatId as string;
  const messageId = event.params.messageId as string;
  const text: string = (msg?.text || '').toString().trim();
  if (!text) { return; }

  const db = admin.firestore();
  const chunks = chunkText(text, 700);
  const batch = db.batch();
  let seq = 0;
  for (const ch of chunks) {
    const vec = await embedText(ch);
    const ref = db.collection('chats').doc(chatId).collection('messages').doc(messageId).collection('chunks').doc(String(seq));
    batch.set(ref, { seq, text: ch, len: ch.length, embed: vec }, { merge: true });
    seq += 1;
  }
  await batch.commit();
});

/** Send push notification on new group message. */
export const sendGroupPushNotification = onDocumentCreated(
  'groups/{groupId}/messages/{messageId}',
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data() as any;
    const groupId = event.params.groupId as string;

    console.log(`New message in group ${groupId}, sending push notification`);

    const groupDoc = await admin.firestore().collection('groups').doc(groupId).get();
    if (!groupDoc.exists) {
      console.log('Group document not found');
      return;
    }

    const groupData = groupDoc.data() as any;
    const members: string[] = groupData?.members || [];
    const senderId: string = message.senderId;

    const recipients = members.filter((id) => id !== senderId);
    if (recipients.length === 0) {
      console.log('No recipients found');
      return;
    }

    const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
    const senderName = senderDoc.data()?.displayName || 'Someone';
    const groupName = groupData?.name || 'Group';

    const tokens: string[] = [];
    for (const recipientId of recipients) {
      const userDoc = await admin.firestore().collection('users').doc(recipientId).get();
      const fcmToken = userDoc.data()?.fcmToken as string | undefined;
      if (fcmToken) {
        console.log(`Found FCM token for group member ${recipientId}`);
        tokens.push(fcmToken);
      }
    }
    if (tokens.length === 0) {
      console.log('No valid FCM tokens found');
      return;
    }

    const messagePayload = {
      notification: {
        title: `${senderName} in ${groupName}`,
        body: message.text || '📷 Image',
      },
      data: {
        groupId,
        messageId: event.params.messageId as string,
        type: 'group_message',
      },
      tokens,
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(messagePayload);
      console.log(`Successfully sent ${response.successCount} notifications, ${response.failureCount} failed`);
      
      if (response.failureCount > 0) {
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            console.error(`Failed to send to token ${tokens[idx]}: ${resp.error?.message}`);
          }
        });
      }
    } catch (error) {
      console.error('Error sending group push notification:', error);
    }
  }
);

// --- Simple per-instance circuit breaker ------------------------------------
enum CircuitState {
  CLOSED = 'CLOSED',
  OPEN = 'OPEN',
  HALF_OPEN = 'HALF_OPEN',
}

class CircuitBreaker {
  private state: CircuitState = CircuitState.CLOSED;
  private failureCount = 0;
  private successCount = 0;
  private lastFailureTime = 0;

  constructor(
    private readonly failureThreshold: number = 5,
    private readonly recoveryTimeoutMs: number = 60000,
    private readonly successThreshold: number = 2,
  ) {}

  async execute<T>(fn: () => Promise<T>): Promise<T> {
    // transition to HALF_OPEN after cooldown
    if (this.state === CircuitState.OPEN && (Date.now() - this.lastFailureTime) > this.recoveryTimeoutMs) {
      this.state = CircuitState.HALF_OPEN;
      this.successCount = 0;
      console.log(JSON.stringify({ level: 'info', event: 'circuit_half_open' }));
    }

    if (this.state === CircuitState.OPEN) {
      throw new Error('CircuitOpen');
    }

    try {
      const out = await fn();
      this.onSuccess();
      return out;
    } catch (e) {
      this.onFailure();
      throw e;
    }
  }

  private onSuccess() {
    this.failureCount = 0;
    if (this.state === CircuitState.HALF_OPEN) {
      this.successCount += 1;
      if (this.successCount >= this.successThreshold) {
        this.state = CircuitState.CLOSED;
        console.log(JSON.stringify({ level: 'info', event: 'circuit_closed' }));
      }
    }
  }

  private onFailure() {
    this.lastFailureTime = Date.now();
    if (this.state === CircuitState.CLOSED) {
      this.failureCount += 1;
      if (this.failureCount >= this.failureThreshold) {
        this.state = CircuitState.OPEN;
        console.log(JSON.stringify({ level: 'error', event: 'circuit_open' }));
      }
    } else if (this.state === CircuitState.HALF_OPEN) {
      this.state = CircuitState.OPEN;
      this.successCount = 0;
      console.log(JSON.stringify({ level: 'error', event: 'circuit_reopen' }));
    }
  }
}

const langChainCircuit = new CircuitBreaker(5, 60000, 2);

// --- HTTPS proxies -----------------------------------------------------------
const OPENAI_API_KEY = defineSecret('OPENAI_API_KEY');

export const openaiProxy = onRequest({ cors: true, secrets: [OPENAI_API_KEY] }, async (req, res) => {
  try {
    // Basic CORS/preflight support
    if (req.method === 'OPTIONS') {
      res.set('Access-Control-Allow-Origin', req.headers.origin || '*');
      res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
      res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
      res.status(204).send('');
      return;
    }

    if (req.method !== 'POST') {
      res.status(405).send('Method Not Allowed');
      return;
    }

    res.set('Access-Control-Allow-Origin', req.headers.origin || '*');

    const body = typeof req.body === 'string' ? JSON.parse(req.body) : (req.body || {});

    // Use global fetch without TS type dependency
    const fetchFn: any = (globalThis as any).fetch;

    const response = await fetchFn('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${OPENAI_API_KEY.value()}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: body.model ?? 'gpt-4o-mini',
        messages: body.messages ?? [{ role: 'user', content: body.prompt ?? '' }],
        temperature: body.temperature ?? 0.7,
        max_tokens: body.max_tokens ?? 512,
      }),
    });

    const data = await response.json();
    if (!response.ok) {
      res.status(response.status).json({ error: data });
      return;
    }
    res.status(200).json(data);
  } catch (e: any) {
    res.status(500).json({ error: e?.message ?? 'Unknown error' });
  }
});

// aiRouter (simple): forwards client envelopes to LangChain service without exposing keys
export const aiRouterSimple = onRequest({ cors: true }, async (req, res) => {
  try {
    if (req.method === 'OPTIONS') {
      res.set('Access-Control-Allow-Origin', req.headers.origin || '*');
      res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
      res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
      res.status(204).send('');
      return;
    }
    if (req.method !== 'POST') {
      res.status(405).send('Method Not Allowed');
      return;
    }
    res.set('Access-Control-Allow-Origin', req.headers.origin || '*');

    const targetPath = (req.query.path as string) || '';
    if (!targetPath) {
      res.status(400).json({ error: 'Missing path query param' });
      return;
    }
    const baseUrl = (process.env.LANGCHAIN_BASE_URL as string | undefined) || 'http://127.0.0.1:8000';

    const body = typeof req.body === 'string' ? JSON.parse(req.body) : (req.body || {});
    const fetchFn: any = (globalThis as any).fetch;

    const upstream = await fetchFn(`${baseUrl.replace(/\/$/, '')}/${targetPath}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Forward Firebase ID token if present; service may ignore or log
        'Authorization': req.headers.authorization || '',
        'x-request-id': body?.requestId || '',
      },
      body: JSON.stringify(body),
    });
    const data = await upstream.json();
    res.status(upstream.status).json(data);
  } catch (e: any) {
    res.status(500).json({ error: e?.message ?? 'Unknown error' });
  }
});

// --- AI Router to LangChain service ------------------------------------------
const LANGCHAIN_SHARED_SECRET = defineSecret('LANGCHAIN_SHARED_SECRET');
const ALLOWED_ORIGINS = defineString('ALLOWED_ORIGINS'); // comma‑separated

type Envelope = {
  requestId: string;
  context: Record<string, unknown>;
  payload: Record<string, unknown>;
};

// naive in-memory token bucket (cold start resets) per uid (kept as fast-path)
const buckets: Record<string, { tokens: number; lastRefillMs: number }> = {};
const RATE_PER_MIN = 10;
const BURST = 20;

function refill(uid: string, now: number) {
  const b = buckets[uid] ?? { tokens: BURST, lastRefillMs: now };
  const elapsedMin = (now - b.lastRefillMs) / 60000;
  const newTokens = Math.floor(elapsedMin * RATE_PER_MIN);
  if (newTokens > 0) {
    b.tokens = Math.min(BURST, b.tokens + newTokens);
    b.lastRefillMs = now;
  }
  buckets[uid] = b;
}

function take(uid: string): boolean {
  const now = Date.now();
  refill(uid, now);
  const b = buckets[uid]!;
  if (b.tokens > 0) {
    b.tokens -= 1;
    return true;
  }
  return false;
}

function cors(req: any, res: any): boolean {
  const allowList = (ALLOWED_ORIGINS.value() || '').split(',').map((s) => s.trim()).filter(Boolean);
  const origin = req.headers.origin || '';
  const allow = allowList.length === 0 || allowList.includes('*') || allowList.includes(origin);
  if (allow) {
    res.set('Access-Control-Allow-Origin', origin || '*');
    res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  }
  if (req.method === 'OPTIONS') {
    res.status(204).send('');
    return true;
  }
  return false;
}

async function verifyIdToken(req: any): Promise<string> {
  const authz = req.headers['authorization'] || req.headers['Authorization'];
  if (!authz || typeof authz !== 'string' || !authz.startsWith('Bearer ')) {
    throw new Error('unauthorized');
  }
  const token = authz.substring('Bearer '.length);
  const decoded = await admin.auth().verifyIdToken(token);
  return decoded.uid;
}

function buildEnvelope(uid: string, body: any): Envelope {
  const requestId = (body && body.requestId) || crypto.randomUUID();
  const payload = (body && body.payload && typeof body.payload === 'object') ? body.payload : (body || {});
  const context = { ...(body?.context || {}), uid } as Record<string, unknown>;
  return { requestId, context, payload };
}

function sign(envelope: Envelope): { sig: string; ts: string } {
  const ts = Date.now().toString();
  // IMPORTANT: Hash the EXACT body we send downstream, not just payload
  const bodyStr = JSON.stringify(envelope);
  const bodyHash = crypto.createHash('sha256').update(bodyStr).digest('hex');
  const base = `${envelope.requestId}.${ts}.${bodyHash}`;
  const secret = LANGCHAIN_SHARED_SECRET.value();
  const sig = crypto.createHmac('sha256', secret).update(base).digest('hex');
  return { sig, ts };
}

// Firestore-backed rate limit (persistent across cold starts)
async function checkRateLimitPersistent(uid: string): Promise<boolean> {
  const db = admin.firestore();
  const ref = db.collection('rateLimits').doc(uid);
  return await db.runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    const now = Date.now();
    let tokens = BURST;
    let lastRefillMs = now;
    if (doc.exists) {
      const data = doc.data() as any;
      tokens = typeof data.tokens === 'number' ? data.tokens : BURST;
      lastRefillMs = typeof data.lastRefillMs === 'number' ? data.lastRefillMs : now;
      const elapsedMin = (now - lastRefillMs) / 60000;
      const newTokens = Math.floor(elapsedMin * RATE_PER_MIN);
      if (newTokens > 0) {
        tokens = Math.min(BURST, tokens + newTokens);
        lastRefillMs = now;
      }
    }
    if (tokens > 0) {
      tokens -= 1;
      tx.set(ref, { tokens, lastRefillMs, uid }, { merge: true });
      return true;
    }
    return false;
  });
}

async function forwardToLangChain(path: string, envelope: Envelope, timeoutMs: number): Promise<Response> {
  const { sig, ts } = sign(envelope);
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const baseUrl = (process.env.LANGCHAIN_BASE_URL as string | undefined) || 'http://127.0.0.1:8000';
    const fetchFn: any = (globalThis as any).fetch;
    // Debug log (safe prefixes only)
    try {
      console.log(JSON.stringify({
        level: 'info',
        event: 'forward_headers_debug',
        requestId: envelope.requestId,
        path,
        langchainBaseUrl: baseUrl,
        sigPrefix: (sig || '').slice(0, 12),
        ts,
        bodyLen: Buffer.byteLength(JSON.stringify(envelope), 'utf8')
      }));
    } catch {}
    const bodyStr = JSON.stringify(envelope);
    const res = await fetchFn(`${baseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-request-id': envelope.requestId,
        'x-uid': String(envelope.context['uid'] ?? ''),
        'x-sig': sig,
        'x-sig-ts': ts,
      },
      body: bodyStr,
      signal: controller.signal,
    });
    return res as Response;
  } finally {
    clearTimeout(t);
  }
}

function routeToPath(reqPath: string): { path: string; timeoutMs: number } | null {
  const p = reqPath.replace(/^\//, '');
  // Bump timeouts to allow LLM fills to complete under emulator/DEV
  const fast = 20000; // 20s
  const slow = 60000; // 60s
  switch (p) {
    case 'v1/template/generate':
      return { path: 'template/generate', timeoutMs: fast };
    case 'v1/template/warnord':
      return { path: 'template/warnord', timeoutMs: slow };
    case 'v1/template/opord':
      return { path: 'template/opord', timeoutMs: slow };
    case 'v1/template/frago':
      return { path: 'template/frago', timeoutMs: slow };
    case 'v1/template/medevac':
      return { path: 'template/medevac', timeoutMs: slow };
    case 'v1/assistant/route':
      return { path: 'assistant/route', timeoutMs: fast };
    case 'v1/assistant/gate':
      return { path: 'assistant/gate', timeoutMs: fast };
    case 'v1/threats/extract':
      return { path: 'threats/extract', timeoutMs: fast };
    case 'v1/sitrep/summarize':
      return { path: 'sitrep/summarize', timeoutMs: slow };
    case 'v1/intent/casevac/detect':
      return { path: 'intent/casevac/detect', timeoutMs: fast };
    case 'v1/workflow/casevac/run':
      return { path: 'workflow/casevac/run', timeoutMs: slow };
    case 'v1/rag/warm':
      return { path: 'rag/warm', timeoutMs: slow };
    case 'v1/missions/plan':
      return { path: 'missions/plan', timeoutMs: slow };
    default:
      return null;
  }
}

export const aiRouter = onRequest({ cors: true, secrets: [LANGCHAIN_SHARED_SECRET] }, async (req, res) => {
  // CORS / preflight
  if (cors(req, res)) return;

  // Method
  if (req.method !== 'POST') { res.status(405).send('Method Not Allowed'); return; }

  // Auth (ID token only; no role checks)
  let uid: string;
  try {
    uid = await verifyIdToken(req);
  } catch {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  // Rate limit: in-memory fast-path (best-effort; resets on cold start)
  try {
    if (!take(uid)) { res.status(429).json({ error: 'Rate limit exceeded' }); return; }
  } catch (err) {
    console.error(JSON.stringify({ level: 'error', event: 'ratelimit_mem_error', error: (err as any)?.message }));
  }

  // Rate limit: persistent check (fallback to allow on Firestore error)
  try {
    const allowed = await checkRateLimitPersistent(uid);
    if (!allowed) { res.status(429).json({ error: 'Rate limit exceeded' }); return; }
  } catch (err) {
    console.error(JSON.stringify({ level: 'error', event: 'ratelimit_error', error: (err as any)?.message }));
  }

  // Route (robust normalization). Extract segment after "/aiRouter/" regardless of leading project/function prefixes.
  const rawPath = (req.path || '');
  const url = (req as any).originalUrl || (req as any).url || rawPath;
  let extracted = '';
  const m = url.match(/\/aiRouter\/(.*)$/);
  if (m && m[1]) {
    extracted = m[1];
  } else if (typeof req.query?.path === 'string') {
    extracted = req.query.path as string; // support aiRouterSimple-style
  } else {
    extracted = url.replace(/^.*\/aiRouter\/?/, '');
  }
  console.log(JSON.stringify({ level: 'info', event: 'route_debug', rawPath, url, extracted }));
  const target = routeToPath(extracted);
  if (!target) { res.status(404).json({ error: 'Unknown endpoint' }); return; }
  console.log(JSON.stringify({ level: 'info', event: 'route_target', target: target.path, timeoutMs: target.timeoutMs }));

  // Body and size cap (~64KB)
  const body = typeof req.body === 'string' ? JSON.parse(req.body) : (req.body || {});
  const approxBytes = Buffer.byteLength(JSON.stringify(body), 'utf8');
  if (approxBytes > 64 * 1024) { res.status(413).json({ error: 'Payload too large' }); return; }

  // Envelope
  const envelope = buildEnvelope(uid, body);

  const started = Date.now();
  try {
    const upstream = await langChainCircuit.execute(() => forwardToLangChain(target.path, envelope, target.timeoutMs));
    const data = await upstream.json();
    res.status(upstream.status).json(data);
    console.log(JSON.stringify({
      level: 'info',
      requestId: envelope.requestId,
      uid,
      endpoint: target.path,
      latencyMs: Date.now() - started,
      status: upstream.status,
    }));
  } catch (e: any) {
    if (e?.message === 'CircuitOpen') {
      res.status(503).json({ error: 'Service unavailable, please retry shortly' });
      return;
    }
    const code = e?.name === 'AbortError' ? 504 : 502;
    res.status(code).json({ error: e?.message || 'Upstream error' });
    console.error(JSON.stringify({
      level: 'error',
      requestId: envelope.requestId,
      uid,
      endpoint: target.path,
      latencyMs: Date.now() - started,
      error: e?.message || String(e),
    }));
  }
});

/** Update user presence on auth user deletion. */
// Temporarily disabled - v1 auth triggers causing deployment issues
// TODO: Migrate to v2 auth triggers or Cloud Scheduler
// export const updatePresenceOnDisconnect = user().onDelete(async (userRecord: UserRecord) => {
//   const uid = userRecord.uid;
//   await admin.firestore().collection('users').doc(uid).update({
//     isOnline: false,
//     lastSeen: admin.firestore.FieldValue.serverTimestamp(),
//   });
// });
