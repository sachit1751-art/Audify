import test from 'node:test';
import assert from 'node:assert/strict';
import { bytesToBase64, readBoundedBody, uploadFeedbackAttachment } from './feedback.js';

test('bounded image reader accepts payloads within the configured limit', async () => {
  const bytes = new Uint8Array([1, 2, 3, 4]);
  const result = await readBoundedBody(new Request('https://example.test/upload', { method: 'POST', body: bytes }), 4);
  assert.deepEqual([...result], [...bytes]);
});

test('bounded image reader rejects declared and streamed bodies above the limit', async () => {
  const declared = new Request('https://example.test/upload', {
    method: 'POST', headers: { 'content-length': '5' }, body: new Uint8Array([1, 2, 3, 4]),
  });
  assert.equal(await readBoundedBody(declared, 4), null);

  const streamed = new Request('https://example.test/upload', {
    method: 'POST', body: new Uint8Array([1, 2, 3, 4, 5]),
  });
  assert.equal(await readBoundedBody(streamed, 4), null);
});

test('binary image data is encoded as valid base64 without changing bytes', () => {
  const bytes = Uint8Array.from({ length: 100_000 }, (_, index) => index % 256);
  assert.deepEqual([...Buffer.from(bytesToBase64(bytes), 'base64')], [...bytes]);
});

test('screenshot upload uses the server token and stores image in the Audify repository', async () => {
  const bytes = new Uint8Array([0, 1, 2, 253, 254, 255]);
  let observed;
  const result = await uploadFeedbackAttachment(bytes, 'image/png', 'server-only-token', async (url, options) => {
    observed = { url, options };
    return new Response(JSON.stringify({ content: { path: decodeURIComponent(new URL(url).pathname.split('/contents/')[1]) } }), { status: 201 });
  });
  assert.equal(observed.options.method, 'PUT');
  assert.equal(observed.options.headers.authorization, 'Bearer server-only-token');
  assert.match(observed.url, /^https:\/\/api\.github\.com\/repos\/sachit1751-art\/Audify\/contents\/feedback-attachments\/[a-f0-9-]+\.png$/);
  const body = JSON.parse(observed.options.body);
  assert.deepEqual([...Buffer.from(body.content, 'base64')], [...bytes]);
  assert.match(result.url, /^https:\/\/raw\.githubusercontent\.com\/sachit1751-art\/Audify\/main\/feedback-attachments\/[a-f0-9-]+\.png$/);
});

test('screenshot upload reports GitHub permission or API rejection without exposing response details', async () => {
  const result = await uploadFeedbackAttachment(new Uint8Array([1]), 'image/jpeg', 'secret', async () => new Response('forbidden', { status: 403 }));
  assert.deepEqual(result, { ok: false });
});
