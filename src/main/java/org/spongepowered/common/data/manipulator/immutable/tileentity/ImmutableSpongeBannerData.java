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
package org.spongepowered.common.data.manipulator.immutable.tileentity;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.ImmutableBannerData;
import org.spongepowered.api.data.manipulator.mutable.BannerData;
import org.spongepowered.api.data.meta.PatternLayer;
import org.spongepowered.api.data.type.DyeColor;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.common.data.manipulator.immutable.common.AbstractImmutableData;
import org.spongepowered.common.data.manipulator.mutable.tileentity.SpongeBannerData;
import org.spongepowered.common.data.value.SpongeImmutableListValue;
import org.spongepowered.common.data.value.SpongeImmutableValue;

import java.util.List;

public class ImmutableSpongeBannerData extends AbstractImmutableData<ImmutableBannerData, BannerData> implements ImmutableBannerData {

    private final DyeColor base;
    private final List<PatternLayer> layers;

    private final Value.Immutable<DyeColor> baseValue;
    private final SpongeImmutableListValue<PatternLayer> layersValue;

    public ImmutableSpongeBannerData(DyeColor base, List<PatternLayer> layers) {
        super(ImmutableBannerData.class);
        this.base = checkNotNull(base, "Null base!");
        this.layers = ImmutableList.copyOf(checkNotNull(layers, "Null pattern list!"));
        this.baseValue = SpongeImmutableValue.cachedOf(Keys.BANNER_BASE_COLOR, this.base);
        this.layersValue = new SpongeImmutableListValue<>(Keys.BANNER_PATTERNS, this.layers);
        registerGetters();
    }

    public DyeColor getBase() {
        return this.base;
    }

    public List<PatternLayer> getLayers() {
        return this.layers;
    }

    @Override
    protected void registerGetters() {
        registerFieldGetter(Keys.BANNER_BASE_COLOR, this::getBase);
        registerKeyValue(Keys.BANNER_BASE_COLOR, this::baseColor);

        registerFieldGetter(Keys.BANNER_PATTERNS, this::getLayers);
        registerKeyValue(Keys.BANNER_PATTERNS, this::patterns);
    }

    @Override
    public Value.Immutable<DyeColor> baseColor() {
        return this.baseValue;
    }

    @Override
    public SpongeImmutableListValue<PatternLayer> patterns() {
        return this.layersValue;
    }

    @Override
    public BannerData asMutable() {
        return new SpongeBannerData(this.base, this.layers);
    }

    @Override
    public DataContainer toContainer() {
        return super.toContainer()
                .set(Keys.BANNER_BASE_COLOR.getQuery(), this.base.getKey())
                .set(Keys.BANNER_PATTERNS, this.layers);
    }
}
