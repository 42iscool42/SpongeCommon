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
package org.spongepowered.common.item.inventory.adapter.impl;

import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult.Type;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.adapter.impl.slots.SlotAdapter;
import org.spongepowered.common.item.inventory.fabric.Fabric;
import org.spongepowered.common.item.inventory.lens.Lens;
import org.spongepowered.common.item.inventory.lens.impl.slot.SlotLens;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;

import java.util.Optional;

import javax.annotation.Nullable;

public abstract class AdapterLogic {

    private AdapterLogic() {
    }

    public static InventoryTransactionResult.Poll pollSequential(Fabric fabric, @Nullable Lens lens, @Nullable Integer limit) {
        if (lens == null || lens.getSlots().size() <= 0) {
            return InventoryTransactionResult.builder().type(Type.NO_SLOT).poll(ItemStackSnapshot.empty()).build();
        }

        InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(Type.SUCCESS);

        ItemStack removedType = null; // used when polling from multiple slots
        int totalPolled = 0;

        for (SlotLens slot : lens.getSlots()) {
            net.minecraft.item.ItemStack stack = slot.getStack(fabric);

            // Only remove one type of item
            if (stack.isEmpty() || (removedType != null && !ItemStackUtil.compareIgnoreQuantity(removedType, stack))) {
                continue;
            }

            // Poll up to limit items OR entire stack when no limit is set
            int pollCount = limit != null ? Math.min(stack.getCount(), limit) : stack.getCount();

            net.minecraft.item.ItemStack newStack = net.minecraft.item.ItemStack.EMPTY;
            if (pollCount < stack.getCount()) { // is stack not removed completely?
                newStack = stack.copy();
                newStack.setCount(newStack.getCount() - pollCount);
            }

            if (slot.setStack(fabric, newStack)) { // Set new stack
                SlotAdapter slotAdapter = (SlotAdapter) slot.getAdapter(fabric, null); // TODO parent??
                result.transaction(new SlotTransaction(slotAdapter, ItemStackUtil.snapshotOf(stack), ItemStackUtil.snapshotOf(newStack)));
                if (removedType == null) {
                    removedType = ItemStackUtil.cloneDefensive(stack, 1); // set removed type when first removing
                }
                if (limit == null) {
                    totalPolled = pollCount;
                    break; // no limit only polls the first non-empty slot
                }
                limit -= pollCount; // remove amount polled from slot
                totalPolled += pollCount;
            }
            // else setting stack failed - do nothing

            if (limit != null && limit <= 0) { // polled all items requested
                break;
            }
        }

        if (removedType != null) { // mark dirty if items were removed
            fabric.fabric$markDirty();
        }

        if (limit != null && limit > 0) { // not all items requested could be polled
            result.type(Type.FAILURE);
        }

        if (removedType == null) {
            removedType = ItemStack.empty();
        } else {
            removedType.setQuantity(totalPolled);
        }

        return result.poll(removedType.createSnapshot()).build();
    }

    public static Optional<ItemStack> peekSequential(Fabric fabric, @Nullable Lens lens) {
        return AdapterLogic.findStack(fabric, lens);
    }

    private static Optional<ItemStack> findStack(Fabric fabric, @Nullable Lens lens) {
        if (lens == null || lens.getSlots().size() <= 0) {
            return Optional.empty();
        }

        for (SlotLens slot : lens.getSlots()) {
            net.minecraft.item.ItemStack stack = slot.getStack(fabric);
            if (!stack.isEmpty()) {
                return ItemStackUtil.cloneDefensiveOptional(stack);
            }
        }

        if (lens.slotCount() > 0) {
            return Optional.of(ItemStack.empty());
        }

        return Optional.of(ItemStack.empty());
    }

    public static InventoryTransactionResult insertSequential(Fabric fabric, @Nullable Lens lens, ItemStack stack) {
        if (lens == null) {
            return InventoryTransactionResult.builder().type(Type.FAILURE).reject(ItemStackUtil.cloneDefensive(stack)).build();
        }
        try {
            return AdapterLogic.insertStack(fabric, lens, stack);
        } catch (Exception ex) {
            return InventoryTransactionResult.builder().type(Type.ERROR).reject(ItemStackUtil.cloneDefensive(stack)).build();
        }
    }

    private static InventoryTransactionResult insertStack(Fabric fabric, Lens lens, ItemStack stack) {
        InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(Type.SUCCESS);
        net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

        int maxStackSize = Math.min(lens.getMaxStackSize(fabric), nativeStack.getMaxStackSize());
        int remaining = stack.getQuantity();

        for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
            net.minecraft.item.ItemStack old = lens.getStack(fabric, ord);
            ItemStackSnapshot oldSnap = ItemStackUtil.snapshotOf(old);
            int push = Math.min(remaining, maxStackSize);
            net.minecraft.item.ItemStack newStack = ItemStackUtil.cloneDefensiveNative(nativeStack, push);
            if (lens.setStack(fabric, ord, newStack)) {
                InventoryAdapter adapter = lens.getAdapter(fabric, null);
                SlotTransaction trans = new SlotTransaction((Slot) adapter, ItemStackUtil.snapshotOf(old), ItemStackUtil.snapshotOf(newStack));
                result.transaction(trans);
                remaining -= push;

                Slot slot = ((SlotAdapter) lens.getSlotLens(ord).getAdapter(fabric, null));
                result.transaction(new SlotTransaction(slot, oldSnap, ItemStackUtil.snapshotOf(lens.getStack(fabric, ord))));
            }
        }

