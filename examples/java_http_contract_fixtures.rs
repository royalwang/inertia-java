//! Export real Rust HTTP policy results for Java compatibility contracts.
use http::{HeaderMap, HeaderName, HeaderValue, Method, StatusCode, Uri};
use inertia::{Config, HttpResponse, Request, protocol};
use serde_json::{Value, json};

fn snapshot(response: HttpResponse) -> Value {
    let mut headers = serde_json::Map::new();
    for name in response.headers().keys() {
        let values: Vec<&str> = response
            .headers()
            .get_all(name)
            .iter()
            .map(|value| value.to_str().expect("UTF-8 fixture header"))
            .collect();
        headers.insert(name.to_string(), json!(values));
    }
    json!({"status": response.status().as_u16(), "headers": headers, "body": response.body()})
}

fn main() {
    let definitions: Vec<Value> = serde_json::from_str(include_str!("java_http_contract_cases.json")).unwrap();
    let mut cases = Vec::new();
    for definition in definitions {
        let uri: Uri = definition["url"].as_str().unwrap().parse().unwrap();
        let method: Method = definition["method"].as_str().unwrap().parse().unwrap();
        let mut headers = HeaderMap::new();
        for (name, value) in definition["headers"].as_object().unwrap() {
            headers.insert(
                name.parse::<HeaderName>().unwrap(),
                value.as_str().unwrap().parse::<HeaderValue>().unwrap(),
            );
        }
        let request = Request::new(method, &uri, headers);
        let expected = match definition["operation"].as_str().unwrap() {
            "before" => protocol::before(
                &request,
                &Config::new().version(definition["version"].as_str().unwrap()),
            )
            .map(snapshot)
            .unwrap_or(Value::Null),
            "redirect" => snapshot(protocol::redirect(definition["target"].as_str().unwrap())),
            "location" => snapshot(protocol::location(&request, definition["target"].as_str().unwrap())),
            "after" => {
                let mut response = HttpResponse::new(definition["body"].as_str().unwrap().to_owned());
                *response.status_mut() = StatusCode::from_u16(definition["status"].as_u64().unwrap() as u16).unwrap();
                for (name, values) in definition["responseHeaders"].as_object().unwrap() {
                    for value in values.as_array().unwrap() {
                        response.headers_mut().append(
                            name.parse::<HeaderName>().unwrap(),
                            value.as_str().unwrap().parse::<HeaderValue>().unwrap(),
                        );
                    }
                }
                let (mut parts, body) = response.into_parts();
                let replacement = protocol::after(&request, &mut parts, body.is_empty());
                snapshot(replacement.unwrap_or_else(|| HttpResponse::from_parts(parts, body)))
            }
            _ => panic!("Unknown fixture operation"),
        };
        let mut case = definition;
        case["expectedRust"] = expected;
        cases.push(case);
    }
    println!(
        "{}",
        serde_json::to_string_pretty(
            &json!({"format":1,"rustSourceBaseline":"6667d8d1be314067af989eb049ba419a07fcd412","cases":cases})
        )
        .unwrap()
    );
}
