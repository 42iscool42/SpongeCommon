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
package org.spongepowered.common.mixin.core.world.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.server.MinecraftServerBridge;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.bridge.world.storage.SaveHandlerBridge;
import org.spongepowered.common.data.util.DataUtil;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.world.storage.SpongePlayerDataHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

@NonnullByDefault
@Mixin(SaveHandler.class)
public abstract class MixinSaveHandler implements SaveHandlerBridge {

    @Shadow @Final private File worldDirectory;

    @Nullable private Exception capturedException;
    @Nullable private Path file;

    @ModifyArg(method = "checkSessionLock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/MinecraftException;<init>(Ljava/lang/String;)V", ordinal = 0, remap = false))
    private String modifyMinecraftExceptionOutputIfNotInitializationTime(String message) {
        return "The save folder for world " + this.worldDirectory + " is being accessed from another location, aborting";
    }

    @ModifyArg(method = "checkSessionLock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/MinecraftException;<init>(Ljava/lang/String;)V", ordinal = 1, remap = false))
    private String modifyMinecraftExceptionOutputIfIOException(String message) {
        return "Failed to check session lock for world " + this.worldDirectory + ", aborting";
    }

    @Inject(method = "saveWorldInfoWithPlayer", at = @At("RETURN"))
    private void onSaveWorldInfoWithPlayerEnd(WorldInfo worldInformation, NBTTagCompound tagCompound, CallbackInfo ci) {
        this.saveSpongeDatData(worldInformation);
    }

    private void saveSpongeDatData(WorldInfo info) {
        try {
            final File spongeFile1 = new File(this.worldDirectory, "level_sponge.dat_new");
            final File spongeFile2 = new File(this.worldDirectory, "level_sponge.dat_old");
            final File spongeFile3 = new File(this.worldDirectory, "level_sponge.dat");
            try (final FileOutputStream stream = new FileOutputStream(spongeFile1)) {
                CompressedStreamTools.writeCompressed(((WorldInfoBridge) info).getSpongeRootLevelNbt(), stream);
            }
            if (spongeFile2.exists()) {
                spongeFile2.delete();
            }

            spongeFile3.renameTo(spongeFile2);

            if (spongeFile3.exists()) {
                spongeFile3.delete();
            }

            spongeFile1.renameTo(spongeFile3);

            if (spongeFile1.exists()) {
                spongeFile1.delete();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Redirect(method = "readPlayerData(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/nbt/NBTTagCompound;", at = @At(value = "INVOKE", target = "Ljava/io/File;isFile()Z", remap = false))
    private boolean grabfile(File localfile) {
        final boolean isFile = localfile.isFile();
        this.file = isFile ? localfile.toPath() : null;
        return isFile;
    }

    @Redirect(method = "readPlayerData(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/nbt/NBTTagCompound;", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompressedStreamTools;readCompressed(Ljava/io/InputStream;)Lnet/minecraft/nbt/NBTTagCompound;"))
    private NBTTagCompound readPlayerUniqueIdAndJoinData(InputStream inputStream) throws IOException {
        Instant creation = this.file == null ? Instant.now() : Files.readAttributes(this.file, BasicFileAttributes.class).creationTime().toInstant();
        NBTTagCompound compound = CompressedStreamTools.readCompressed(inputStream);
        Instant lastPlayed = Instant.now();
        if (compound.hasKey(Constants.Bukkit.BUKKIT, Constants.NBT.TAG_COMPOUND)) {
            final NBTTagCompound bukkitCompound = compound.getCompoundTag(Constants.Bukkit.BUKKIT);
            creation = Instant.ofEpochMilli(bukkitCompound.getLong(Constants.Bukkit.BUKKIT_FIRST_PLAYED));
            lastPlayed = Instant.ofEpochMilli(bukkitCompound.getLong(Constants.Bukkit.BUKKIT_LAST_PLAYED));
        }
        UUID playerId = null;
        if (compound.hasUniqueId(Constants.UUID)) {
            playerId = compound.getUniqueId(Constants.UUID);
        }
        if (playerId != null) {
            Optional<Instant> savedFirst = SpongePlayerDataHandler.getFirstJoined(playerId);
            if (savedFirst.isPresent()) {
                creation = savedFirst.get();
            }
            Optional<Instant> savedJoined = SpongePlayerDataHandler.getLastPlayed(playerId);
            if (savedJoined.isPresent()) {
                lastPlayed = savedJoined.get();
            }
            SpongePlayerDataHandler.setPlayerInfo(playerId, creation, lastPlayed);
        }
        this.file = null;
        return compound;
    }

    @Inject(method = "writePlayerData", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompressedStreamTools;writeCompressed(Lnet/minecraft/nbt/NBTTagCompound;Ljava/io/OutputStream;)V", shift = At.Shift.AFTER))
    private void writeSpongePlayerData(EntityPlayer player, CallbackInfo callbackInfo) {
        SpongePlayerDataHandler.savePlayer(player.getUniqueID());
    }

    @Inject(
        method = "writePlayerData",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V",
            remap = false
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void beforeLogWarning(EntityPlayer player, CallbackInfo ci, Exception exception) {
        this.capturedException = exception;
    }

    @Redirect(
        method = "writePlayerData",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V",
            remap = false
        )
    )
    private void onWarn(Logger logger, String message, Object param) {
        logger.warn(message, param, this.capturedException);
        this.capturedException = null;
    }

    @Override
    public void bridge$loadSpongeData(WorldInfo info) {
        final File spongeFile = new File(this.worldDirectory, "level_sponge.dat");
        final File spongeOldFile = new File(this.worldDirectory, "level_sponge.dat_old");

        if (spongeFile.exists() || spongeOldFile.exists()) {
            final File actualFile = spongeFile.exists() ? spongeFile : spongeOldFile;
            final NBTTagCompound compound;
            try (final FileInputStream stream = new FileInputStream(actualFile)) {
                compound = CompressedStreamTools.readCompressed(stream);
            } catch (Exception ex) {
                throw new RuntimeException("Attempt failed when reading Sponge level data for [" + info.getWorldName() + "] from file [" + actualFile.getName() + "]!", ex);
            }
            ((WorldInfoBridge) info).setSpongeRootLevelNBT(compound);
            if (compound.hasKey(Constants.Sponge.SPONGE_DATA)) {
                final NBTTagCompound spongeCompound = compound.getCompoundTag(Constants.Sponge.SPONGE_DATA);
                DataUtil.spongeDataFixer.process(FixTypes.LEVEL, spongeCompound);
                ((WorldInfoBridge) info).readSpongeNbt(spongeCompound);
            }
        }
    }

    // SF overrides getWorldDirectory for mod compatibility.
    // In order to avoid conflicts, we simply use another method to guarantee
    // the sponge world directory is returned for the corresponding save handler.
    // MixinAnvilSaveHandler#getChunkLoader is one example where we must use this method.
    @Override
    public File bridge$getWorldDirectory() {
        return this.worldDirectory;
    }
}
