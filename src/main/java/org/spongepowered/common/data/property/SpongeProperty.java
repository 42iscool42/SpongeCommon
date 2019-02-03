/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.data.property;

import com.google.common.base.MoreObjects;
import com.google.common.reflect.TypeToken;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.data.property.Property;

import java.util.Comparator;
import java.util.function.BiPredicate;

public class SpongeProperty<V> implements Property<V> {

    private final CatalogKey key;
    private final TypeToken<V> valueType;
    private final Comparator<V> valueComparator;
    private final BiPredicate<V, V> includesTester;

    SpongeProperty(CatalogKey key, TypeToken<V> valueType, Comparator<V> valueComparator, BiPredicate<V, V> includesTester) {
        this.key = key;
        this.valueType = valueType;
        this.valueComparator = valueComparator;
        this.includesTester = includesTester;
    }

    @Override
    public TypeToken<V> getValueType() {
        return this.valueType;
    }

    @Override
    public Comparator<V> getValueComparator() {
        return this.valueComparator;
    }

    @Override
    public BiPredicate<V, V> getValueIncludesTester() {
        return this.includesTester;
    }

    @Override
    public CatalogKey getKey() {
        return this.key;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("key", this.key)
                .add("valueType", this.valueType)
                .toString();
    }
}