        if (remaining > 0) {
            result.reject(ItemStackUtil.cloneDefensive(nativeStack, remaining));
        }

        fabric.fabric$markDirty();

        return result.build();
    }

    public static InventoryTransactionResult appendSequential(Fabric fabric, @Nullable Lens lens, ItemStack stack) {
        if (lens == null) {
            return InventoryTransactionResult.builder().type(Type.FAILURE).reject(ItemStackUtil.cloneDefensive(stack)).build();
        }
        InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(Type.SUCCESS);
        net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

        int maxStackSize = Math.min(lens.getMaxStackSize(fabric), nativeStack.getMaxStackSize());
        int remaining = stack.getQuantity();

        for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
            net.minecraft.item.ItemStack old = lens.getStack(fabric, ord);

            int push = Math.min(remaining, maxStackSize);
            if (old.isEmpty() && lens.setStack(fabric, ord, ItemStackUtil.cloneDefensiveNative(nativeStack, push))) {
                remaining -= push;
                Slot slot = ((SlotAdapter) lens.getSlotLens(ord).getAdapter(fabric, null));
                result.transaction(new SlotTransaction(slot, ItemStackUtil.snapshotOf(old), ItemStackUtil.snapshotOf(lens.getStack(fabric, ord))));
            } else if (!old.isEmpty() && ItemStackUtil.compareIgnoreQuantity(old, stack)) {
                ItemStackSnapshot oldSnap = ItemStackUtil.snapshotOf(old);
                push = Math.max(Math.min(maxStackSize - old.getCount(), remaining), 0); // max() accounts for oversized stacks
                old.setCount(old.getCount() + push);
                remaining -= push;
                Slot slot = ((SlotAdapter) lens.getSlotLens(ord).getAdapter(fabric, null));
                result.transaction(new SlotTransaction(slot, oldSnap, ItemStackUtil.snapshotOf(lens.getStack(fabric, ord))));
            }

        }

        if (remaining == stack.getQuantity()) {
            // No items were consumed
            result.type(Type.FAILURE).reject(ItemStackUtil.cloneDefensive(nativeStack));
        } else {
            stack.setQuantity(remaining);
            fabric.fabric$markDirty();
        }

        return result.build();
    }

    public static int countFreeCapacity(Fabric fabric, Lens lens) {
        return lens.getSlots().stream().mapToInt(slot -> slot.getStack(fabric).isEmpty() ? 1 : 0).sum();
    }

    public static int countQuantity(Fabric fabric, Lens lens) {
        int items = 0;

        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack stack = lens.getStack(fabric, ord);
            items += !stack.isEmpty() ? stack.getCount() : 0;
        }

        return items;
    }

    public static int getCapacity(Fabric fabric, Lens lens) {
        return lens.slotCount();
    }

    public static boolean contains(InventoryAdapter adapter, ItemStack stack) {
        return AdapterLogic.contains(adapter.bridge$getFabric(), adapter.bridge$getRootLens(), stack, stack.getQuantity());
    }

    public static boolean contains(InventoryAdapter adapter, ItemStack stack, int quantity) {
        return AdapterLogic.contains(adapter.bridge$getFabric(), adapter.bridge$getRootLens(), stack, quantity);
    }

    /**
     * Searches for at least <code>quantity</code> of given stack.
     *
     * @param fabric The inventory to search in
     * @param lens The lens to search with
     * @param stack The stack to search with
     * @param quantity The quantity to find
     * @return true if at least <code>quantity</code> of given stack has been found in given inventory
     */
    public static boolean contains(Fabric fabric, Lens lens, ItemStack stack, int quantity) {
        net.minecraft.item.ItemStack nonNullStack = ItemStackUtil.toNative(stack); // Handle null as empty
        int found = 0;
        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack slotStack = lens.getStack(fabric, ord);
            if (slotStack.isEmpty()) {
                if (nonNullStack.isEmpty()) {
                    found++; // Found an empty Slot
                    if (found >= quantity) {
                        return true;
                    }
                }
            } else {
                if (ItemStackUtil.compareIgnoreQuantity(slotStack, stack)) {
                    found += slotStack.getCount(); // Found a matching stack
                    if (found >= quantity) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean contains(InventoryAdapter adapter, ItemType type) {
        return AdapterLogic.contains(adapter.bridge$getFabric(), adapter.bridge$getRootLens(), type);
    }

    public static boolean contains(Fabric fabric, Lens lens, ItemType type) {
        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack slotStack = lens.getStack(fabric, ord);
            if (slotStack.isEmpty()) {
                if (type == null || type == ItemTypes.AIR) {
                    return true; // Found an empty Slot
                }
            } else {
                if (slotStack.getItem() == type) {
                    return true; // Found a matching stack
                }
            }
        }
        return false;
    }

    public static boolean canFit(Fabric fabric, Lens lens, ItemStack stack) {
        net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

        int maxStackSize = Math.min(lens.getMaxStackSize(fabric), nativeStack.getMaxStackSize());
        int remaining = stack.getQuantity();

        for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
            net.minecraft.item.ItemStack old = lens.getStack(fabric, ord);
            int push = Math.min(remaining, maxStackSize);
            if (old.isEmpty()) {
                remaining -= push;
            } else if (ItemStackUtil.compareIgnoreQuantity(old, stack)) {
                push = Math.max(Math.min(maxStackSize - old.getCount(), remaining), 0); // max() accounts for oversized stacks
                remaining -= push;
            }
        }

        return remaining == 0;
    }
}
