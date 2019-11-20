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

import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.common.item.inventory.PropertyEntry;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.adapter.impl.BasicInventoryAdapter;
import org.spongepowered.common.item.inventory.lens.impl.slot.CompoundSlotProvider;
import org.spongepowered.common.item.inventory.fabric.Fabric;
import org.spongepowered.common.item.inventory.lens.Lens;
import org.spongepowered.common.item.inventory.lens.impl.slot.SlotLensProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A compound-lens composed of multiple lenses.
 * Only contains slot-lenses.
 */
public class CompoundLens extends SlotBasedLens {

    protected final List<Lens> inventories;

    private CompoundLens(int size, Class<? extends Inventory> adapterType, SlotLensProvider slots, List<Lens> lenses) {
        super(0, size, 1, adapterType, slots);
        this.inventories = lenses;
        this.init(slots);
    }

    protected void init(SlotLensProvider slots) {

        // Adding slots
        for (int ord = 0, slot = this.base; ord < this.size; ord++, slot++) {
            if (!this.children.contains(slots.getSlotLens(slot))) {
                this.addSpanningChild(slots.getSlotLens(slot), PropertyEntry.slotIndex(ord));
            }
        }
    }

    @Override
    public InventoryAdapter getAdapter(Fabric fabric, Inventory parent) {
        return new BasicInventoryAdapter(fabric, this, parent);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<Lens> lenses = new ArrayList<>();

        @SuppressWarnings({"rawtypes", "unchecked"})
        public Builder add(Lens lens) {
            this.lenses.add(lens);
            return this;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public CompoundLens build(CompoundSlotProvider provider) {
            return new CompoundLens(provider.size(), BasicInventoryAdapter.class, provider, this.lenses);
        }
    }
}
