package io.inertia.core;

import java.util.Objects;
import java.util.concurrent.*;

/** A request-local source and composable loading behavior. */
public record Prop(
    Source source,
    Loading loading,
    String group,
    boolean always,
    boolean rescued,
    Merge mergeOptions,
    Once onceOptions,
    boolean scroll) {
  public Prop(Source source, Loading loading, String group, boolean always, boolean rescued) {
    this(source, loading, group, always, rescued, null, null, false);
  }

  public record Merge(
      boolean deep,
      boolean prependRoot,
      java.util.List<String> appendAt,
      java.util.List<String> prependAt,
      java.util.List<String> matchOn) {
    public Merge {
      appendAt = java.util.List.copyOf(appendAt);
      prependAt = java.util.List.copyOf(prependAt);
      matchOn = java.util.List.copyOf(matchOn);
    }
  }

  public record Once(String key, java.time.Duration ttl, boolean fresh) {
    public Once {
      if (ttl != null && ttl.isNegative())
        throw new IllegalArgumentException("Negative onceOptions TTL");
    }
  }

  public enum Loading {
    EAGER,
    OPTIONAL,
    DEFERRED
  }

  @FunctionalInterface
  public interface Task {
    Object get() throws Exception;
  }

  public sealed interface Source permits Literal, Nested, Computed, Async {}

  public record Literal(Object value) implements Source {}

  public record Nested(Props props) implements Source {}

  public record Computed(Task task) implements Source {}

  public record Async(java.util.function.Supplier<CompletionStage<?>> task) implements Source {}

  public Prop {
    Objects.requireNonNull(source);
    Objects.requireNonNull(loading);
    Objects.requireNonNull(group);
  }

  public static Prop value(Object value) {
    return new Prop(
        value instanceof Props p ? new Nested(p) : new Literal(value),
        Loading.EAGER,
        "default",
        false,
        false);
  }

  public static Prop lazy(Task task) {
    return new Prop(
        new Computed(Objects.requireNonNull(task)), Loading.EAGER, "default", false, false);
  }

  public static Prop async(java.util.function.Supplier<CompletionStage<?>> task) {
    return new Prop(new Async(task), Loading.EAGER, "default", false, false);
  }

  public static Prop optional(Task task) {
    return new Prop(new Computed(task), Loading.OPTIONAL, "default", false, false);
  }

  public static Prop defer(Task task) {
    return new Prop(new Computed(task), Loading.DEFERRED, "default", false, false);
  }

  public static Prop always(Object value) {
    return new Prop(value(value).source, Loading.EAGER, "default", true, false);
  }

  public Prop group(String value) {
    if (loading != Loading.DEFERRED || value.isBlank())
      throw new IllegalArgumentException("Group requires deferred prop");
    return new Prop(source, loading, value, always, rescued, mergeOptions, onceOptions, scroll);
  }

  public Prop merge() {
    return copy(
        new Merge(false, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
        onceOptions,
        scroll);
  }

  public Prop deepMerge() {
    return copy(
        new Merge(true, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
        onceOptions,
        scroll);
  }

  public Prop prepend() {
    return copy(
        new Merge(false, true, java.util.List.of(), java.util.List.of(), matches()),
        onceOptions,
        scroll);
  }

  public Prop appendAt(String path) {
    return mergeAt(path, false);
  }

  public Prop prependAt(String path) {
    return mergeAt(path, true);
  }

  private Prop mergeAt(String path, boolean prepend) {
    checkPath(path);
    var appends =
        new java.util.ArrayList<>(
            mergeOptions == null ? java.util.List.<String>of() : mergeOptions.appendAt());
    var prepends =
        new java.util.ArrayList<>(
            mergeOptions == null ? java.util.List.<String>of() : mergeOptions.prependAt());
    (prepend ? prepends : appends).add(path);
    return copy(new Merge(false, false, appends, prepends, matches()), onceOptions, scroll);
  }

  public Prop matchOn(String path) {
    checkPath(path);
    if (mergeOptions == null) throw new IllegalStateException("matchOn requires mergeOptions");
    var paths = new java.util.ArrayList<>(matches());
    paths.add(path);
    return copy(
        new Merge(
            mergeOptions.deep(),
            mergeOptions.prependRoot(),
            mergeOptions.appendAt(),
            mergeOptions.prependAt(),
            paths),
        onceOptions,
        scroll);
  }

  private java.util.List<String> matches() {
    return mergeOptions == null ? java.util.List.of() : mergeOptions.matchOn();
  }

  public Prop once() {
    return copy(mergeOptions, new Once(null, null, false), scroll);
  }

  public Prop onceAs(String key) {
    if (key.isBlank()) throw new IllegalArgumentException("Empty onceOptions key");
    return copy(
        mergeOptions,
        new Once(
            key,
            onceOptions == null ? null : onceOptions.ttl(),
            onceOptions != null && onceOptions.fresh()),
        scroll);
  }

  public Prop until(java.time.Duration ttl) {
    return copy(
        mergeOptions,
        new Once(
            onceOptions == null ? null : onceOptions.key(),
            ttl,
            onceOptions != null && onceOptions.fresh()),
        scroll);
  }

  public Prop fresh() {
    return copy(
        mergeOptions,
        new Once(
            onceOptions == null ? null : onceOptions.key(),
            onceOptions == null ? null : onceOptions.ttl(),
            true),
        scroll);
  }

  public static Prop scroll(ScrollPage page) {
    return value(page)
        .copy(
            new Merge(false, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
            null,
            true);
  }

  public static Prop scrollWith(Task task) {
    return lazy(task)
        .copy(
            new Merge(false, false, java.util.List.of(), java.util.List.of(), java.util.List.of()),
            null,
            true);
  }

  private Prop copy(Merge mergeOptions, Once onceOptions, boolean scroll) {
    return new Prop(source, loading, group, always, rescued, mergeOptions, onceOptions, scroll);
  }

  private static void checkPath(String path) {
    Props.builder().put(path, 1).build();
  }

  public Prop rescue() {
    if (loading != Loading.DEFERRED)
      throw new IllegalArgumentException("Rescue requires deferred prop");
    return new Prop(source, loading, group, always, true, mergeOptions, onceOptions, scroll);
  }
}
