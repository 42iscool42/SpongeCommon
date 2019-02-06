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
package org.spongepowered.common.mixin.core.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockConcretePowder;
import net.minecraft.item.EnumDyeColor;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.interfaces.block.IMixinDyedBlock;
import org.spongepowered.common.text.translation.SpongeTranslation;

@Mixin(BlockConcretePowder.class)
public abstract class MixinBlockConcretePowder extends MixinBlock implements IMixinDyedBlock {

    private EnumDyeColor color;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Block block, Block.Builder builder, CallbackInfo ci) {
        for (EnumDyeColor color : EnumDyeColor.values()) {
            if (this.blockMapColor == color.getMapColor()) {
                this.color = color;
                break;
            }
        }
    }

    @Override
    public EnumDyeColor getDyeColor() {
        return this.color;
    }

    @Override
    public Translation getTranslation() {
        return new SpongeTranslation(getTranslationKey() + ".white.name");
    }
}
