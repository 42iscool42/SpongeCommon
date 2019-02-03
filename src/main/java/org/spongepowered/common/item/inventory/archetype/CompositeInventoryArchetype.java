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
package org.spongepowered.common.item.inventory.archetype;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.data.property.Property;
import org.spongepowered.api.item.inventory.InventoryArchetype;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import javax.annotation.Nullable;

public class CompositeInventoryArchetype implements InventoryArchetype {

    private final CatalogKey key;
    private final String name;
    private final List<InventoryArchetype> types;
    private final Map<Property<?>, ?> properties;
    @Nullable private ContainerProvider containerProvider;

    public CompositeInventoryArchetype(String id, String name, List<InventoryArchetype> types,
            Map<Property<?>, ?> properties, @Nullable ContainerProvider containerProvider) {
        this.key = CatalogKey.resolve(id);
        this.name = name;
        this.types = ImmutableList.copyOf(types);
        this.properties = ImmutableMap.copyOf(properties);
        this.containerProvider = containerProvider;
    }

    @Override
    public CatalogKey getKey() {
        return this.key;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public List<InventoryArchetype> getChildArchetypes() {
        return this.types;
    }

    @Override
    public <V> Optional<V> getProperty(Property<V> property) {
        return Optional.ofNullable((V) this.properties.get(property));
    }

    @Override
    public OptionalInt getIntProperty(Property<Integer> property) {
        final Integer value = (Integer) this.properties.get(property);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    @Override
    public OptionalDouble getDoubleProperty(Property<Double> property) {
        final Double value = (Double) this.properties.get(property);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @Override
    public Map<Property<?>, ?> getProperties() {
        return this.properties;
    }

    @Nullable public ContainerProvider getContainerProvider() {
        return this.containerProvider;
    }

    /**
     * Provides a {@link Container} for a {@link EntityPlayer} viewing an {@link IInventory}
     */
    public interface ContainerProvider {
        Container provide(IInventory viewed, EntityPlayer viewing);
    }

}
