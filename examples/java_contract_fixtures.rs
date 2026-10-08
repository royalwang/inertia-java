//! Export reference Page objects for the Java adapter. Run from the repository root.
use http::{HeaderMap, HeaderName, Method};
use inertia::{Config, Inertia, Prop, Props, Request, ValidationErrors};
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
            {"key":"optional","kind":"optional","value":9}]},
        {"name":"errors-default-scoped-and-mixed", "headers":{"x-inertia-error-bag":"createUser"}, "definitions":[],
         "errors":[{"default":{"name":["Required", "Too short"]}, "login":{"email":["Invalid"]}}]},
        {"name":"errors-default-all", "headers":{}, "definitions":[], "allErrors":true,
         "errors":[{"default":{"name":["Required", "Too short"], "empty":[]}, "login":{"email":["Invalid"]}}]},
        {"name":"errors-named-first", "headers":{"x-inertia-error-bag":"ignored"}, "definitions":[],
         "errors":[{"login":{"email":["Required", "Invalid"]}, "register":{"name":["Too short"]}}]},
        {"name":"errors-named-all-merge", "headers":{}, "definitions":[], "allErrors":true,
         "errors":[{"login":{"email":["Required"]}}, {"login":{"email":["Invalid"], "password":["Use a symbol"]}}]}
    ]);
    let mut output = Vec::new();
    for case in definitions.as_array().unwrap() {
        let mut headers = HeaderMap::new();
        headers.insert("x-inertia", "true".parse().unwrap());
        headers.insert("x-inertia-version", "v1".parse().unwrap());
        for (key, value) in case["headers"].as_object().unwrap() {
            headers.insert(
                HeaderName::from_bytes(key.as_bytes()).unwrap(),
                value.as_str().unwrap().parse().unwrap(),
            );
        }
        let request = Request::new(Method::GET, &"/users?page=2".parse().unwrap(), headers);
        let inertia = Inertia::new(
            Config::new()
                .version("v1")
                .with_all_errors(case["allErrors"].as_bool().unwrap_or(false)),
            request,
        );
        if let Some(batches) = case["errors"].as_array() {
            for batch in batches {
                for (bag, fields) in batch.as_object().unwrap() {
                    let mut errors = ValidationErrors::new();
                    for (field, messages) in fields.as_object().unwrap() {
                        // Retain empty fields as well as multiple messages.
                        let mut values: serde_json::Map<String, Value> = serde_json::Map::new();
                        values.insert(field.clone(), messages.clone());
                        let field_errors: ValidationErrors = serde_json::from_value(Value::Object(values)).unwrap();
                        errors.merge(field_errors);
                    }
                    inertia.with_errors_in(bag, errors);
                }
            }
        }
        let mut props = Props::new();
        for definition in case["definitions"].as_array().unwrap() {
            let value = definition["value"].clone();
            let prop: Prop = match definition["kind"].as_str().unwrap() {
                "lazy" => inertia::lazy(move || async move { value }),
                "optional" => inertia::optional(move || async move { value }),
                "deferred" => inertia::defer(move || async move { value }),
                _ => Prop::value(value),
            };
            props.insert(definition["key"].as_str().unwrap(), prop);
        }
        let page = inertia.render("Home", props).into_page().await.unwrap();
        let mut fixture = case.clone();
        fixture["expectedPage"] = serde_json::to_value(page).unwrap();
        output.push(fixture);
    }
    let fixture: Value = json!({"rustBaseline":"6667d8d1be314067af989eb049ba419a07fcd412","cases":output});
    println!("{}", serde_json::to_string_pretty(&fixture).unwrap());
}
