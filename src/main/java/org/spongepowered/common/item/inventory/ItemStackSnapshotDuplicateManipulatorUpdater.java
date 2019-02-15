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
package org.spongepowered.common.item.inventory;

import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.Queries;
import org.spongepowered.api.data.persistence.DataContentUpdater;
import org.spongepowered.common.data.persistence.NbtTranslator;
import org.spongepowered.common.data.util.DataQueries;
import org.spongepowered.common.data.util.DataVersions;
import org.spongepowered.common.data.util.NbtDataUtil;

public final class ItemStackSnapshotDuplicateManipulatorUpdater implements DataContentUpdater {

    @Override
    public int getInputVersion() {
        return DataVersions.ItemStackSnapshot.DUPLICATE_MANIPULATOR_DATA_VERSION;
    }

    @Override
    public int getOutputVersion() {
        return DataVersions.ItemStackSnapshot.REMOVED_DUPLICATE_DATA;
    }

    @Override
    public DataView update(DataView content) {
        if (content.contains(DataQueries.UNSAFE_NBT)) {
            NBTTagCompound compound = NbtTranslator.getInstance().translateData(content.getView(DataQueries.UNSAFE_NBT).get());
            if (compound.contains(NbtDataUtil.SPONGE_DATA)) {
                final NBTTagCompound spongeCompound = compound.getCompound(NbtDataUtil.SPONGE_DATA);
                if (spongeCompound.contains(NbtDataUtil.CUSTOM_MANIPULATOR_TAG_LIST)) {
                    spongeCompound.remove(NbtDataUtil.CUSTOM_MANIPULATOR_TAG_LIST);
                }
            }
            NbtDataUtil.filterSpongeCustomData(compound);
            content.remove(DataQueries.UNSAFE_NBT);
            if (!compound.isEmpty()) {
                content.set(DataQueries.UNSAFE_NBT, NbtTranslator.getInstance().translate(compound));
            }
        }
        content.set(Queries.CONTENT_VERSION, this.getOutputVersion());
        return content;
    }
}
