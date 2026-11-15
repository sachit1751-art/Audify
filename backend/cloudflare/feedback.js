export async function readBoundedBody(request, maximum) {
  const declaredLength = Number(request.headers.get('content-length'));
  if (Number.isFinite(declaredLength) && declaredLength > maximum) return null;
  if (!request.body) return new Uint8Array();
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maximum) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}

export function bytesToBase64(bytes) {
  let binary = '';
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length)));
  }
  return btoa(binary);
}

export async function uploadFeedbackAttachment(bytes, mime, token, fetchImpl = fetch) {
  const extension = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' }[mime];
  if (!extension) throw new TypeError('Unsupported feedback image type.');
  const path = `feedback-attachments/${crypto.randomUUID()}.${extension}`;
  const response = await fetchImpl(`https://api.github.com/repos/sachit1751-art/Audify/contents/${path}`, {
    method: 'PUT',
    headers: {
      authorization: `Bearer ${token}`,
      accept: 'application/vnd.github+json',
      'content-type': 'application/json',
      'user-agent': 'Audify-Feedback',
      'x-github-api-version': '2022-11-28',
    },
    body: JSON.stringify({ message: 'Add anonymous Audify feedback screenshot', content: bytesToBase64(bytes) }),
  });
  if (!response.ok) return { ok: false };
  return { ok: true, url: `https://raw.githubusercontent.com/sachit1751-art/Audify/main/${path}` };
}
