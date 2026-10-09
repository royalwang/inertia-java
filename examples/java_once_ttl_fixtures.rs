//! Live TTL semantic evidence; wall-clock timestamps are never checked in as fixed oracles.
use http::{HeaderMap, HeaderName, HeaderValue, Method};
use inertia::{Config, Inertia, Props, Request};
use serde_json::{Value, json};
use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
fn now_millis() -> u128 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis()
}
#[tokio::main]
async fn main() {
    let definitions: Vec<Value> = serde_json::from_str(include_str!("java_once_ttl_cases.json")).unwrap();
    let mut cases = Vec::new();
    for definition in definitions {
        let count = Arc::new(AtomicUsize::new(0));
        let callback_count = count.clone();
        let mut prop = inertia::lazy(move || async move {
            callback_count.fetch_add(1, Ordering::SeqCst);
            7
        })
        .once_as("ttl-cache");
        if let Some(ttl) = definition["ttlMillis"].as_u64() {
            prop = prop.until(Duration::from_millis(ttl));
        }
        if definition["fresh"].as_bool().unwrap_or(false) {
            prop = prop.fresh();
        }
        let mut props = Props::new();
        props.insert("catalog", prop);
        let mut headers = HeaderMap::new();
        for (name, value) in definition["headers"].as_object().unwrap() {
            headers.insert(
                name.parse::<HeaderName>().unwrap(),
                value.as_str().unwrap().parse::<HeaderValue>().unwrap(),
            );
        }
        let request = Request::new(Method::GET, &"/ttl".parse().unwrap(), headers);
        let inertia = Inertia::new(Config::new().version("v1"), request);
        let before = now_millis();
        let page = inertia.render("Home", props).into_page().await.unwrap();
        let after = now_millis();
        cases.push(json!({"name":definition["name"], "beforeMillis":before, "afterMillis":after, "calls":count.load(Ordering::SeqCst), "page":page}));
    }
    println!(
        "{}",
        serde_json::to_string_pretty(&json!({"format":1,"cases":cases})).unwrap()
    );
}
