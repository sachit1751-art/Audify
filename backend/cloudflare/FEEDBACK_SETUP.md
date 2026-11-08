# Anonymous feedback delivery

The Android app posts bug reports and feature suggestions to `POST /api/feedback` on this Worker. The Worker creates a public GitHub issue in `sachit1751-art/Audify`; users do not need an Audify account, a GitHub account, or an email app.

## One-time setup

1. Create a GitHub fine-grained personal access token limited to `sachit1751-art/Audify`.
2. Grant **Issues: Read and write**. Do not grant Contents, Actions, Administration, or other permissions.
3. Store it as a Cloudflare Worker secret (never in the app or repository):

   ```powershell
   npx wrangler secret put GITHUB_ISSUES_TOKEN
   ```

4. Deploy the Worker. The endpoint intentionally returns `503` until the secret exists; it never falls back to email or silently discards a real submission.

## Privacy and abuse controls

- No app login, GitHub login, email address, account ID, or user ID is requested or forwarded.
- Audify version and platform are attached. Device model and Android version are sent only after the user enables the optional diagnostics checkbox.
- Raw logs, music history, files, and credentials are never collected by this form.
- The Worker hashes the source IP before passing it to the Durable Object rate limiter. The limiter allows at most three submissions per hour per source; the raw IP is not included in the GitHub issue.
- A hidden honeypot field rejects automated submissions without creating issues. Input lengths and categories are validated server-side.
- Submitted text becomes a **public GitHub issue**. The app warns users not to submit personal or account information.

Cloudflare still processes request metadata (including IP address) to deliver and protect the endpoint. This feature does not claim network-level anonymity.
