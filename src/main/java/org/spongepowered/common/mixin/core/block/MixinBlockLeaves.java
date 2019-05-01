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

import com.google.common.collect.ImmutableList;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.ImmutableDataManipulator;
import org.spongepowered.api.data.manipulator.immutable.ImmutableDecayableData;
import org.spongepowered.api.data.manipulator.immutable.ImmutableTreeData;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.LocatableBlock;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.data.ImmutableDataCachingUtil;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeDecayableData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeTreeData;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseData;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.TrackingPhases;
import org.spongepowered.common.event.tracking.phase.block.BlockPhase;
import org.spongepowered.common.interfaces.world.IMixinWorld_Impl;
import org.spongepowered.common.registry.type.block.TreeTypeRegistryModule;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@NonnullByDefault
@Mixin(BlockLeaves.class)
public abstract class MixinBlockLeaves extends MixinBlock {

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void onLeavesConstruction(CallbackInfo ci) {
        this.setUpdateRandomly(SpongeImpl.getGlobalConfig().getConfig().getWorld().getLeafDecay());
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;I)Z"))
    private boolean onUpdateDecayState(net.minecraft.world.World worldIn, BlockPos pos, IBlockState state, int flags) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final boolean isBlockAlready = phaseTracker.getCurrentState().getPhase() != TrackingPhases.BLOCK;
        final IPhaseState<?> currentState = phaseTracker.getCurrentPhaseData().state;
        final boolean isWorldGen = currentState.isWorldGeneration();
        try (PhaseContext<?> context = isBlockAlready && !isWorldGen
                                       ? BlockPhase.State.BLOCK_DECAY.createPhaseContext()
                                           .source(LocatableBlock.builder()
                                               .location(new Location((World) worldIn, pos.getX(), pos.getY(), pos.getZ()))
                                               .state((BlockState) state)
                                               .build())
                                       : null) {
            if (context != null) {
                context.buildAndSwitch();
            }
            return worldIn.setBlockState(pos, state, flags);
        }
    }

    /**
     * @author gabizou - August 2nd, 2016
     * @reason Rewrite to handle both drops and the change state for leaves
     * that are considered to be decaying, so the drops do not leak into
     * whatever previous phase is being handled in. Since the issue is that
     * the block change takes place in a different phase (more than likely),
     * the drops are either "lost" or not considered for drops because the
     * blocks didn't change according to whatever previous phase.
     *
     * @param worldIn The world in
     * @param pos The position
     */
    @Overwrite
    public void randomTick(IBlockState state, net.minecraft.world.World worldIn, BlockPos pos, Random random) {
        if (!state.get(BlockLeaves.PERSISTENT) && state.get(BlockLeaves.DISTANCE) == 7) {
            // Sponge Start - Cause tracking
            if (!((IMixinWorld_Impl) worldIn).isFake()) {
                final PhaseTracker phaseTracker = PhaseTracker.getInstance();
                final PhaseData peek = phaseTracker.getCurrentPhaseData();
                final IPhaseState<?> currentState = peek.state;
                final boolean isWorldGen = currentState.isWorldGeneration();
                final boolean isBlockAlready = phaseTracker.getCurrentState().getPhase() != TrackingPhases.BLOCK;
                try (PhaseContext<?> context = isBlockAlready && !isWorldGen ? BlockPhase.State.BLOCK_DECAY.createPhaseContext()
                        .source(LocatableBlock.builder()
                                .location(new Location((World) worldIn, pos.getX(), pos.getY(), pos.getZ()))
                                .state((BlockState) state)
                                .build()) : null) {
                    if (context != null) {
                        context.buildAndSwitch();
                    }
                    state.dropBlockAsItem(worldIn, pos, 0);
                    worldIn.removeBlock(pos);
                }
                return;
            }
            // Sponge End
            state.dropBlockAsItem(worldIn, pos, 0);
            worldIn.removeBlock(pos);
        }
    }

    private ImmutableTreeData getTreeData(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeTreeData.class, TreeTypeRegistryModule.getTreeType(blockState));
    }

    private ImmutableDecayableData getIsDecayableFor(IBlockState blockState) {
        // TODO: Update data
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeDecayableData.class, blockState.get(BlockLeaves.PERSISTENT));
    }

    @Override
    public boolean supports(Class<? extends ImmutableDataManipulator<?, ?>> immutable) {
        return ImmutableTreeData.class.isAssignableFrom(immutable) || ImmutableDecayableData.class.isAssignableFrom(immutable);
    }

    @Override
    public Optional<BlockState> getStateWithData(IBlockState blockState, ImmutableDataManipulator<?, ?> manipulator) {
        if (manipulator instanceof ImmutableDecayableData) {
            final ImmutableDecayableData decayableData = (ImmutableDecayableData) manipulator;
            final int distance = decayableData.get(Keys.DECAY_DISTANCE).get();
            final boolean persistent = decayableData.get(Keys.PERSISTENT).get();
            return Optional.of((BlockState) blockState
                    .with(BlockLeaves.DISTANCE, distance)
                    .with(BlockLeaves.PERSISTENT, persistent));
        }
        return super.getStateWithData(blockState, manipulator);
    }

    @Override
    public <E> Optional<BlockState> getStateWithValue(IBlockState blockState, Key<? extends Value<E>> key, E value) {
        if (key.equals(Keys.DECAY_DISTANCE)) {
            final int distance = (Integer) value;
            return Optional.of((BlockState) blockState.with(BlockLeaves.DISTANCE, distance));
        } else if (key.equals(Keys.PERSISTENT)) {
            final boolean persistent = (Boolean) value;
            return Optional.of((BlockState) blockState.with(BlockLeaves.PERSISTENT, persistent));
        }
        return super.getStateWithValue(blockState, key, value);
    }

    @Override
    public List<ImmutableDataManipulator<?, ?>> getManipulators(IBlockState blockState) {
        return ImmutableList.of(getTreeData(blockState), getIsDecayableFor(blockState));

    }

}
