//! Where the Audify Sessions server is: the official one, or one the listener runs, whose address
//! is read leniently and checked before it is trusted.

use std::time::Duration;

use anyhow::{Context as _, Result, bail};
use reqwest::Url;

/// Points the app at a server explicitly supplied for development or testing.
pub(crate) const VARIABLE: &str = "AUDIFY_SESSION_SERVER";
pub(crate) const OFFICIAL: &str = "https://audify-listen-together.sachit1751.workers.dev";
/// How long a health check waits for an answer.
const PATIENCE: Duration = Duration::from_secs(6);

/// The WebSocket address `typed` stands for. A bare host or an http address is taken to mean the
/// server's WebSocket on the same host, and an address without a path gets the server's `/ws`.
pub(crate) fn socket(typed: &str) -> Option<Url> {
    let typed = typed.trim();
    if typed.is_empty() {
        return None;
    }
    let written = match typed.split_once("://") {
        Some(("ws" | "wss", _)) => typed.to_owned(),
        Some(("http", rest)) => format!("ws://{rest}"),
        Some(("https", rest)) => format!("wss://{rest}"),
        Some(_) => return None,
        None => format!("wss://{typed}"),
    };
    let mut url = Url::parse(&written).ok()?;
    url.host_str()?;
    if url.path().is_empty() || url.path() == "/" {
        url.set_path("/ws");
    }
    Some(url)
}

/// HTTP origin used by the authenticated REST and WebSocket party API.
pub(crate) fn http_base(typed: &str) -> Option<String> {
    let typed = typed.trim();
    if typed.is_empty() {
        return None;
    }
    let written = match typed.split_once("://") {
        Some(("http" | "https", _)) => typed.to_owned(),
        Some(("ws", rest)) => format!("http://{rest}"),
        Some(("wss", rest)) => format!("https://{rest}"),
        Some(_) => return None,
        None => format!("https://{typed}"),
    };
    let mut url = Url::parse(&written).ok()?;
    url.host_str()?;
    if url.path() == "/ws" || url.path() == "/ws/" || url.path() == "/" {
        url.set_path("");
    }
    url.set_query(None);
    url.set_fragment(None);
    Some(url.as_str().trim_end_matches('/').to_owned())
}

/// Asks the server behind `socket` whether it is up, preferring Cloudflare's `/healthz`.
pub(crate) async fn check(http: reqwest::Client, socket: Url) -> Result<()> {
    let mut health = socket;
    let scheme = match health.scheme() {
        "ws" => "http",
        _ => "https",
    };
    health
        .set_scheme(scheme)
        .ok()
        .context("cannot make a health address")?;
    health.set_path("/healthz");
    health.set_query(None);
    let mut answer = http
        .get(health)
        .timeout(PATIENCE)
        .send()
        .await
        .context("cannot reach the server")?;
    if answer.status() == reqwest::StatusCode::NOT_FOUND {
        let mut legacy = answer.url().clone();
        legacy.set_path("/health");
        answer = http
            .get(legacy)
            .timeout(PATIENCE)
            .send()
            .await
            .context("cannot reach the server health endpoint")?;
    }
    if !answer.status().is_success() {
        bail!("the server answered {}", answer.status());
    }
    let body = answer.text().await.context("cannot read the answer")?;
    let cloudflare = serde_json::from_str::<serde_json::Value>(&body)
        .ok()
        .and_then(|value| value.get("ok").and_then(serde_json::Value::as_bool))
        == Some(true);
    if body.trim() != "ok" && !cloudflare {
        bail!("the server does not answer as a Audify Sessions server");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::http_base;

    #[test]
    fn official_and_custom_websocket_addresses_resolve_to_the_rest_origin() {
        assert_eq!(
            http_base("wss://sessions.example.test/ws"),
            Some("https://sessions.example.test".to_owned())
        );
        assert_eq!(
            http_base("http://127.0.0.1:8787/ws"),
            Some("http://127.0.0.1:8787".to_owned())
        );
        assert_eq!(http_base("ftp://sessions.example.test"), None);
    }
}
