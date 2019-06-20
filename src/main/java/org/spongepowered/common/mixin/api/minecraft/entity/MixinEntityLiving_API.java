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
package org.spongepowered.common.mixin.api.minecraft.entity;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.mutable.entity.AgentData;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.ai.Goal;
import org.spongepowered.api.entity.ai.GoalType;
import org.spongepowered.api.entity.ai.GoalTypes;
import org.spongepowered.api.entity.living.Agent;
import org.spongepowered.api.item.inventory.Carrier;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.type.CarriedInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeAgentData;
import org.spongepowered.common.data.value.mutable.SpongeValue;
import org.spongepowered.common.item.inventory.custom.LivingInventory;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

@Mixin(EntityLiving.class)
public abstract class MixinEntityLiving_API extends MixinEntityLivingBase_API implements Agent, Carrier {

    @Shadow @Final protected EntityAITasks tasks;
    @Shadow @Final protected EntityAITasks targetTasks;
    @Shadow @Nullable private EntityLivingBase attackTarget;

    @Shadow public abstract boolean isAIDisabled();

    @Shadow @Final private NonNullList<ItemStack> inventoryArmor;
    @Shadow @Final private NonNullList<ItemStack> inventoryHands;

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Agent> Optional<Goal<T>> getGoal(GoalType type) {
        if (GoalTypes.NORMAL.equals(type)) {
            return Optional.of((Goal<T>) this.tasks);
        } else if (GoalTypes.TARGET.equals(type)) {
            return Optional.of((Goal<T>) this.targetTasks);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Entity> getTarget() {
        return Optional.ofNullable((Entity) this.attackTarget);
    }

    @Override
    public void setTarget(@Nullable Entity target) {
        if (target instanceof EntityLivingBase) {
            this.attackTarget = (EntityLivingBase) target;
        } else {
            this.attackTarget = null;
        }
    }



    @Override
    public AgentData getAgentData() {
        return new SpongeAgentData(!this.isAIDisabled());
    }

    @Override
    public Value<Boolean> aiEnabled() {
        return new SpongeValue<>(Keys.AI_ENABLED, true, !this.isAIDisabled());
    }

    @Override
    public void spongeApi$supplyVanillaManipulators(List<? super DataManipulator<?, ?>> manipulators) {
        super.spongeApi$supplyVanillaManipulators(manipulators);
        manipulators.add(getAgentData());
    }

    @SuppressWarnings("unchecked")
    @Override
    public CarriedInventory<? extends Carrier> getInventory() {
        return (CarriedInventory<? extends Carrier>) this;
    }
}
