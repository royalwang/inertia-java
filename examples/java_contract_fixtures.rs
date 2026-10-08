//! Export reference Page objects for the Java adapter. Run from the repository root.
use http::{HeaderMap, HeaderName, Method};
use inertia::{Config, Inertia, Prop, Props, Request};
use serde_json::{Value, json};

#[tokio::main]
async fn main() {
    let definitions = json!([
        {"name":"full-basic", "headers":{}, "definitions":[
            {"key":"user","kind":"literal","value":{"name":"Ada","role":"reader"}},
            {"key":"stats","kind":"deferred","value":7},
            {"key":"billing","kind":"optional","value":9}]},
        {"name":"partial-nested", "headers":{"x-inertia-partial-component":"Home","x-inertia-partial-data":"user.name"}, "definitions":[
            {"key":"user","kind":"literal","value":{"name":"Ada","role":"reader"}},
            {"key":"stats","kind":"lazy","value":7}]},
        {"name":"partial-deferred", "headers":{"x-inertia-partial-component":"Home","x-inertia-partial-data":"stats"}, "definitions":[
            {"key":"user","kind":"literal","value":{"name":"Ada"}},
            {"key":"stats","kind":"deferred","value":7}]},
        {"name":"different-component", "headers":{"x-inertia-partial-component":"Other","x-inertia-partial-data":"nothing"}, "definitions":[
            {"key":"user.name","kind":"literal","value":"Ada"},
            {"key":"stats","kind":"lazy","value":7},
            {"key":"optional","kind":"optional","value":9}]}
    ]);
    let mut output = Vec::new();
    for case in definitions.as_array().unwrap() {
        let mut headers = HeaderMap::new();
        headers.insert("x-inertia", "true".parse().unwrap());
        headers.insert("x-inertia-version", "v1".parse().unwrap());
        for (key,value) in case["headers"].as_object().unwrap() {
            headers.insert(HeaderName::from_bytes(key.as_bytes()).unwrap(),value.as_str().unwrap().parse().unwrap());
        }
        let request = Request::new(Method::GET,&"/users?page=2".parse().unwrap(),headers);
        let inertia = Inertia::new(Config::new().version("v1"),request);
        let mut props = Props::new();
        for definition in case["definitions"].as_array().unwrap() {
            let value = definition["value"].clone();
            let prop: Prop = match definition["kind"].as_str().unwrap() {
                "lazy" => inertia::lazy(move || async move { value }),
                "optional" => inertia::optional(move || async move { value }),
                "deferred" => inertia::defer(move || async move { value }),
                _ => Prop::value(value),
            };
            props.insert(definition["key"].as_str().unwrap(),prop);
        }
        let page = inertia.render("Home",props).into_page().await.unwrap();
        let mut fixture = case.clone();
        fixture["expectedPage"] = serde_json::to_value(page).unwrap();
        output.push(fixture);
    }
    let fixture: Value = json!({"rustBaseline":"6667d8d1be314067af989eb049ba419a07fcd412","cases":output});
    println!("{}",serde_json::to_string_pretty(&fixture).unwrap());
}
