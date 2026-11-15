// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

// The development extension's id, pinned by its manifest `key`
// (web/extension/dev-key/). Read at build time so the page can message it.
function devExtensionId(): string {
  try {
    return readFileSync(new URL('../extension/dev-key/extension-id.txt', import.meta.url), 'utf8').trim();
  } catch {
    return '';
  }
}

export default defineConfig(({ command, mode }) => {
  // The deployed Listen Together endpoint is public configuration, not a secret.
  // Keep an environment override for staging/self-hosted deployments, but make
  // clean production builds reproducible without an untracked local .env file.
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), 'VITE_');
  const partyServerUrl = env.VITE_PARTY_SERVER_URL?.trim()
    || 'https://audify-listen-together.sachit1751.workers.dev';
  return {
    plugins: [react(), tailwindcss()],
    define: {
      DEV_EXTENSION_ID: JSON.stringify(devExtensionId()),
      'import.meta.env.VITE_PARTY_SERVER_URL': JSON.stringify(partyServerUrl),
    },
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    // AutoMix's analysis worker (playback/automix): essentia.js's 2021-era
    // Emscripten glue breaks under dep pre-bundling, and the worker is a module.
    optimizeDeps: { exclude: ['essentia.js'] },
    worker: { format: 'es' },
    server: {
      // The extension's externally_connectable and DNR rules name this origin
      // exactly, so the port must not drift to 5174 when 5173 is busy.
      port: 5173,
      strictPort: true,
    },
  };
});
