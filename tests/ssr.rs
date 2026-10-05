//! Server-side rendering through `HttpGateway`, against stand-in SSR servers.

#![cfg(feature = "ssr")]

use axum::Router;
use axum::http::StatusCode;
use axum::routing::post;
use http::{HeaderMap, Method};
use inertia::ssr::HttpGateway;
use inertia::testing::AssertablePage;
use inertia::{Config, Inertia, Request, props};
use serde_json::json;
use tokio::net::TcpListener;

/// Serve `router` on a free port, returning its URL.
async fn serve(router: Router) -> String {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());

    tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });

    url
}

/// The body of a first visit, rendered through the SSR server at `url`.
async fn first_visit(url: &str) -> String {
    let config = Config::new().ssr(HttpGateway::new().url(url));
    let request = Request::new(Method::GET, &"/".parse().unwrap(), HeaderMap::new());

    let response = Inertia::new(config, request)
        .render("Home", props! { "name" => "Taylor" })
        .into_http()
        .await;

    response.into_body()
}

#[tokio::test]
async fn pages_are_rendered_by_the_ssr_server() {
    let url = serve(Router::new().route(
        "/render",
        post(|| async {
            axum::Json(json!({ "head": ["<title>Home</title>"], "body": "<div id=\"app\">Taylor</div>" }))
        }),
    ))
    .await;

    let body = first_visit(&url).await;

    assert!(body.contains("<title>Home</title>"), "{body}");
    assert!(body.contains(r#"<div id="app">Taylor</div>"#), "{body}");
}

#[tokio::test]
async fn failed_renders_fall_back_to_the_client() {
    let url = serve(Router::new().route("/render", post(|| async { StatusCode::INTERNAL_SERVER_ERROR }))).await;

    let body = first_visit(&url).await;

    assert!(body.contains(r#"<div id="app"></div>"#), "{body}");
    AssertablePage::from_body(&body)
        .component("Home")
        .equals("name", "Taylor");
}

#[tokio::test]
async fn unreachable_servers_fall_back_to_the_client() {
    // A port that was free a moment ago, so nothing is listening on it.
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    drop(listener);

    let body = first_visit(&url).await;

    assert!(body.contains(r#"<div id="app"></div>"#), "{body}");
    AssertablePage::from_body(&body)
        .component("Home")
        .equals("name", "Taylor");
}
