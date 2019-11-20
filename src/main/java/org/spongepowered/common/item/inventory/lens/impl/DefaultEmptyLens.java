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
package org.spongepowered.common.item.inventory.lens.impl;

import net.minecraft.item.ItemStack;
import org.spongepowered.api.data.property.Property;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.fabric.Fabric;
import org.spongepowered.common.item.inventory.lens.Lens;
import org.spongepowered.common.item.inventory.lens.impl.slot.SlotLens;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class DefaultEmptyLens implements Lens {

    protected final InventoryAdapter adapter;

    public DefaultEmptyLens(final InventoryAdapter adapter) {
        this.adapter = adapter;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends Inventory> getAdapterType() {
        return (Class<? extends Inventory>) this.adapter.getClass();
    }

    @Override
    public InventoryAdapter getAdapter(final Fabric fabric, final Inventory parent) {
        return this.adapter;
    }

    @Override
    public Translation getName(final Fabric fabric) {
        return fabric.fabric$getDisplayName();
    }

    @Override
    public int slotCount() {
        return 0;
    }

    @Override
    public ItemStack getStack(final Fabric fabric, final int ordinal) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean setStack(final Fabric fabric, final int index, final ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxStackSize(final Fabric fabric) {
        return 0;
    }

    @Override
    public List<Lens> getChildren() {
        return Collections.<Lens>emptyList();
    }

    @Override
    public List<Lens> getSpanningChildren() {
        return Collections.<Lens>emptyList();
    }

    @Override
    public Lens getLens(final int index) {
        return null;
    }

    @Override
    public Map<Property<?>, Object> getProperties(int index) {
        return Collections.emptyMap();
    }

    @Override public Map<Property<?>, Object> getProperties(Lens lens) {
        return Collections.emptyMap();
    }

    @Override
    public boolean has(final Lens lens) {
        return false;
    }

    @Override
    public boolean isSubsetOf(final Collection<Lens> c) {
        return true;
    }

    @Override
    public Lens getParent() {
        return null;
    }

    @Override
    public List<Lens> children() {
        return Collections.emptyList()
    }

    @Override
    public List<SlotLens> getSlots() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public SlotLens getSlotLens(int ordinal) {
        return null;
    }

    @Override
    public String toString(int deep) {
        return "EmptyLens";
    }
}
