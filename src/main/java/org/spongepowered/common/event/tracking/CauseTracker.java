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
package org.spongepowered.common.event.tracking;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.effect.EntityWeatherEffect;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.action.LightningEvent;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.gen.PopulatorType;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.entity.PlayerTracker;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.phase.BlockPhase;
import org.spongepowered.common.event.tracking.phase.GeneralPhase;
import org.spongepowered.common.event.tracking.phase.SpawningPhase;
import org.spongepowered.common.event.tracking.phase.TrackingPhase;
import org.spongepowered.common.event.tracking.phase.TrackingPhases;
import org.spongepowered.common.event.tracking.phase.WorldPhase;
import org.spongepowered.common.interfaces.IMixinChunk;
import org.spongepowered.common.interfaces.IMixinMinecraftServer;
import org.spongepowered.common.interfaces.entity.IMixinEntity;
import org.spongepowered.common.interfaces.entity.IMixinEntityLightningBolt;
import org.spongepowered.common.interfaces.entity.IMixinEntityLivingBase;
import org.spongepowered.common.interfaces.world.IMixinWorld;
import org.spongepowered.common.util.SpongeHooks;
import org.spongepowered.common.util.StaticMixinHelper;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.CaptureType;
import org.spongepowered.common.world.SpongeProxyBlockAccess;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import javax.annotation.Nullable;

public final class CauseTracker {

    private final net.minecraft.world.World targetWorld;
    private final List<Entity> capturedEntities = new ArrayList<>();
    private final List<BlockSnapshot> capturedSpongeBlockSnapshots = new ArrayList<>();
    private final Map<PopulatorType, LinkedHashMap<Vector3i, Transaction<BlockSnapshot>>> capturedSpongePopulators = Maps.newHashMap();
    private final List<Transaction<BlockSnapshot>> invalidTransactions = new ArrayList<>();
    private final List<Entity> capturedEntityItems = new ArrayList<>();

    private boolean captureBlocks = false;

    private final TrackingPhases phases = new TrackingPhases();

    @Nullable private BlockSnapshot currentTickBlock;
    @Nullable private Entity currentTickEntity;
    @Nullable private TileEntity currentTickTileEntity;
    @Nullable private Cause pluginCause;
    @Nullable private ICommand command;
    @Nullable private ICommandSender commandSender;

    public CauseTracker(net.minecraft.world.World targetWorld) {
        if (((IMixinWorld) targetWorld).getCauseTracker() != null) {
            throw new IllegalArgumentException("Attempting to create a new CauseTracker for a world that already has a CauseTracker!!");
        }
        this.targetWorld = targetWorld;
    }

    public World getWorld() {
        return (World) this.targetWorld;
    }

    public net.minecraft.world.World getMinecraftWorld() {
        return this.targetWorld;
    }

    public IMixinWorld getMixinWorld() {
        return (IMixinWorld) this.targetWorld;
    }

    public CauseTracker push(ITrackingPhaseState phaseState) {
        this.phases.push(checkNotNull(phaseState, "Phase state cannot be null!"));
        return this;
    }

    public CauseTracker pop() {
        this.phases.pop();
        return this;
    }

    public TrackingPhases getPhases() {
        return this.phases;
    }

    public boolean isCapturing() {
        final ITrackingPhaseState state = this.phases.peek();
        return state != null && state.isBusy();
    }

    public boolean isProcessingBlockRandomTicks() {
        return this.phases.peek() == WorldPhase.State.RANDOM_TICK_BLOCK;
    }

    public boolean isCapturingTerrainGen() {
        return this.phases.peek() == WorldPhase.State.TERRAIN_GENERATION;
    }

    public boolean isCapturingBlocks() {
        return this.captureBlocks;
    }

    public void setCaptureBlocks(boolean captureBlocks) {
        this.captureBlocks = captureBlocks;
    }

    public boolean isCaptureCommand() {
        return this.phases.peek() == GeneralPhase.State.COMMAND;
    }

    public boolean isRestoringBlocks() {
        return this.phases.peek() == BlockPhase.State.RESTORING_BLOCKS;
    }

    public boolean isSpawningDeathDrops() {
        return this.phases.peek() == SpawningPhase.State.DEATH_DROPS_SPAWNING;
    }

    public boolean isWorldSpawnerRunning() {
        return this.phases.peek() == SpawningPhase.State.WORLD_SPAWNER_SPAWNING;
    }

    public boolean isChunkSpawnerRunning() {
        return this.phases.peek() == SpawningPhase.State.CHUNK_SPAWNING;
    }


    public List<Entity> getCapturedEntities() {
        return this.capturedEntities;
    }

    public List<Entity> getCapturedEntityItems() {
        return this.capturedEntityItems;
    }

    public boolean hasTickingBlock() {
        return this.currentTickBlock != null;
    }

    public Optional<BlockSnapshot> getCurrentTickBlock() {
        return Optional.ofNullable(this.currentTickBlock);
    }

    public void setCurrentTickBlock(BlockSnapshot currentTickBlock) {
        checkNotNull(currentTickBlock, "Cannot tick on a null ticking block!");
        switchToPhase(TrackingPhases.GENERAL, WorldPhase.State.TICKING_BLOCK);
        this.currentTickBlock = currentTickBlock;
    }

    public void completeTickingBlock() {
        checkState(this.currentTickBlock != null, "Canot capture on a null ticking block!");
        handlePostTickCaptures(Cause.of(NamedCause.source(this.currentTickBlock)));
        this.currentTickBlock = null;
    }

    public boolean hasTickingEntity() {
        return this.currentTickEntity != null;
    }

    public Optional<Entity> getCurrentTickEntity() {
        return Optional.ofNullable(this.currentTickEntity);
    }

    public void setCurrentTickEntity(Entity currentTickEntity) {
        checkNotNull(currentTickEntity, "Cannot capture on a null ticking entity!");
        switchToPhase(TrackingPhases.GENERAL, WorldPhase.State.TICKING_ENTITY);
        this.currentTickEntity = currentTickEntity;
    }

    public void completeTickingEntity() {
        checkState(this.currentTickEntity != null, "The current ticking entity is null!!!");
        handlePostTickCaptures(Cause.of(NamedCause.source(this.currentTickEntity)));
        this.currentTickEntity = null;
    }

    public boolean hasTickingTileEntity() {
        return this.currentTickTileEntity != null;
    }

    public Optional<TileEntity> getCurrentTickTileEntity() {
        return Optional.ofNullable(this.currentTickTileEntity);
    }

