//! Resolves a page's props for a request, collecting the page metadata
//! (`deferredProps`, `mergeProps`, `onceProps`, ...) along the way.
//!
//! A port of the Laravel adapter's `PropsResolver`, with one difference:
//! callbacks and nested props resolve concurrently across the whole tree.

use std::time::{SystemTime, UNIX_EPOCH};

use futures_util::future::{BoxFuture, join, join_all};
use serde_json::{Map, Value};

use super::prop::Options;
use super::{Computed, Loading, Merge, Once, Prop, PropError, Props, Source};
use crate::page::{Metadata, OnceState, ScrollState};
use crate::request::Request;

/// A resolved level of the props tree, and the metadata it announces.
type Resolution = Result<(Map<String, Value>, Metadata), PropError>;

pub(crate) struct PropsResolver<'a> {
    request: &'a Request,
    is_partial: bool,
}

/// What to do with a prop once all of its siblings have been planned.
enum Plan {
    /// Left out of the response, though it may still announce metadata.
    Excluded { path: String, options: Options },
    /// Part of the response.
    Included {
        key: String,
        path: String,
        options: Options,
        value: Result<Resolved, PropError>,
    },
}

/// A prop value on its way into the response.
enum Resolved {
    /// A literal value; nested objects are still subject to partial filtering.
    Literal(Value),
    /// Nested props resolving concurrently with their siblings, by index.
    Nested(usize),
    /// A callback running concurrently with its siblings, by index.
    Pending(usize),
    /// A callback whose output gains nested props once it runs, by index.
    Extended(usize, Vec<(Vec<String>, Prop)>),
    /// A callback's output; its children bypass partial filtering.
    Computed(Value),
    /// An extended callback's output, with its nested props set.
    Expanded(Props),
}

/// A planned prop's contribution to the response, kept in prop order.
struct Entry {
    metadata: Metadata,
    output: Option<(String, Output)>,
}

enum Output {
    Value(Value),
    /// The result of a nested level, by index.
    Nested(usize),
    /// The result of an expanded callback's level, by index.
    Expanded(usize),
}

impl<'a> PropsResolver<'a> {
    pub(crate) fn new(request: &'a Request, component: &str) -> Self {
        Self {
            request,
            is_partial: request.is_partial_reload_of(component),
        }
    }

    /// Resolve the shared props and page props, returning the resolved props
    /// and their metadata. Page props take precedence over shared props.
    pub(crate) async fn resolve(
        self,
        shared: Props,
        props: Props,
        expose_shared_keys: bool,
    ) -> Result<(Map<String, Value>, Metadata), PropError> {
        let mut metadata = Metadata::default();

        if expose_shared_keys {
            for key in shared.keys() {
                let key = key.split('.').next().unwrap_or(key);

                if !metadata.shared_props.iter().any(|shared| shared == key) {
                    metadata.shared_props.push(key.to_owned());
                }
            }
        }

        let mut all = shared;
        all.0.extend(props.0);

        let all = unpack_dot_keys(all)?;
        let (resolved, collected) = self.resolve_props(all, String::new(), false).await?;
        metadata.extend(collected);

        Ok((resolved, metadata))
    }

