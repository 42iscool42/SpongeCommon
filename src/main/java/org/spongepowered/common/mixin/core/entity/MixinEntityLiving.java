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
package org.spongepowered.common.mixin.core.entity;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.ai.Goal;
import org.spongepowered.api.entity.ai.GoalTypes;
import org.spongepowered.api.entity.ai.task.AITask;
import org.spongepowered.api.entity.living.Agent;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.entity.LeashEntityEvent;
import org.spongepowered.api.event.entity.UnleashEntityEvent;
import org.spongepowered.api.event.entity.ai.AITaskEvent;
import org.spongepowered.api.event.entity.ai.SetAITargetEvent;
import org.spongepowered.api.item.inventory.Carrier;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.data.VanishingBridge;
import org.spongepowered.common.bridge.entity.GrieferBridge;
import org.spongepowered.common.bridge.entity.ai.EntityGoalBridge;
import org.spongepowered.common.bridge.inventory.TrackedInventoryBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.interfaces.ai.IMixinEntityAITasks;

import java.util.Iterator;

import javax.annotation.Nullable;

@Mixin(EntityLiving.class)
public abstract class MixinEntityLiving extends MixinEntityLivingBase {

    @Shadow @Final protected EntityAITasks tasks;
    @Shadow @Final protected EntityAITasks targetTasks;
    @Shadow @Nullable private EntityLivingBase attackTarget;
    @Shadow @Final private NonNullList<ItemStack> inventoryArmor;
    @Shadow @Final private NonNullList<ItemStack> inventoryHands;

    @Shadow public abstract boolean isAIDisabled();
    @Shadow @Nullable public abstract net.minecraft.entity.Entity getLeashHolder();
    @Shadow protected abstract void initEntityAI();
    @Shadow public abstract void setItemStackToSlot(EntityEquipmentSlot slotIn, net.minecraft.item.ItemStack stack);

    @Nullable private Inventory impl$inventoryAdapter = null;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLiving;initEntityAI()V"))
    private void spongeImpl$initializeAI(final EntityLiving this$0) {
        this.initSpongeAI();
        this.initEntityAI();
    }

    private void initSpongeAI() {
        if (!((IMixinEntityAITasks) this.tasks).initialized()) {
            ((IMixinEntityAITasks) this.tasks).setOwner((EntityLiving) (Object) this);
            ((IMixinEntityAITasks) this.tasks).setType(GoalTypes.NORMAL);
            ((IMixinEntityAITasks) this.tasks).setInitialized(true);
        }
        if (!((IMixinEntityAITasks) this.targetTasks).initialized()) {
            ((IMixinEntityAITasks) this.targetTasks).setOwner((EntityLiving) (Object) this);
            ((IMixinEntityAITasks) this.targetTasks).setType(GoalTypes.TARGET);
            ((IMixinEntityAITasks) this.targetTasks).setInitialized(true);
        }
    }

