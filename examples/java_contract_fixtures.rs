//! Export reference Page objects for the Java adapter. Run from the repository root.
use http::{HeaderMap, HeaderName, Method};
use inertia::props::{IntoProp, ProvidesScrollMetadata, ScrollMetadata};
use inertia::{Config, Inertia, Prop, Props, Request, ValidationErrors};
use serde::Serialize;
use serde_json::{Value, json};

#[derive(Serialize)]
struct FixtureScroll {
    #[serde(flatten)]
    value: serde_json::Map<String, Value>,
    #[serde(skip)]
    metadata: ScrollMetadata,
}
impl ProvidesScrollMetadata for FixtureScroll {
    fn scroll_metadata(&self) -> ScrollMetadata {
        self.metadata.clone()
    }
}
fn props(definitions: &Value) -> Props {
    let mut props = Props::new();
    for definition in definitions.as_array().into_iter().flatten() {
        props.insert(definition["key"].as_str().unwrap(), prop(definition));
    }
    props
}
fn prop(definition: &Value) -> Prop {
    let value = definition["value"].clone();
    let mut prop = match definition["kind"].as_str().unwrap() {
        "lazy" => inertia::lazy(move || async move { value }),
        "optional" => inertia::optional(move || async move { value }),
        "deferred" => inertia::defer(move || async move { value }),
        "failure" => inertia::props::try_lazy(|| async { Err::<Value, _>(std::io::Error::other("fixture failure")) }),
        "nested" => props(&definition["definitions"]).into_prop(),
        "scroll" | "scroll-lazy" => {
            let wrapper = definition["wrapper"].as_str().unwrap_or("data");
            let page = FixtureScroll {
                value: serde_json::Map::from_iter([(wrapper.to_owned(), value)]),
                metadata: serde_json::from_value(definition["scroll"].clone()).unwrap(),
            };
            (if definition["kind"] == "scroll-lazy" {
                inertia::scroll_with(move || async move { page })
            } else {
                inertia::scroll(page)
            })
            .wrapper(wrapper)
        }
        "literal" => Prop::value(value),
        other => panic!("Unknown fixture kind: {other}"),
    };
    if definition["loading"] == "deferred" {
        prop = prop.deferred();
    }
    if let Some(group) = definition["group"].as_str() {
        prop = prop.group(group);
    }
    if definition["always"].as_bool().unwrap_or(false) {
        prop = prop.always();
    }
    prop = match definition["merge"].as_str() {
        Some("merge") => prop.merge(),
        Some("prepend") => prop.prepend(),
        Some("deep") => prop.deep_merge(),
        None => prop,
        Some(other) => panic!("Unknown merge: {other}"),
    };
    for at in definition["appendAt"].as_array().into_iter().flatten() {
        prop = prop.append_at(at.as_str().unwrap());
    }
    for at in definition["prependAt"].as_array().into_iter().flatten() {
        prop = prop.prepend_at(at.as_str().unwrap());
    }
    for at in definition["matchOn"].as_array().into_iter().flatten() {
        prop = prop.match_on(at.as_str().unwrap());
    }
    if definition["once"] == true {
        prop = prop.once();
    }
    if let Some(key) = definition["once"].as_str() {
        prop = prop.once_as(key);
    }
    if definition["fresh"] == true {
        prop = prop.fresh();
    }
    if definition["rescue"] == true {
        prop = prop.rescue();
    }
    prop
}
#[tokio::main]
async fn main() {
    let definitions: Value = serde_json::from_str(include_str!("java_contract_cases.json")).unwrap();
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
        let shared = case["configShared"].clone();
        let mut config = Config::new().version("v1");
        if let Some(url) = case["resolvedUrl"].as_str() {
            let url = url.to_owned();
            config = config.resolve_url_using(move |_| url.clone());
        }
        config = config.expose_shared_prop_keys(case["exposeShared"].as_bool().unwrap_or(true));
        let inertia = Inertia::new(
            config
                .with_all_errors(case["allErrors"].as_bool().unwrap_or(false))
                .encrypt_history(case["configEncrypt"].as_bool().unwrap_or(false))
                .preserve_big_integers(case["configBigint"].as_bool().unwrap_or(false))
                .share(move |_| props(&shared)),
            request,
        );
        if let Some(batches) = case["errors"].as_array() {
            for batch in batches {
                for (bag, fields) in batch.as_object().unwrap() {
                    inertia.with_errors_in(bag, serde_json::from_value::<ValidationErrors>(fields.clone()).unwrap());
                }
            }
        }
        if let Some(shared) = case["requestShared"].as_array() {
            for definition in shared {
                inertia.share(definition["key"].as_str().unwrap(), prop(definition));
            }
        }
        if let Some(value) = case["requestEncrypt"].as_bool() {
            inertia.encrypt_history(value);
        }
        if case["preserveFragment"] == true {
            inertia.preserve_fragment();
        }
        for (key, value) in case["requestFlash"].as_object().into_iter().flatten() {
            inertia.flash(key, value);
        }
        let mut response = inertia.render("Home", props(&case["definitions"]));
        if let Some(value) = case["responseEncrypt"].as_bool() {
            response = response.encrypt_history(value);
        }
        if let Some(value) = case["responseBigint"].as_bool() {
            response = response.preserve_big_integers(value);
        }
        if case["clearHistory"] == true {
            response = response.clear_history();
        }
        for (key, value) in case["responseFlash"].as_object().into_iter().flatten() {
            response = response.flash(key, value);
        }
        let page = response.into_page().await.unwrap();
        let mut fixture = case.clone();
        fixture["expectedPage"] = serde_json::to_value(page).unwrap();
        output.push(fixture);
    }
    println!(
        "{}",
        serde_json::to_string_pretty(
            &json!({"rustBaseline":"6667d8d1be314067af989eb049ba419a07fcd412","cases":output})
        )
        .unwrap()
    );
}