    /// Recursively resolve a level of the props tree.
    ///
    /// A level's callbacks run concurrently with each other and with its
    /// nested levels, while metadata is still collected in prop order so the
    /// page object matches the Laravel adapter's.
    fn resolve_props(&self, props: Props, prefix: String, parent_was_resolved: bool) -> BoxFuture<'_, Resolution> {
        Box::pin(async move {
            let mut plans = Vec::with_capacity(props.len());
            let mut callbacks = Vec::new();
            let mut nested = Vec::new();

            for (key, Prop { source, mut options }) in props {
                let path = if prefix.is_empty() {
                    key.clone()
                } else {
                    format!("{prefix}.{key}")
                };

                // Partial reloads only include the props they ask for. Always
                // props, and the children of resolved values, are exempt.
                if !self.is_included_in_partial_reload(&options, &path, parent_was_resolved) {
                    continue;
                }

                // Optional, deferred and already loaded once props are left out
                // of full visits, without running their callbacks.
                if !self.is_partial && self.is_excluded_from_full_visit(&options, &path) {
                    plans.push(Plan::Excluded { path, options });
                    continue;
                }

                if let (Some(scroll), Some(merge)) = (&options.scroll, &mut options.merge) {
                    if self.request.prepends_scroll() {
                        merge.prepends_at.push(scroll.wrapper.clone());
                    } else {
                        merge.appends_at.push(scroll.wrapper.clone());
                    }
                }

                let value = match source {
                    Source::Value(value) => Ok(Resolved::Literal(value)),
                    Source::Props(props) => {
                        nested.push(self.resolve_props(props, path.clone(), parent_was_resolved));
                        Ok(Resolved::Nested(nested.len() - 1))
                    }
                    Source::Failed(error) => Err(error),
                    Source::Callback(callback) => {
                        callbacks.push(callback());
                        Ok(Resolved::Pending(callbacks.len() - 1))
                    }
                    Source::Extended { base, overlay } => {
                        callbacks.push(base());
                        Ok(Resolved::Extended(callbacks.len() - 1, overlay))
                    }
                };

                plans.push(Plan::Included {
                    key,
                    path,
                    options,
                    value,
                });
            }

            let (computed, nested) = join(join_all(callbacks), join_all(nested)).await;
            let mut computed: Vec<_> = computed.into_iter().map(Some).collect();
            let mut nested: Vec<_> = nested.into_iter().map(Some).collect();
            let mut expanded = Vec::new();
            let mut entries = Vec::with_capacity(plans.len());

            for plan in plans {
                let mut metadata = Metadata::default();

                let (key, path, mut options, value) = match plan {
                    Plan::Excluded { path, options } => {
                        self.collect_excluded_metadata(&mut metadata, &options, &path);
                        entries.push(Entry { metadata, output: None });
                        continue;
                    }
                    Plan::Included {
                        key,
                        path,
                        options,
                        value,
                    } => (key, path, options, value),
                };

                let value = value.and_then(|value| match value {
                    Resolved::Pending(index) => {
                        let Computed { value, scroll } = take(&mut computed, index)?;

                        if let (Some(options), Some(metadata)) = (&mut options.scroll, scroll) {
                            options.metadata = Some(metadata);
                        }

                        Ok(Resolved::Computed(value))
                    }
                    Resolved::Extended(index, overlay) => {
                        let mut props = object_props(take(&mut computed, index)?.value);

                        for (segments, prop) in overlay {
                            set_nested(&mut props, &segments, prop)?;
                        }

                        Ok(Resolved::Expanded(props))
                    }
                    value => Ok(value),
                });

                let value = match value {
                    Ok(value) => value,
                    Err(error) if options.rescue => {
                        tracing::error!(prop = path, %error, "rescued an Inertia prop that failed to resolve");
                        metadata.rescued_props.push(path);
                        entries.push(Entry { metadata, output: None });
                        continue;
                    }
                    Err(error) => return Err(error),
                };

                self.collect_metadata(&mut metadata, &options, &path);

                let output = match value {
                    Resolved::Nested(index) => Output::Nested(index),
                    Resolved::Expanded(props) => {
                        expanded.push(self.resolve_props(props, path, parent_was_resolved));
                        Output::Expanded(expanded.len() - 1)
                    }
                    Resolved::Literal(value) => Output::Value(self.filter_literal(value, &path, parent_was_resolved)),
                    Resolved::Computed(value) => Output::Value(value),
                    Resolved::Pending(_) | Resolved::Extended(..) => unreachable!("callbacks are computed above"),
                };

                entries.push(Entry {
                    metadata,
                    output: Some((key, output)),
                });
            }

            let mut expanded: Vec<_> = join_all(expanded).await.into_iter().map(Some).collect();
            let mut resolved = Map::new();
            let mut metadata = Metadata::default();

            for Entry { metadata: own, output } in entries {
                metadata.extend(own);

                let Some((key, output)) = output else {
                    continue;
                };

                let value = match output {
                    Output::Value(value) => value,
                    Output::Nested(index) => {
                        let (children, collected) = take(&mut nested, index)?;
                        metadata.extend(collected);
                        Value::Object(children)
                    }
                    Output::Expanded(index) => {
                        let (children, collected) = take(&mut expanded, index)?;
                        metadata.extend(collected);
                        Value::Object(children)
                    }
                };

                resolved.insert(key, value);
            }

            Ok((resolved, metadata))
        })
    }

    /// Apply partial reload filtering to the children of a literal object.
    fn filter_literal(&self, value: Value, path: &str, parent_was_resolved: bool) -> Value {
        if !self.is_partial || parent_was_resolved {
            return value;
        }

        let Value::Object(object) = value else {
            return value;
        };

        Value::Object(
            object
                .into_iter()
                .filter_map(|(key, child)| {
                    let path = format!("{path}.{key}");

                    self.matches_partial_reload(&path)
                        .then(|| (key, self.filter_literal(child, &path, false)))
                })
                .collect(),
        )
    }

    fn is_included_in_partial_reload(&self, options: &Options, path: &str, parent_was_resolved: bool) -> bool {
        !self.is_partial || options.always || parent_was_resolved || self.matches_partial_reload(path)
    }

    /// Bidirectional prefix matching against the requested paths: `auth`
    /// leads to `auth.user`, and `auth.user` is within `auth`.
    fn matches_partial_reload(&self, path: &str) -> bool {
        if let Some(only) = self.request.only()
            && !only.iter().any(|only| is_within(path, only) || is_within(only, path))
        {
            return false;
        }

        !self.is_excepted(path)
    }

    /// Whether a prop contributes metadata to a partial reload: unlike
    /// filtering, merely leading to a requested path isn't enough.
    fn contributes_partial_metadata(&self, path: &str) -> bool {
        if let Some(only) = self.request.only()
            && !only.iter().any(|only| is_within(path, only))
        {
            return false;
        }

        !self.is_excepted(path)
    }

    fn is_excepted(&self, path: &str) -> bool {
        self.request
            .except()
            .is_some_and(|except| except.iter().any(|except| is_within(path, except)))
    }

    fn is_excluded_from_full_visit(&self, options: &Options, path: &str) -> bool {
        options.loading != Loading::Eager || (self.request.is_inertia() && self.was_already_loaded(options, path))
    }

    /// Whether the client already has this once prop.
    fn was_already_loaded(&self, options: &Options, path: &str) -> bool {
        options.once.as_ref().is_some_and(|once| {
            let key = once.key.as_deref().unwrap_or(path);

            !once.fresh && self.request.except_once_props().iter().any(|loaded| loaded == key)
        })
    }

    /// Collect the metadata a prop announces while being left out.
    fn collect_excluded_metadata(&self, metadata: &mut Metadata, options: &Options, path: &str) {
        if let Loading::Deferred { group } = &options.loading
            && !self.was_already_loaded(options, path)
        {
            metadata
                .deferred_props
                .entry(group.clone())
                .or_default()
                .push(path.to_owned());
        }

        if options.loading != Loading::Eager
            && let Some(merge) = &options.merge
        {
            self.collect_merge_metadata(metadata, merge, path);
        }

        if let Some(once) = &options.once {
            self.collect_once_metadata(metadata, once, path);
        }
    }

    /// Collect the metadata of a prop that is part of the response.
    fn collect_metadata(&self, metadata: &mut Metadata, options: &Options, path: &str) {
        if let Some(merge) = &options.merge {
            self.collect_merge_metadata(metadata, merge, path);
        }

        if let Some(scroll) = options.scroll.as_ref().and_then(|scroll| scroll.metadata.clone()) {
            let reset = self.request.reset().iter().any(|reset| reset == path);

            metadata.scroll_props.insert(
                path.to_owned(),
                ScrollState {
                    metadata: scroll,
                    reset,
                },
            );
        }

        if let Some(once) = &options.once {
            self.collect_once_metadata(metadata, once, path);
        }
    }

    fn collect_merge_metadata(&self, metadata: &mut Metadata, merge: &Merge, path: &str) {
        if self.request.reset().iter().any(|reset| reset == path)
            || (self.is_partial && !self.contributes_partial_metadata(path))
        {
            return;
        }

        if merge.deep {
            metadata.deep_merge_props.push(path.to_owned());
        } else if merge.appends_at_root() {
            metadata.merge_props.push(path.to_owned());
        } else if merge.prepends_at_root() {
            metadata.prepend_props.push(path.to_owned());
        } else {
            metadata
                .merge_props
                .extend(merge.appends_at.iter().map(|at| format!("{path}.{at}")));
            metadata
                .prepend_props
                .extend(merge.prepends_at.iter().map(|at| format!("{path}.{at}")));
        }

        metadata
            .match_props_on
            .extend(merge.match_on.iter().map(|key| format!("{path}.{key}")));
    }

    fn collect_once_metadata(&self, metadata: &mut Metadata, once: &Once, path: &str) {
        if self.is_partial && !self.contributes_partial_metadata(path) {
            return;
        }

        // Second precision, like the Laravel adapter.
        let expires_at = once.ttl.map(|ttl| {
            let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default();

            now.as_secs().saturating_add(ttl.as_secs()).saturating_mul(1000)
        });

        metadata.once_props.insert(
            once.key.clone().unwrap_or_else(|| path.to_owned()),
            OnceState {
                prop: path.to_owned(),
                expires_at,
            },
        );
    }
}