    public void setCurrentTickTileEntity(TileEntity currentTickTileEntity) {
        checkNotNull(currentTickTileEntity, "Cannot capture on a null ticking tile entity!");
        switchToPhase(TrackingPhases.GENERAL, WorldPhase.State.TICKING_TILE_ENTITY);
        this.currentTickTileEntity = currentTickTileEntity;
    }

    public void completeTickingTileEntity() {
        checkState(this.currentTickTileEntity != null, "Current ticking tile entity is null!!!");
        handlePostTickCaptures(Cause.of(NamedCause.source(this.currentTickTileEntity)));
        this.currentTickTileEntity = null;
    }

    public List<BlockSnapshot> getCapturedSpongeBlockSnapshots() {
        return this.capturedSpongeBlockSnapshots;
    }

    public Map<PopulatorType, LinkedHashMap<Vector3i, Transaction<BlockSnapshot>>> getCapturedPopulators() {
        return this.capturedSpongePopulators;
    }

    public Optional<Cause> getPluginCause() {
        return Optional.ofNullable(this.pluginCause);
    }

    public void setPluginCause(@Nullable Cause pluginCause) {
        this.pluginCause = pluginCause;
    }

    public boolean hasPluginCause() {
        return this.pluginCause != null;
    }

    public void handleEntitySpawns(Cause cause) {
        ITrackingPhaseState state = this.phases.pop();
        if (this.capturedEntities.isEmpty() && this.capturedEntityItems.isEmpty()) {
            return; // there's nothing to do.
        }

        if (!(state instanceof SpawningPhase.State)) {
            throw new IllegalArgumentException("Invalid state detected.");
        }

        SpawnEntityEvent event = ((SpawningPhase.State) state).createEntityEvent(cause, getWorld(), this.capturedEntities, this.invalidTransactions);
        if (event == null) {
            return;
        }

        if (!(SpongeImpl.postEvent(event))) {
            Iterator<Entity> iterator = event.getEntities().iterator();
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                if (entity.isRemoved()) { // Entity removed in an event handler
                    iterator.remove();
                    continue;
                }
                net.minecraft.entity.Entity nmsEntity = (net.minecraft.entity.Entity) entity;
                if (nmsEntity instanceof EntityWeatherEffect) {
                    addWeatherEffect(nmsEntity);
                } else {
                    int x = MathHelper.floor_double(nmsEntity.posX / 16.0D);
                    int z = MathHelper.floor_double(nmsEntity.posZ / 16.0D);
                    this.getMinecraftWorld().getChunkFromChunkCoords(x, z).addEntity(nmsEntity);
                    this.getMinecraftWorld().loadedEntityList.add(nmsEntity);
                    this.getMixinWorld().onSpongeEntityAdded(nmsEntity);
                    SpongeHooks.logEntitySpawn(cause, nmsEntity);
                }
                iterator.remove();
            }
        } else {
            this.capturedEntities.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePostTickCaptures(Cause cause) {
        final ITrackingPhaseState phase = this.phases.pop();
        if (this.getMinecraftWorld().isRemote || phase.isManaged()) {
            return;
        } else if (this.capturedEntities.size() == 0 && this.capturedEntityItems.size() == 0 && this.capturedSpongeBlockSnapshots.size() == 0
                   && this.capturedSpongePopulators.size() == 0 && StaticMixinHelper.packetPlayer == null) {
            return; // nothing was captured, return
        }

        EntityPlayerMP player = StaticMixinHelper.packetPlayer;
        Packet<?> packetIn = StaticMixinHelper.processingPacket;

        // Attempt to find a Player cause if we do not have one
        cause = TrackingHelper.identifyCauses(cause, this.capturedSpongeBlockSnapshots, this.getMinecraftWorld());

        // Handle Block Captures
        handleBlockCaptures(cause);

        // Handle Player Toss
        handleToss(player, packetIn);

        // Handle Player kill commands
        cause = handleKill(cause, player, packetIn);

        // Handle Player Entity destruct
        handleEntityDestruct(cause, player, packetIn);

        // Inventory Events
        handleInventoryEvents(player, packetIn, StaticMixinHelper.lastOpenContainer);

        // Handle Entity captures
        if (this.capturedEntityItems.size() > 0) {
            if (StaticMixinHelper.dropCause != null) {
                cause = StaticMixinHelper.dropCause;
                StaticMixinHelper.destructItemDrop = true;
            }
            handleDroppedItems(cause);
        }
        if (this.capturedEntities.size() > 0) {
            handleEntitySpawns(cause);
        }

        StaticMixinHelper.dropCause = null;
        StaticMixinHelper.destructItemDrop = false;
        this.invalidTransactions.clear();
    }

    private void handleToss(@Nullable EntityPlayerMP playerMP, Packet<?> packet) {
        if (playerMP != null && packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging digPacket = (C07PacketPlayerDigging) packet;
            if (digPacket.getStatus() == C07PacketPlayerDigging.Action.DROP_ITEM) {
                StaticMixinHelper.destructItemDrop = false;
            }
        }
    }

    private Cause handleKill(Cause cause, EntityPlayerMP player, Packet<?> packetIn) {
        if (player != null && packetIn instanceof C01PacketChatMessage) {
            C01PacketChatMessage chatPacket = (C01PacketChatMessage) packetIn;
            if (chatPacket.getMessage().contains("kill")) {
                if (!cause.contains(player)) {
                    cause = cause.with(NamedCause.of("Player", player));
                }
                StaticMixinHelper.destructItemDrop = true;
            }
        }
        return cause;
    }

    private void handleEntityDestruct(Cause cause, EntityPlayerMP player, Packet<?> packetIn) {
        if (player != null && packetIn instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) packetIn;
            if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                net.minecraft.entity.Entity entity = packet.getEntityFromWorld(this.getMinecraftWorld());
                if (entity != null && entity.isDead && !(entity instanceof EntityLivingBase)) {
                    Player spongePlayer = (Player) player;
                    MessageChannel originalChannel = spongePlayer.getMessageChannel();

                    DestructEntityEvent event = SpongeEventFactory.createDestructEntityEvent(cause, originalChannel, Optional.of(originalChannel),
                            Optional.empty(), Optional.empty(), (Entity) entity);
                    SpongeImpl.getGame().getEventManager().post(event);
                    event.getMessage().ifPresent(text -> event.getChannel().ifPresent(channel -> channel.send(text)));

                    StaticMixinHelper.lastDestroyedEntityId = entity.getEntityId();
                }
            }
        }
    }

    private void handleInventoryEvents(EntityPlayerMP player, Packet<?> packetIn, Container container) {
        if (player != null && player.getHealth() > 0 && container != null) {
            if (packetIn instanceof C10PacketCreativeInventoryAction && !StaticMixinHelper.ignoreCreativeInventoryPacket) {
                SpongeCommonEventFactory.handleCreativeClickInventoryEvent(Cause.of(NamedCause.source(player)), player,
                        (C10PacketCreativeInventoryAction) packetIn);
            } else {
                SpongeCommonEventFactory.handleInteractInventoryOpenCloseEvent(Cause.of(NamedCause.source(player)), player, packetIn);
                if (packetIn instanceof C0EPacketClickWindow) {
                    SpongeCommonEventFactory.handleClickInteractInventoryEvent(Cause.of(NamedCause.source(player)), player,
                            (C0EPacketClickWindow) packetIn);
                }
            }
        }
    }

    public void handleDroppedItems(Cause cause) {
        Iterator<Entity> iter = this.capturedEntityItems.iterator();
        ImmutableList.Builder<EntitySnapshot> entitySnapshotBuilder = new ImmutableList.Builder<>();
        while (iter.hasNext()) {
            Entity currentEntity = iter.next();
            if (TrackingHelper.doInvalidTransactionsExist(this.invalidTransactions, iter, currentEntity)) {
                continue;
            }

            if (cause.first(User.class).isPresent()) {
                // store user UUID with entity to track later
                User user = cause.first(User.class).get();
                ((IMixinEntity) currentEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, user.getUniqueId());
            } else if (cause.first(Entity.class).isPresent()) {
                IMixinEntity spongeEntity = (IMixinEntity) cause.first(Entity.class).get();
                Optional<User> owner = spongeEntity.getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR);
                if (owner.isPresent()) {
                    if (!cause.containsNamed(NamedCause.OWNER)) {
                        cause = cause.with(NamedCause.of(NamedCause.OWNER, owner.get()));
                    }
                    ((IMixinEntity) currentEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, owner.get().getUniqueId());
                }
                if (spongeEntity instanceof EntityLivingBase) {
                    IMixinEntityLivingBase spongeLivingEntity = (IMixinEntityLivingBase) spongeEntity;
                    DamageSource lastDamageSource = spongeLivingEntity.getLastDamageSource();
                    if (lastDamageSource != null && !cause.contains(lastDamageSource)) {
                        if (!cause.containsNamed("Attacker")) {
                            cause = cause.with(NamedCause.of("Attacker", lastDamageSource));
                        }

                    }
                }
            }
            entitySnapshotBuilder.add(currentEntity.createSnapshot());
        }

        List<EntitySnapshot> entitySnapshots = entitySnapshotBuilder.build();
        if (entitySnapshots.isEmpty()) {
            return;
        }
        DropItemEvent event;

        if (StaticMixinHelper.destructItemDrop) {
            event = SpongeEventFactory.createDropItemEventDestruct(cause, this.capturedEntityItems, entitySnapshots, this.getWorld());
        } else {
            event = SpongeEventFactory.createDropItemEventDispense(cause, this.capturedEntityItems, entitySnapshots, this.getWorld());
        }

        if (!(SpongeImpl.postEvent(event))) {
            // Handle player deaths
            for (Player causePlayer : cause.allOf(Player.class)) {
                EntityPlayerMP playermp = (EntityPlayerMP) causePlayer;
                if (playermp.getHealth() <= 0 || playermp.isDead) {
                    if (!playermp.worldObj.getGameRules().getBoolean("keepInventory")) {
                        playermp.inventory.clear();
                    } else {
                        // don't drop anything if keepInventory is enabled
                        this.capturedEntityItems.clear();
                    }
                }
            }

            Iterator<Entity> iterator =
                event instanceof DropItemEvent.Destruct ? ((DropItemEvent.Destruct) event).getEntities().iterator()
                                                        : ((DropItemEvent.Dispense) event).getEntities().iterator();
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                if (entity.isRemoved()) { // Entity removed in an event handler
                    iterator.remove();
                    continue;
                }

                net.minecraft.entity.Entity nmsEntity = (net.minecraft.entity.Entity) entity;
                int x = MathHelper.floor_double(nmsEntity.posX / 16.0D);
                int z = MathHelper.floor_double(nmsEntity.posZ / 16.0D);
                this.getMinecraftWorld().getChunkFromChunkCoords(x, z).addEntity(nmsEntity);
                this.getMinecraftWorld().loadedEntityList.add(nmsEntity);
                this.getMixinWorld().onSpongeEntityAdded(nmsEntity);
                SpongeHooks.logEntitySpawn(cause, nmsEntity);
                iterator.remove();
            }
        } else {
            if (cause.root() == StaticMixinHelper.packetPlayer) {
                sendItemChangeToPlayer(StaticMixinHelper.packetPlayer);
            }
            this.capturedEntityItems.clear();
        }
    }

    private boolean addWeatherEffect(net.minecraft.entity.Entity entity) {
        if (entity instanceof EntityLightningBolt) {
            LightningEvent.Pre event = SpongeEventFactory.createLightningEventPre(((IMixinEntityLightningBolt) entity).getCause());
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                return getMinecraftWorld().addWeatherEffect(entity);
            }
        } else {
            return getMinecraftWorld().addWeatherEffect(entity);
        }
        return false;
    }

    public void handleBlockCaptures(Cause cause) {
        EntityPlayerMP player = StaticMixinHelper.packetPlayer;
        Packet<?> packetIn = StaticMixinHelper.processingPacket;

        ImmutableList<Transaction<BlockSnapshot>> blockBreakTransactions;
        ImmutableList<Transaction<BlockSnapshot>> blockModifyTransactions;
        ImmutableList<Transaction<BlockSnapshot>> blockPlaceTransactions;
        ImmutableList<Transaction<BlockSnapshot>> blockDecayTransactions;
        ImmutableList<Transaction<BlockSnapshot>> blockMultiTransactions;
        ImmutableList.Builder<Transaction<BlockSnapshot>> breakBuilder = new ImmutableList.Builder<>();
        ImmutableList.Builder<Transaction<BlockSnapshot>> placeBuilder = new ImmutableList.Builder<>();
        ImmutableList.Builder<Transaction<BlockSnapshot>> decayBuilder = new ImmutableList.Builder<>();
        ImmutableList.Builder<Transaction<BlockSnapshot>> modifyBuilder = new ImmutableList.Builder<>();
        ImmutableList.Builder<Transaction<BlockSnapshot>> multiBuilder = new ImmutableList.Builder<>();
        ChangeBlockEvent.Break breakEvent = null;
        ChangeBlockEvent.Modify modifyEvent = null;
        ChangeBlockEvent.Place placeEvent = null;
        List<ChangeBlockEvent> blockEvents = new ArrayList<>();

        Iterator<BlockSnapshot> iterator = this.capturedSpongeBlockSnapshots.iterator();
        while (iterator.hasNext()) {
            SpongeBlockSnapshot blockSnapshot = (SpongeBlockSnapshot) iterator.next();
            CaptureType captureType = blockSnapshot.captureType;
            BlockPos pos = VecHelper.toBlockPos(blockSnapshot.getPosition());
            IBlockState currentState = this.getMinecraftWorld().getBlockState(pos);
            Transaction<BlockSnapshot>
                    transaction =
                    new Transaction<>(blockSnapshot, this.getMixinWorld().createSpongeBlockSnapshot(currentState, currentState.getBlock()
                            .getActualState(currentState, this.getMinecraftWorld(), pos), pos, 0));
            if (captureType == CaptureType.BREAK) {
                breakBuilder.add(transaction);
            } else if (captureType == CaptureType.DECAY) {
                decayBuilder.add(transaction);
            } else if (captureType == CaptureType.PLACE) {
                placeBuilder.add(transaction);
            } else if (captureType == CaptureType.MODIFY) {
                modifyBuilder.add(transaction);
            }
            multiBuilder.add(transaction);
            iterator.remove();
        }

        blockBreakTransactions = breakBuilder.build();
        blockDecayTransactions = decayBuilder.build();
        blockModifyTransactions = modifyBuilder.build();
        blockPlaceTransactions = placeBuilder.build();
        blockMultiTransactions = multiBuilder.build();
        ChangeBlockEvent changeBlockEvent;
        if (blockBreakTransactions.size() > 0) {
            changeBlockEvent = SpongeEventFactory.createChangeBlockEventBreak(cause, this.getWorld(), blockBreakTransactions);
            SpongeImpl.postEvent(changeBlockEvent);
            breakEvent = (ChangeBlockEvent.Break) changeBlockEvent;
            blockEvents.add(changeBlockEvent);
        }
        if (blockModifyTransactions.size() > 0) {
            changeBlockEvent = SpongeEventFactory.createChangeBlockEventModify(cause, this.getWorld(), blockModifyTransactions);
            SpongeImpl.postEvent(changeBlockEvent);
            modifyEvent = (ChangeBlockEvent.Modify) changeBlockEvent;
            blockEvents.add(changeBlockEvent);
        }
        if (blockPlaceTransactions.size() > 0) {
            changeBlockEvent = SpongeEventFactory.createChangeBlockEventPlace(cause, this.getWorld(), blockPlaceTransactions);
            SpongeImpl.postEvent(changeBlockEvent);
            placeEvent = (ChangeBlockEvent.Place) changeBlockEvent;
            blockEvents.add(changeBlockEvent);
        }
        if (blockEvents.size() > 1) {
            if (breakEvent != null) {
                int count = cause.allOf(ChangeBlockEvent.Break.class).size();
                String namedCause = "BreakEvent" + (count != 0 ? count : "");
                cause = cause.with(NamedCause.of(namedCause, breakEvent));
            }
            if (modifyEvent != null) {
                int count = cause.allOf(ChangeBlockEvent.Modify.class).size();
                String namedCause = "ModifyEvent" + (count != 0 ? count : "");
                cause = cause.with(NamedCause.of(namedCause, modifyEvent));
            }
            if (placeEvent != null) {
                int count = cause.allOf(ChangeBlockEvent.Place.class).size();
                String namedCause = "PlaceEvent" + (count != 0 ? count : "");
                cause = cause.with(NamedCause.of(namedCause, placeEvent));
            }
            changeBlockEvent = SpongeEventFactory.createChangeBlockEventPost(cause, this.getWorld(), blockMultiTransactions);
            SpongeImpl.postEvent(changeBlockEvent);
            if (changeBlockEvent.isCancelled()) {
                // Restore original blocks
                ListIterator<Transaction<BlockSnapshot>>
                        listIterator =
                        changeBlockEvent.getTransactions().listIterator(changeBlockEvent.getTransactions().size());
                processList(listIterator);

                if (player != null) {
                    CaptureType captureType = null;
                    if (packetIn instanceof C08PacketPlayerBlockPlacement) {
                        captureType = CaptureType.PLACE;
                    } else if (packetIn instanceof C07PacketPlayerDigging) {
                        captureType = CaptureType.BREAK;
                    }
                    if (captureType != null) {
                        handlePostPlayerBlockEvent(captureType, changeBlockEvent.getTransactions());
                    }
                }

                // clear entity list and return to avoid spawning items
                this.capturedEntities.clear();
                this.capturedEntityItems.clear();
                return;
            }
        }

        if (blockDecayTransactions.size() > 0) {
            changeBlockEvent = SpongeEventFactory.createChangeBlockEventDecay(cause, this.getWorld(), blockDecayTransactions);
            SpongeImpl.postEvent(changeBlockEvent);
            blockEvents.add(changeBlockEvent);
        }
        processBlockEvents(cause, blockEvents, packetIn, player, breakEvent);
    }

    private void processBlockEvents(Cause cause, List<ChangeBlockEvent> blockEvents, Packet<?> packetIn, @Nullable EntityPlayer player, @Nullable ChangeBlockEvent.Break breakEvent) {
        for (ChangeBlockEvent blockEvent : blockEvents) {
            CaptureType captureType = null;
            if (blockEvent instanceof ChangeBlockEvent.Break) {
                captureType = CaptureType.BREAK;
            } else if (blockEvent instanceof ChangeBlockEvent.Decay) {
                captureType = CaptureType.DECAY;
            } else if (blockEvent instanceof ChangeBlockEvent.Modify) {
                captureType = CaptureType.MODIFY;
            } else if (blockEvent instanceof ChangeBlockEvent.Place) {
                captureType = CaptureType.PLACE;
            }

            C08PacketPlayerBlockPlacement packet = null;

            if (packetIn instanceof C08PacketPlayerBlockPlacement) {
                packet = (C08PacketPlayerBlockPlacement) packetIn;
            }

            if (blockEvent.isCancelled()) {
                // Restore original blocks
                ListIterator<Transaction<BlockSnapshot>>
                    listIterator =
                    blockEvent.getTransactions().listIterator(blockEvent.getTransactions().size());
                processList(listIterator);

                handlePostPlayerBlockEvent(captureType, blockEvent.getTransactions());

                // clear entity list and return to avoid spawning items
                this.capturedEntities.clear();
                this.capturedEntityItems.clear();
                return;
            } else {
                for (Transaction<BlockSnapshot> transaction : blockEvent.getTransactions()) {
                    if (!transaction.isValid()) {
                        this.invalidTransactions.add(transaction);
                    } else {
                        if (captureType == CaptureType.BREAK && cause.first(User.class).isPresent()) {
                            BlockPos pos = VecHelper.toBlockPos(transaction.getOriginal().getPosition());
                            for (EntityHanging hanging : SpongeHooks.findHangingEntities(this.getMinecraftWorld(), pos)) {
                                if (hanging != null) {
                                    if (hanging instanceof EntityItemFrame) {
                                        EntityItemFrame itemFrame = (EntityItemFrame) hanging;
                                        net.minecraft.entity.Entity dropCause = null;
                                        if (cause.root() instanceof net.minecraft.entity.Entity) {
                                            dropCause = (net.minecraft.entity.Entity) cause.root();
                                        }

                                        itemFrame.dropItemOrSelf(dropCause, true);
                                        itemFrame.setDead();
                                    }
                                }
                            }
                        }

                        if (captureType == CaptureType.PLACE && packetIn instanceof C08PacketPlayerBlockPlacement) {
                            BlockPos pos = VecHelper.toBlockPos(transaction.getFinal().getPosition());
                            IMixinChunk spongeChunk = (IMixinChunk) this.getMinecraftWorld().getChunkFromBlockCoords(pos);
                            spongeChunk.addTrackedBlockPosition((net.minecraft.block.Block) transaction.getFinal().getState().getType(), pos,
                                (User) player, PlayerTracker.Type.OWNER);
                            spongeChunk.addTrackedBlockPosition((net.minecraft.block.Block) transaction.getFinal().getState().getType(), pos,
                                (User) player, PlayerTracker.Type.NOTIFIER);
                        }
                    }
                }

                if (this.invalidTransactions.size() > 0) {
                    for (Transaction<BlockSnapshot> transaction : Lists.reverse(this.invalidTransactions)) {
                        push(BlockPhase.State.RESTORING_BLOCKS);
                        transaction.getOriginal().restore(true, false);
                        pop();
                    }
                    handlePostPlayerBlockEvent(captureType, this.invalidTransactions);
                }

                if (this.capturedEntityItems.size() > 0 && blockEvents.get(0) == breakEvent) {
                    StaticMixinHelper.destructItemDrop = true;
                }

                this.markAndNotifyBlockPost(blockEvent.getTransactions(), captureType, cause);

                if (captureType == CaptureType.PLACE && player != null && packet != null && packet.getStack() != null) {
                    player.addStat(StatList.objectUseStats[net.minecraft.item.Item.getIdFromItem(packet.getStack().getItem())], 1);
                }
            }
        }
    }

    private void handlePostPlayerBlockEvent(@Nullable CaptureType captureType, List<Transaction<BlockSnapshot>> transactions) {
        if (StaticMixinHelper.packetPlayer == null) {
            return;
        }

        if (captureType == CaptureType.BREAK) {
            // Let the client know the blocks still exist
            for (Transaction<BlockSnapshot> transaction : transactions) {
                BlockSnapshot snapshot = transaction.getOriginal();
                BlockPos pos = VecHelper.toBlockPos(snapshot.getPosition());
                StaticMixinHelper.packetPlayer.playerNetServerHandler.sendPacket(new S23PacketBlockChange(this.getMinecraftWorld(), pos));

                // Update any tile entity data for this block
                net.minecraft.tileentity.TileEntity tileentity = this.getMinecraftWorld().getTileEntity(pos);
                if (tileentity != null) {
                    Packet<?> pkt = tileentity.getDescriptionPacket();
                    if (pkt != null) {
                        StaticMixinHelper.packetPlayer.playerNetServerHandler.sendPacket(pkt);
                    }
                }
            }
        } else if (captureType == CaptureType.PLACE) {
            sendItemChangeToPlayer(StaticMixinHelper.packetPlayer);
        }
    }

    private void sendItemChangeToPlayer(EntityPlayerMP player) {
        if (StaticMixinHelper.prePacketProcessItem == null) {
            return;
        }

        // handle revert
        player.isChangingQuantityOnly = true;
        player.inventory.mainInventory[player.inventory.currentItem] = StaticMixinHelper.prePacketProcessItem;
        Slot slot = player.openContainer.getSlotFromInventory(player.inventory, player.inventory.currentItem);
        player.openContainer.detectAndSendChanges();
        player.isChangingQuantityOnly = false;
        // force client itemstack update if place event was cancelled
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(player.openContainer.windowId, slot.slotNumber,
            StaticMixinHelper.prePacketProcessItem));
    }

    private void processList(ListIterator<Transaction<BlockSnapshot>> listIterator) {
        while (listIterator.hasPrevious()) {
            Transaction<BlockSnapshot> transaction = listIterator.previous();
            push(BlockPhase.State.RESTORING_BLOCKS);
            transaction.getOriginal().restore(true, false);
            pop();
        }
    }

    public boolean processSpawnEntity(Entity entity, Cause cause) {
        checkNotNull(entity, "Entity cannot be null!");
        checkNotNull(cause, "Cause cannot be null!");

        // Very first thing - fire events that are from entity construction
        if (((IMixinEntity) entity).isInConstructPhase()) {
            ((IMixinEntity) entity).firePostConstructEvents();
        }

        final net.minecraft.entity.Entity entityIn = (net.minecraft.entity.Entity) entity;
        // do not drop any items while restoring blocksnapshots. Prevents dupes
        final net.minecraft.world.World minecraftWorld = this.getMinecraftWorld();
        if (!minecraftWorld.isRemote && entityIn instanceof EntityItem && this.isRestoringBlocks()) {
            return false;
        }

        int i = MathHelper.floor_double(entityIn.posX / 16.0D);
        int j = MathHelper.floor_double(entityIn.posZ / 16.0D);
        boolean flag = entityIn.forceSpawn;

        if (entityIn instanceof EntityPlayer) {
            flag = true;
        } else if (entityIn instanceof EntityLightningBolt) {
            ((IMixinEntityLightningBolt) entityIn).setCause(cause);
        }

        if (!flag && !minecraftWorld.isChunkLoaded(i, j, true)) {
            return false;
        } else {
            if (entityIn instanceof EntityPlayer) {
                EntityPlayer entityplayer = (EntityPlayer) entityIn;
                net.minecraft.world.World world = this.targetWorld;
                world.playerEntities.add(entityplayer);
                world.updateAllPlayersSleepingFlag();
            }

            final IMixinWorld mixinWorld = this.getMixinWorld();
            if (minecraftWorld.isRemote || flag || this.isSpawningDeathDrops()) {
                minecraftWorld.getChunkFromChunkCoords(i, j).addEntity(entityIn);
                minecraftWorld.loadedEntityList.add(entityIn);
                mixinWorld.onSpongeEntityAdded(entityIn);
                return true;
            }

            if (this.isCapturing()) {
                if (this.currentTickBlock != null) {
                    BlockPos sourcePos = VecHelper.toBlockPos(this.currentTickBlock.getPosition());
                    Block targetBlock = getMinecraftWorld().getBlockState(entityIn.getPosition()).getBlock();
                    SpongeHooks
                        .tryToTrackBlockAndEntity(minecraftWorld, this.currentTickBlock, entityIn, sourcePos, targetBlock, entityIn.getPosition(),
                            PlayerTracker.Type.NOTIFIER);
                }
                if (this.currentTickEntity != null) {
                    Optional<User> creator = ((IMixinEntity) this.currentTickEntity).getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR);
                    if (creator.isPresent()) { // transfer user to next entity. This occurs with falling blocks that change into items
                        ((IMixinEntity) entityIn).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, creator.get().getUniqueId());
                    }
                }
                if (entityIn instanceof EntityItem) {
                    this.capturedEntityItems.add(entity);
                } else {
                    this.capturedEntities.add(entity);
                }
                return true;
            } else { // Custom

                if (entityIn instanceof EntityFishHook && ((EntityFishHook) entityIn).angler == null) {
                    // TODO MixinEntityFishHook.setShooter makes angler null
                    // sometimes, but that will cause NPE when ticking
                    return false;
                }

                EntityLivingBase specialCause = null;
                String causeName = "";
                // Special case for throwables
                if (entityIn instanceof EntityThrowable) {
                    EntityThrowable throwable = (EntityThrowable) entityIn;
                    specialCause = throwable.getThrower();

                    if (specialCause != null) {
                        causeName = NamedCause.THROWER;
                        if (specialCause instanceof Player) {
                            Player player = (Player) specialCause;
                            ((IMixinEntity) entityIn).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueId());
                        }
                    }
                }
                // Special case for TNT
                else if (entityIn instanceof EntityTNTPrimed) {
                    EntityTNTPrimed tntEntity = (EntityTNTPrimed) entityIn;
                    specialCause = tntEntity.getTntPlacedBy();
                    causeName = NamedCause.IGNITER;

                    if (specialCause instanceof Player) {
                        Player player = (Player) specialCause;
                        ((IMixinEntity) entityIn).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueId());
                    }
                }
                // Special case for Tameables
                else if (entityIn instanceof EntityTameable) {
                    EntityTameable tameable = (EntityTameable) entityIn;
                    if (tameable.getOwner() != null) {
                        specialCause = tameable.getOwner();
                        causeName = NamedCause.OWNER;
                    }
                }

                if (specialCause != null && !cause.containsNamed(causeName)) {
                    cause = cause.with(NamedCause.of(causeName, specialCause));
                }

                org.spongepowered.api.event.Event event;
                ImmutableList.Builder<EntitySnapshot> entitySnapshotBuilder = new ImmutableList.Builder<>();
                entitySnapshotBuilder.add(((Entity) entityIn).createSnapshot());

                if (entityIn instanceof EntityItem) {
                    this.capturedEntityItems.add(entity);
                    event = SpongeEventFactory.createDropItemEventCustom(cause, this.capturedEntityItems,
                            entitySnapshotBuilder.build(), this.getWorld());
                } else {
                    this.capturedEntities.add(entity);
                    event = SpongeEventFactory.createSpawnEntityEventCustom(cause, this.capturedEntities,
                            entitySnapshotBuilder.build(), this.getWorld());
                }
                if (!SpongeImpl.postEvent(event) && !entity.isRemoved()) {
                    if (entityIn instanceof EntityWeatherEffect) {
                        return addWeatherEffect(entityIn);
                    }

                    minecraftWorld.getChunkFromChunkCoords(i, j).addEntity(entityIn);
                    minecraftWorld.loadedEntityList.add(entityIn);
                    mixinWorld.onSpongeEntityAdded(entityIn);
                    if (entityIn instanceof EntityItem) {
                        this.capturedEntityItems.remove(entity);
                    } else {
                        this.capturedEntities.remove(entity);
                    }
                    return true;
                }

                return false;
            }
        }
    }

    public void randomTickBlock(Block block, BlockPos pos, IBlockState state, Random random) {
        setCurrentTickBlock(this.getMixinWorld().createSpongeBlockSnapshot(state, state.getBlock().getActualState(state, this.getMinecraftWorld(), pos), pos, 0));
        pop();
        push(WorldPhase.State.RANDOM_TICK_BLOCK);
        block.randomTick(this.getMinecraftWorld(), pos, state, random);
        completeTickingBlock();
    }

    public void updateTickBlock(Block block, BlockPos pos, IBlockState state, Random rand) {
        setCurrentTickBlock(this.getMixinWorld().createSpongeBlockSnapshot(state, state.getBlock().getActualState(state, this.getMinecraftWorld(), pos), pos, 0));
        block.updateTick(this.getMinecraftWorld(), pos, state, rand);
        completeTickingBlock();
    }

    public void notifyBlockOfStateChange(BlockPos notifyPos, final Block sourceBlock, BlockPos sourcePos) {
        if (!this.getMinecraftWorld().isRemote) {
            IBlockState iblockstate = this.getMinecraftWorld().getBlockState(notifyPos);

            try {
                if (!this.getMinecraftWorld().isRemote) {
                    final Chunk chunkFromBlockCoords = this.getMinecraftWorld().getChunkFromBlockCoords(notifyPos);
                    if (StaticMixinHelper.packetPlayer != null) {
                        IMixinChunk spongeChunk = (IMixinChunk) chunkFromBlockCoords;
                        if (!(spongeChunk instanceof EmptyChunk)) {
                            spongeChunk.addTrackedBlockPosition(iblockstate.getBlock(), notifyPos, (User) StaticMixinHelper.packetPlayer,
                                    PlayerTracker.Type.NOTIFIER);
                        }
                    } else {
                        Object source = null;
                        if (this.hasTickingBlock()) {
                            source = this.getCurrentTickBlock().get();
                            sourcePos = VecHelper.toBlockPos(this.getCurrentTickBlock().get().getPosition());
                        } else if (this.hasTickingTileEntity()) {
                            source = this.getCurrentTickTileEntity().get();
                            sourcePos = ((net.minecraft.tileentity.TileEntity) this.getCurrentTickTileEntity().get()).getPos();
                        } else if (this.hasTickingEntity()) { // Falling Blocks
                            IMixinEntity spongeEntity = (IMixinEntity) this.getCurrentTickEntity().get();
                            sourcePos = ((net.minecraft.entity.Entity) this.getCurrentTickEntity().get()).getPosition();
                            Optional<User> owner = spongeEntity.getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR);
                            Optional<User> notifier = spongeEntity.getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_NOTIFIER);
                            IMixinChunk spongeChunk = (IMixinChunk) chunkFromBlockCoords;
                            if (notifier.isPresent()) {
                                spongeChunk.addTrackedBlockPosition(iblockstate.getBlock(), notifyPos, notifier.get(), PlayerTracker.Type.NOTIFIER);
                            } else if (owner.isPresent()) {
                                spongeChunk.addTrackedBlockPosition(iblockstate.getBlock(), notifyPos, owner.get(), PlayerTracker.Type.NOTIFIER);
                            }
                        }

                        if (source != null) {
                            SpongeHooks.tryToTrackBlock(this.getMinecraftWorld(), source, sourcePos, iblockstate.getBlock(), notifyPos,
                                    PlayerTracker.Type.NOTIFIER);
                        }
                    }
                }

                iblockstate.getBlock().onNeighborBlockChange(this.getMinecraftWorld(), notifyPos, iblockstate, sourceBlock);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception while updating neighbours");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being updated");
                // TODO
                /*crashreportcategory.addCrashSectionCallable("Source block type", new Callable()
                {
                    public String call() {
                        try {
                            return String.format("ID #%d (%s // %s)", new Object[] {Integer.valueOf(Block.getIdFromBlock(blockIn)), blockIn.getUnlocalizedName(), blockIn.getClass().getCanonicalName()});
                        } catch (Throwable throwable1) {
                            return "ID #" + Block.getIdFromBlock(blockIn);
                        }
                    }
                });*/
                CrashReportCategory.addBlockInfo(crashreportcategory, notifyPos, iblockstate);
                throw new ReportedException(crashreport);
            }
        }
    }

    public void markAndNotifyBlockPost(List<Transaction<BlockSnapshot>> transactions, @Nullable CaptureType type, Cause cause) {
        // We have to use a proxy so that our pending changes are notified such that any accessors from block
        // classes do not fail on getting the incorrect block state from the IBlockAccess
        SpongeProxyBlockAccess proxyBlockAccess = new SpongeProxyBlockAccess(this.getMinecraftWorld(), transactions);
        for (Transaction<BlockSnapshot> transaction : transactions) {
            if (!transaction.isValid()) {
                continue; // Don't use invalidated block transactions during notifications, these only need to be restored
            }
            // Handle custom replacements
            if (transaction.getCustom().isPresent()) {
                push(BlockPhase.State.RESTORING_BLOCKS);
                transaction.getFinal().restore(true, false);
                pop();
            }

            SpongeBlockSnapshot oldBlockSnapshot = (SpongeBlockSnapshot) transaction.getOriginal();
            SpongeBlockSnapshot newBlockSnapshot = (SpongeBlockSnapshot) transaction.getFinal();
            SpongeHooks.logBlockAction(cause, this.getMinecraftWorld(), type, transaction);
            int updateFlag = oldBlockSnapshot.getUpdateFlag();
            BlockPos pos = VecHelper.toBlockPos(oldBlockSnapshot.getPosition());
            IBlockState originalState = (IBlockState) oldBlockSnapshot.getState();
            IBlockState newState = (IBlockState) newBlockSnapshot.getState();
            BlockSnapshot currentTickingBlock = this.getCurrentTickBlock().orElse(null);
            // Containers get placed automatically
            if (!SpongeImplHooks.blockHasTileEntity(newState.getBlock(), newState)) {
                this.setCurrentTickBlock(this.getMixinWorld().createSpongeBlockSnapshot(newState,
                        newState.getBlock().getActualState(newState, proxyBlockAccess, pos), pos, updateFlag));
                newState.getBlock().onBlockAdded(this.getMinecraftWorld(), pos, newState);
                if (shouldChainCause(cause)) {
                    Cause currentCause = cause;
                    List<NamedCause> causes = new ArrayList<>();
                    causes.add(NamedCause.source(this.getCurrentTickBlock().get()));
                    List<String> namesUsed = new ArrayList<>();
                    int iteration = 1;
                    final Map<String, Object> namedCauses = currentCause.getNamedCauses();
                    for (Map.Entry<String, Object> entry : namedCauses.entrySet()) {
                        String name = entry.getKey().equalsIgnoreCase("Source")
                                      ? "AdditionalSource" : entry.getKey().equalsIgnoreCase("AdditionalSource")
                                                             ? "PreviousSource" : entry.getKey();
                        if (!namesUsed.contains(name)) {
                            name += iteration++;
                        }
                        namesUsed.add(name);
                        causes.add(NamedCause.of(name, entry.getValue()));
                    }
                    cause = Cause.of(causes);
                }
            }

            proxyBlockAccess.proceed();
            this.getMixinWorld().markAndNotifyNeighbors(pos, null, originalState, newState, updateFlag);

            // Handle any additional captures during notify
            // This is to ensure new captures do not leak into next tick with wrong cause
            if (this.getCapturedEntities().size() > 0 && this.pluginCause == null) {
                this.handlePostTickCaptures(cause);
            }

            if (currentTickingBlock != null) {
                this.setCurrentTickBlock(currentTickingBlock);
            } else {
                this.currentTickBlock = null;
            }
        }
    }

    private boolean shouldChainCause(Cause cause) {
        return !this.isCapturingTerrainGen() && !this.isWorldSpawnerRunning() && !this.isChunkSpawnerRunning()
               && !this.isProcessingBlockRandomTicks() && !this.isCaptureCommand() && this.hasTickingBlock() && this.pluginCause == null
               && !cause.contains(this.getCurrentTickBlock().get());

    }


    public void setCommandCapture(ICommandSender sender, ICommand command) {
        this.commandSender = sender;
        this.command = command;
        push(GeneralPhase.State.COMMAND);
    }

    public void completeCommand() {
        checkState(this.command != null);
        checkState(this.commandSender != null);
        handlePostTickCaptures(Cause.of(NamedCause.of("Command", this.command), NamedCause.of("CommandSender", this.commandSender)));
        this.command = null;
        this.commandSender = null;
    }

    public void completePacketProcessing(EntityPlayerMP packetPlayer) {
        final ITrackingPhaseState phaseState = this.phases.peek();
        if (phaseState.getPhase() != TrackingPhases.PACKET) {
            System.err.printf("We aren't capturing a packet!!! Curren phase: %s%n", phaseState);
            Thread.dumpStack();
        }
        handlePostTickCaptures(Cause.of(NamedCause.source(packetPlayer)));
    }

    public void completePopulate() {
        getCapturedPopulators().clear();
        pop();

    }

    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
        net.minecraft.world.chunk.Chunk chunk = this.getMinecraftWorld().getChunkFromBlockCoords(pos);
        IBlockState currentState = chunk.getBlockState(pos);
        if (currentState == newState) {
            return false;
        }

        Block block = newState.getBlock();
        BlockSnapshot originalBlockSnapshot = null;
        BlockSnapshot newBlockSnapshot = null;
        Transaction<BlockSnapshot> transaction = null;
        LinkedHashMap<Vector3i, Transaction<BlockSnapshot>> populatorSnapshotList = null;

        // Don't capture if we are restoring blocks
        final CauseTracker causeTracker = this;
        if (!this.getMinecraftWorld().isRemote && !causeTracker.isRestoringBlocks() && !causeTracker.isWorldSpawnerRunning() && !causeTracker.isChunkSpawnerRunning()) {
            originalBlockSnapshot = null;
            if (causeTracker.isCapturingTerrainGen()) {
                if (StaticMixinHelper.runningGenerator != null) {
                    originalBlockSnapshot = this.getMixinWorld().createSpongeBlockSnapshot(currentState, currentState.getBlock().getActualState(currentState,
                            this.getMinecraftWorld(), pos), pos, flags);

                    if (causeTracker.getCapturedPopulators().get(StaticMixinHelper.runningGenerator) == null) {
                        causeTracker.getCapturedPopulators().put(StaticMixinHelper.runningGenerator, new LinkedHashMap<>());
                    }

                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.POPULATE;
                    transaction = new Transaction<>(originalBlockSnapshot, originalBlockSnapshot.withState((BlockState) newState));
                    populatorSnapshotList = causeTracker.getCapturedPopulators().get(StaticMixinHelper.runningGenerator);
                    populatorSnapshotList.put(transaction.getOriginal().getPosition(), transaction);
                }
            } else if (!(((IMixinMinecraftServer) MinecraftServer.getServer()).isPreparingChunks())) {
                originalBlockSnapshot = this.getMixinWorld().createSpongeBlockSnapshot(currentState, currentState.getBlock().getActualState(currentState,
                        this.getMinecraftWorld(), pos), pos, flags);

                if (StaticMixinHelper.runningGenerator != null) {
                    if (causeTracker.getCapturedPopulators().get(StaticMixinHelper.runningGenerator) == null) {
                        causeTracker.getCapturedPopulators().put(StaticMixinHelper.runningGenerator, new LinkedHashMap<>());
                    }

                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.POPULATE;
                    transaction = new Transaction<>(originalBlockSnapshot, originalBlockSnapshot.withState((BlockState) newState));
                    populatorSnapshotList = causeTracker.getCapturedPopulators().get(StaticMixinHelper.runningGenerator);
                    populatorSnapshotList.put(transaction.getOriginal().getPosition(), transaction);
                } else if (causeTracker.getPhases().peek() == BlockPhase.State.BLOCK_DECAY) {
                    // Only capture final state of decay, ignore the rest
                    if (block == Blocks.air) {
                        ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.DECAY;
                        causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                    }
                } else if (block == Blocks.air) {
                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.BREAK;
                    causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                } else if (block != currentState.getBlock()) {
                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.PLACE;
                    causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                } else {
                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.MODIFY;
                    causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                }
            }
        }

        int oldLight = currentState.getBlock().getLightValue();

        IBlockState iblockstate1 = ((IMixinChunk) chunk).setBlockState(pos, newState, currentState, newBlockSnapshot);

        if (iblockstate1 == null) {
            if (originalBlockSnapshot != null) {
                causeTracker.getCapturedSpongeBlockSnapshots().remove(originalBlockSnapshot);
                if (populatorSnapshotList != null) {
                    populatorSnapshotList.remove(transaction);
                }
            }
            return false;
        } else {
            Block block1 = iblockstate1.getBlock();

            if (block.getLightOpacity() != block1.getLightOpacity() || block.getLightValue() != oldLight) {
                this.getMinecraftWorld().theProfiler.startSection("checkLight");
                this.getMinecraftWorld().checkLight(pos);
                this.getMinecraftWorld().theProfiler.endSection();
            }

            if (causeTracker.hasPluginCause()) {
                causeTracker.handleBlockCaptures(causeTracker.getPluginCause().get());
            } else {
                // Don't notify clients or update physics while capturing blockstates
                if (originalBlockSnapshot == null) {
                    // Modularize client and physic updates
                    this.getMixinWorld().markAndNotifyNeighbors(pos, chunk, iblockstate1, newState, flags);
                }
            }

            return true;
        }
    }

    public void switchToPhase(TrackingPhase general, ITrackingPhaseState state) {
        ITrackingPhaseState currentState = this.phases.peek();
        if (!currentState.canSwitchTo(state)) {
            throw new IllegalArgumentException(String.format("Cannot switch from %s to %s", currentState, state));
        }
        this.phases.push(state);

    }

    public void completePhase() {

    }

    public void setPacketCapture(EntityPlayerMP packetPlayer, Packet<?> packetIn, boolean ignoreCreative, Container openContainer,
            ItemStackSnapshot cursor, ItemStack itemUsed) {

    }
}
