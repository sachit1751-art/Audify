# Anonymous feedback delivery

The Android app submits bug reports and feature suggestions to `POST /api/feedback` on this Worker. The Worker creates a public GitHub issue in `sachit1751-art/Audify`; no Audify account, GitHub login, or email app is required.

## One-time setup

1. Create a GitHub fine-grained personal access token restricted to `sachit1751-art/Audify`.
2. Grant **Issues: Read and write** only.
3. Store it as a Cloudflare Worker secret (never in the app or repository):

   ```powershell
   npx wrangler secret put GITHUB_ISSUES_TOKEN
   ```

4. Deploy the Worker with `npx wrangler deploy`. The endpoint returns an explicit `503` until the secret is configured; it never silently discards a valid report.

## Privacy and abuse controls

- No app login, GitHub login, email address, account ID, or user ID is requested or forwarded.
- App version and platform are attached. Device model, Android version and audio-route details are sent only when the user enables optional diagnostics.
- Raw logs, music history, files and credentials are not collected.
- The Worker hashes the source IP before passing it to the Durable Object rate limiter. The limiter allows at most three submissions per hour per source; the raw IP is not included in the GitHub issue.
- A hidden honeypot field rejects automated submissions without creating issues. Input lengths and categories are validated server-side.
- Submitted report text and optional diagnostics become public GitHub content. The app warns users not to include personal or account information.

Screenshots and file attachments are intentionally not supported. Reports are text-only so attachment permissions, upload failures and image privacy cannot block issue submission.

Cloudflare still processes request metadata (including IP address) to deliver and protect the endpoint. This feature does not claim network-level anonymity.