/// Take a concurrently computed result; each is taken exactly once.
fn take<T>(results: &mut [Option<T>], index: usize) -> T {
    results[index].take().expect("each result is taken once")
}

/// Whether `path` is `ancestor` or one of its descendants.
fn is_within(path: &str, ancestor: &str) -> bool {
    path.strip_prefix(ancestor)
        .is_some_and(|rest| rest.is_empty() || rest.starts_with('.'))
}

/// Unpack top-level dot-notation keys (`"auth.user"`) into nested props.
fn unpack_dot_keys(mut props: Props) -> Result<Props, PropError> {
    let dotted: Vec<String> = props
        .keys()
        .filter(|key| key.contains('.'))
        .map(str::to_owned)
        .collect();

    for key in dotted {
        if let Some(prop) = props.remove(&key) {
            let segments: Vec<String> = key.split('.').map(str::to_owned).collect();
            set_nested(&mut props, &segments, prop)?;
        }
    }

    Ok(props)
}

/// Set a prop at a nested path. A callback on the way keeps its laziness:
/// the prop is set inside its output once it runs, if it's part of the response.
fn set_nested(props: &mut Props, segments: &[String], prop: Prop) -> Result<(), PropError> {
    let Some((first, rest)) = segments.split_first() else {
        return Ok(());
    };

    if rest.is_empty() {
        props.0.insert(first.clone(), prop);
        return Ok(());
    }

    let mut nested = match props.0.get_mut(first) {
        Some(Prop {
            source: Source::Props(nested),
            ..
        }) => return set_nested(nested, rest, prop),
        Some(Prop {
            source: Source::Extended { overlay, .. },
            ..
        }) => {
            overlay.push((rest.to_vec(), prop));
            return Ok(());
        }
        Some(existing) if is_plain(&existing.options) => {
            match std::mem::replace(&mut existing.source, Source::Value(Value::Null)) {
                Source::Callback(base) => {
                    existing.source = Source::Extended {
                        base,
                        overlay: vec![(rest.to_vec(), prop)],
                    };
                    return Ok(());
                }
                Source::Value(value) => object_props(value),
                Source::Failed(error) => return Err(error),
                Source::Props(_) | Source::Extended { .. } => unreachable!("matched above"),
            }
        }
        _ => Props::new(),
    };

    set_nested(&mut nested, rest, prop)?;
    props.insert(first.clone(), nested);

    Ok(())
}

fn is_plain(options: &Options) -> bool {
    options.loading == Loading::Eager
        && !options.always
        && options.merge.is_none()
        && options.once.is_none()
        && options.scroll.is_none()
}

/// The props of an object value; anything else has none.
fn object_props(value: Value) -> Props {
    match value {
        Value::Object(object) => object.into_iter().collect(),
        _ => Props::new(),
    }
}
