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
package org.spongepowered.common.data.manipulator.mutable.entity;

import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.ImmutablePickupDelayData;
import org.spongepowered.api.data.manipulator.mutable.PickupDelayData;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.api.data.value.BoundedValue;
import org.spongepowered.common.data.manipulator.immutable.entity.ImmutableSpongePickupDelayData;
import org.spongepowered.common.data.manipulator.mutable.common.AbstractIntData;
import org.spongepowered.common.data.util.DataConstants;
import org.spongepowered.common.data.value.SpongeValueFactory;
import org.spongepowered.common.data.value.SpongeMutableValue;

public final class SpongePickupDelayData extends AbstractIntData<PickupDelayData, ImmutablePickupDelayData> implements PickupDelayData {

    public SpongePickupDelayData() {
        this(DataConstants.Entity.Item.DEFAULT_PICKUP_DELAY);
    }

    public SpongePickupDelayData(int value, int minimum, int maximum, int defaultValue) {
        this(value);
    }

    public SpongePickupDelayData(int value) {
        super(PickupDelayData.class, value, Keys.PICKUP_DELAY);
    }

    @Override
    public BoundedValue.Mutable<Integer> delay() {
        return SpongeValueFactory.boundedBuilder(Keys.PICKUP_DELAY) // this.usedKey does not work here
                .value(this.getValue())
                .minimum(DataConstants.Entity.Item.MIN_PICKUP_DELAY)
                .maximum(DataConstants.Entity.Item.MAX_PICKUP_DELAY)
                .build();
    }

    @Override
    public DataContainer toContainer() {
        return super.toContainer()
            .set(Keys.INFINITE_PICKUP_DELAY, isInifinitePickup());
    }

    @Override
    public Value.Mutable<Boolean> infinite() {
        return new SpongeMutableValue<>(Keys.INFINITE_PICKUP_DELAY, isInifinitePickup());
    }

    private boolean isInifinitePickup() {
        return this.getValue() == DataConstants.Entity.Item.MAGIC_NO_PICKUP;
    }

    @Override
    protected Value.Mutable<?> getValueGetter() {
        return this.delay();
    }

    @Override
    public ImmutablePickupDelayData asImmutable() {
        return new ImmutableSpongePickupDelayData(this.getValue());
    }

}
