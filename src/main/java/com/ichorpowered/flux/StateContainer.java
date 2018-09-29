/*
 * MIT License
 *
 * Copyright (c) 2017 Connor Hartley
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.ichorpowered.flux;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.ichorpowered.flux.meta.MetaHolder;
import com.ichorpowered.flux.meta.MetaQuery;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class StateContainer implements ContainerHolder {

    public static StateContainer.Builder builder() {
        return new StateContainer.Builder();
    }

    public static StateContainer copyOf(final @NonNull MetaHolder metaHolder) {
        return new StateContainer.Builder().of(metaHolder).build();
    }

    public static StateContainer of(final @NonNull String label) {
        return new StateContainer.Builder().label(label).build();
    }

    public static StateContainer of(final @NonNull String label, final @NonNull String value) {
        return new StateContainer.Builder().tag(label, value).build();
    }

    private ContainerHolder root;
    private ContainerHolder parent;

    private ImmutableMap<String, String> tags = ImmutableMap.of();

    private final Map<String, String> localTags = new HashMap<>();

    private final List<MetaHolder> items = new ArrayList<>();

    private StateContainer(final StateContainer.Builder builder) {
        this.localTags.putAll(builder.tags);

        this.updateTags();
    }

    public <I extends ContainerHolder> @NonNull Optional<I> first() {
        return Optional.ofNullable((I) this.items.get(0));
    }

    public <I extends ContainerHolder> @NonNull Optional<I> queryOne(final @NonNull MetaQuery query) {
        return this.items.stream()
                .filter(query::test)
                .map(item -> (I) item)
                .findFirst();
    }

    public <I extends ContainerHolder> @NonNull Collection<I> queryMany(final @NonNull MetaQuery query) {
        return this.items.stream()
                .filter(query::test)
                .map(item -> (I) item)
                .collect(Collectors.toList());
    }

    public <E> @NonNull Optional<State<E>> queryOne(final @NonNull MetaQuery query, final @NonNull StateKey<E> key) {
        return this.items.stream()
                .filter(query::test)
                .filter(item -> {
                    if (!(item instanceof State<?>)) return false;
                    return key.equals(((State<E>) item).key());
                })
                .map(item -> (State<E>) item)
                .findFirst();
    }

    public <E> @NonNull Collection<State<E>> queryMany(final @NonNull MetaQuery query, final @NonNull StateKey<E> key) {
        return this.items.stream()
                .filter(query::test)
                .filter(item -> {
                    if (!(item instanceof State<?>)) return false;
                    return key.equals(((State<E>) item).key());
                })
                .map(item -> (State<E>) item)
                .collect(Collectors.toList());
    }

    public <E> @NonNull State<E> offer(final @NonNull StateKey<E> key, final @NonNull State<E> state) {
        this.items.removeIf(item -> (item instanceof State<?>) && item.containsAll(state) && key.equals(((State<E>) item).key()));
        state.setParent(this);
        this.items.add(state);
        return state;
    }

    public <E> @NonNull Optional<E> get(final @NonNull StateKey<E> key) {
        return this.items.stream()
                .filter(item -> {
                    if (!(item instanceof State<?>)) return false;
                    return key.equals(((State<E>) item).key());
                })
                .map(item -> ((State<E>) item).get())
                .findFirst();
    }

    @Override
    public <T extends ContainerHolder> @NonNull Optional<T> root() {
        return Optional.ofNullable((T) this.root);
    }

    @Override
    public <T extends ContainerHolder> @NonNull Optional<T> parent() {
        return Optional.ofNullable((T) this.parent);
    }

    @Override
    public <T extends ContainerHolder> @Nullable T setParent(final @Nullable T parent) {
        this.parent = parent;
        this.root = null;

        this.updateTags();
        if (parent != null) this.root = parent.<T>root().orElse(null);

        return parent;
    }

    @Override
    public void updateTags() {
        this.tags = ImmutableMap.<String, String>builder()
                .putAll(this.parent == null ? Maps.newHashMap() : this.parent.tags())
                .putAll(this.localTags)
                .build();
    }

    @Override
    public @NonNull MetaHolder addTag(final @NonNull String label, final @NonNull String value) {
        this.localTags.put(label, value);
        this.updateTags();

        this.items.forEach(item -> {
            if (item instanceof ContainerHolder) ((ContainerHolder) item).updateTags();
        });

        return this;
    }

    @Override
    public @NonNull MetaHolder addLabel(final @NonNull String label) {
        this.localTags.put(label, "");
        this.updateTags();

        this.items.forEach(item -> {
            if (item instanceof ContainerHolder) ((ContainerHolder) item).updateTags();
        });

        return this;
    }

    @Override
    public boolean contains(final @NonNull String label) {
        return this.tags().containsKey(label);
    }

    @Override
    public boolean contains(final @NonNull String label, final @NonNull String value) {
        final ImmutableMap<String, String> requiredTags = this.tags();
        return requiredTags.containsKey(label) && requiredTags.get(label).equals(value);
    }

    @Override
    public <T extends MetaHolder> boolean containsAny(final @NonNull T other) {
        return other.tags().entrySet().stream()
                .anyMatch(test -> this.contains(test.getKey(), test.getValue()));
    }

    @Override
    public <T extends MetaHolder> boolean containsAll(final @NonNull T other) {
        return other.tags().entrySet().stream()
                .allMatch(test -> this.contains(test.getKey(), test.getValue()));
    }

    @Override
    public @NonNull ImmutableMap<String, String> tags() {
        return this.tags;
    }

    @Override
    public boolean hasChildren() {
        return this.items.stream()
                .anyMatch(metaHolder -> metaHolder instanceof StateContainer);
    }

    @Override
    public int size() {
        return this.items.size();
    }

    @Override
    public void clear() {
        this.items.forEach(metaHolder -> {
            if (metaHolder instanceof StateContainer) ((StateContainer) metaHolder).setParent(null);
            else if (metaHolder instanceof State<?>) ((State<?>) metaHolder).setParent(null);
        });

        this.items.clear();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.root, this.parent, this.tags, this.items);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (!(other instanceof StateContainer)) return false;
        final StateContainer that = (StateContainer) other;
        return Objects.equals(this.root, that.root)
                && Objects.equals(this.parent, that.parent)
                && Objects.equals(this.tags, that.tags)
                && Objects.equals(this.items, that.items);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("root", this.root)
                .add("parent", this.parent)
                .add("tags", this.tags)
                .add("items", this.items)
                .toString();
    }

    public static class Builder {

        private final Map<String, String> tags = new HashMap<>();

        private Builder() {}

        public @NonNull Builder of(final @NonNull MetaHolder other) {
            this.tags.putAll(other.tags());
            return this;
        }

        public @NonNull Builder label(final @NonNull String label) {
            this.tags.put(label, "");
            return this;
        }

        public @NonNull Builder tag(final @NonNull String label, final @NonNull String value) {
            this.tags.put(label, value);
            return this;
        }

        public @NonNull StateContainer build() {
            return new StateContainer(this);
        }

    }

}