    @Override
    public void firePostConstructEvents() {
        super.firePostConstructEvents();
        if (ShouldFire.A_I_TASK_EVENT_ADD) {
            handleDelayedTaskEventFiring((IMixinEntityAITasks) this.tasks);
            handleDelayedTaskEventFiring((IMixinEntityAITasks) this.targetTasks);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleDelayedTaskEventFiring(final IMixinEntityAITasks tasks) {
        final Iterator<EntityAITasks.EntityAITaskEntry> taskItr = tasks.getTasksUnsafe().iterator();
        while (taskItr.hasNext()) {
            final EntityAITasks.EntityAITaskEntry task = taskItr.next();
            final AITaskEvent.Add event = SpongeEventFactory.createAITaskEventAdd(Sponge.getCauseStackManager().getCurrentCause(),
                    task.priority, task.priority, (Goal<? extends Agent>) tasks, (Agent) this, (AITask<?>) task.action);
            SpongeImpl.postEvent(event);
            if (event.isCancelled()) {
                ((EntityGoalBridge) task.action).setGoal(null);
                taskItr.remove();
            }
        }
    }

    @Inject(method = "processInitialInteract", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLiving;setLeashHolder(Lnet/minecraft/entity/Entity;Z)V"))
    private void callLeashEvent(final EntityPlayer playerIn, final EnumHand hand, final CallbackInfoReturnable<Boolean> ci) {
        if (!playerIn.world.isRemote) {
            Sponge.getCauseStackManager().pushCause(playerIn);
            final LeashEntityEvent event = SpongeEventFactory.createLeashEntityEvent(Sponge.getCauseStackManager().getCurrentCause(), (Living) this);
            SpongeImpl.postEvent(event);
            Sponge.getCauseStackManager().popCause();
            if(event.isCancelled()) {
                ci.setReturnValue(false);
            }
        }
    }

    @Inject(method = "clearLeashed",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLiving;isLeashed:Z", opcode = Opcodes.PUTFIELD),
        cancellable = true)
    private void impl$ThrowUnleashEvent(final boolean sendPacket, final boolean dropLead, final CallbackInfo ci) {
        final net.minecraft.entity.Entity entity = getLeashHolder();
        if (!this.world.isRemote) {
            final CauseStackManager csm = Sponge.getCauseStackManager();
            if(entity == null) {
                csm.pushCause(this);
            } else {
                csm.pushCause(entity);
            }
            final UnleashEntityEvent event = SpongeEventFactory.createUnleashEntityEvent(csm.getCurrentCause(), (Living) this);
            SpongeImpl.postEvent(event);
            csm.popCause();
            if(event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @ModifyConstant(method = "despawnEntity", constant = @Constant(doubleValue = 16384.0D))
    private double getHardDespawnRange(final double value) {
        if (!this.world.isRemote) {
            return Math.pow(((WorldInfoBridge) this.world.getWorldInfo()).getConfigAdapter().getConfig().getEntity().getHardDespawnRange(), 2);
        }
        return value;
    }

    // Note that this should inject twice.
    @ModifyConstant(method = "despawnEntity", constant = @Constant(doubleValue = 1024.0D), expect = 2)
    private double getSoftDespawnRange(final double value) {
        if (!this.world.isRemote) {
            return Math.pow(((WorldInfoBridge) this.world.getWorldInfo()).getConfigAdapter().getConfig().getEntity().getSoftDespawnRange(), 2);
        }
        return value;
    }

    @ModifyConstant(method = "despawnEntity", constant = @Constant(intValue = 600))
    private int getMinimumLifetime(final int value) {
        if (!this.world.isRemote) {
            return ((WorldInfoBridge) this.world.getWorldInfo()).getConfigAdapter().getConfig().getEntity().getMinimumLife() * 20;
        }
        return value;
    }

    @Nullable
    @Redirect(
        method = "despawnEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getClosestPlayerToEntity(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/EntityPlayer;"))
    private EntityPlayer spongeImpl$despa(final World world, final net.minecraft.entity.Entity entity, final double distance) {
        return ((WorldBridge) world).getClosestPlayerToEntityWhoAffectsSpawning(entity, distance);
    }

    /**
     * @author gabizou - January 4th, 2016
     *
     * This is to instill the check that if the entity is vanish, check whether they're untargetable
     * as well.
     *
     * @param entitylivingbaseIn The entity living base coming in
     */
    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true)
    private void onSetAttackTarget(@Nullable final EntityLivingBase entitylivingbaseIn, final CallbackInfo ci) {
        if (!this.world.isRemote && ShouldFire.SET_A_I_TARGET_EVENT) {
            if (entitylivingbaseIn != null) {
                if (((VanishingBridge) entitylivingbaseIn).vanish$isVanished() && ((VanishingBridge) entitylivingbaseIn).vanish$isUntargetable()) {
                    this.attackTarget = null;
                    ci.cancel();
                } else {
                    final SetAITargetEvent event = SpongeCommonEventFactory.callSetAttackTargetEvent((Entity) entitylivingbaseIn, (Agent) this);
                    if (event.isCancelled()) {
                        ci.cancel();
                    } else {
                        this.attackTarget = ((EntityLivingBase) event.getTarget().orElse(null));
                    }
                }
            }
        }
    }

    /**
     * @author gabizou - January 4th, 2016
     * @reason This will still check if the current attack target
     * is vanish and is untargetable.
     *
     * @return The current attack target, if not null
     */
    @Nullable
    @Overwrite
    public EntityLivingBase getAttackTarget() {
        if (this.attackTarget != null) {
            if (((VanishingBridge) this.attackTarget).vanish$isVanished() && ((VanishingBridge) this.attackTarget).vanish$isUntargetable()) {
                this.attackTarget = null;
            }
        }
        return this.attackTarget;
    }

    /**
     * @author gabizou - April 11th, 2018
     * @reason Instead of redirecting the gamerule request, redirecting the dead check
     * to avoid compatibility issues with Forge's change of the gamerule check to an
     * event check that doesn't exist in sponge except in the case of griefing data.
     *
     * @param thisEntity
     * @return
     */
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLiving;canPickUpLoot()Z"))
    private boolean onCanGrief(final EntityLiving thisEntity) {
        return thisEntity.canPickUpLoot() && ((GrieferBridge) this).bridge$CanGrief();
    }


    @Override
    public void onJoinWorld() {
        this.initSpongeAI();
    }

    @Inject(method = "updateEquipmentIfNeeded", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/EntityEquipmentSlot;getSlotType()Lnet/minecraft/inventory/EntityEquipmentSlot$Type;"))
    private void onUpdateEquipmentIfNeeded(final EntityItem itemEntity, final CallbackInfo ci) {
        if (!SpongeCommonEventFactory.callChangeInventoryPickupPreEvent(((EntityLiving)(Object) this), itemEntity)) {
            ci.cancel();
        }
    }

    @Redirect(method = "updateEquipmentIfNeeded", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLiving;setItemStackToSlot(Lnet/minecraft/inventory/EntityEquipmentSlot;Lnet/minecraft/item/ItemStack;)V"))
    private void onSetItemStackToSlot(final EntityLiving thisEntity, final EntityEquipmentSlot slotIn, final net.minecraft.item.ItemStack stack) {
        final int prev = stack.getCount();
        thisEntity.setItemStackToSlot(slotIn, stack);
        // TODO capture pickupevent transaction
        if (!SpongeCommonEventFactory.callChangeInventoryPickupEvent(thisEntity, (TrackedInventoryBridge) ((Carrier) this).getInventory())) {
            stack.setCount(prev);
        }
    }

}
