package cn.dancingsnow.neoecoae.gui.ldlib.support;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class NELDLibStateCodecs {
    private static final int MAX_STORAGE_UI_TYPES = 64;
    private static final int MAX_WORKER_OUTPUTS = 128;
    private static final int MAX_PARALLEL_CORE_TIERS = 128;
    private static final int MAX_STRUCTURE_TERMINAL_MATERIALS = 512;

    public static void writeStorage(FriendlyByteBuf buf, NEStorageUiState state) {
        buf.writeBlockPos(state.pos());
        buf.writeLong(state.storedEnergy());
        buf.writeLong(state.maxEnergy());
        buf.writeBoolean(state.formed());
        List<NEStorageUiTypeState> types = state.typeStates();
        buf.writeVarInt(Math.min(types.size(), MAX_STORAGE_UI_TYPES));
        int written = 0;
        for (NEStorageUiTypeState type : types) {
            if (written++ >= MAX_STORAGE_UI_TYPES) {
                break;
            }
            buf.writeResourceLocation(type.typeId());
            buf.writeUtf(type.displayName(), 128);
            buf.writeLong(type.usedTypes());
            buf.writeLong(type.totalTypes());
            buf.writeLong(type.usedBytes());
            buf.writeLong(type.totalBytes());
        }
    }

    public static NEStorageUiState readStorage(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long storedEnergy = buf.readLong();
        long maxEnergy = buf.readLong();
        boolean formed = buf.readBoolean();
        int typeCount = buf.readVarInt();
        if (typeCount > MAX_STORAGE_UI_TYPES) {
            throw new IllegalArgumentException("Storage UI type count exceeds protocol limit: " + typeCount);
        }
        List<NEStorageUiTypeState> types = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            types.add(new NEStorageUiTypeState(
                    buf.readResourceLocation(),
                    buf.readUtf(128),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong()));
        }
        return new NEStorageUiState(pos, types, storedEnergy, maxEnergy, formed);
    }

    public static void writeComputation(FriendlyByteBuf buf, NEComputationUiState state) {
        buf.writeBlockPos(state.pos());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.active());
        buf.writeInt(state.usedThreads());
        buf.writeInt(state.maxThreads());
        buf.writeLong(state.availableStorage());
        buf.writeLong(state.totalStorage());
        buf.writeInt(state.parallelCount());
        buf.writeInt(state.accelerators());
        buf.writeEnum(state.cpuSelectionMode());
    }

    public static NEComputationUiState readComputation(FriendlyByteBuf buf) {
        return new NEComputationUiState(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readInt(),
                buf.readInt(),
                buf.readEnum(CpuSelectionMode.class));
    }

    public static void writeCrafting(FriendlyByteBuf buf, NECraftingUiState state) {
        buf.writeBlockPos(state.pos());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.active());
        buf.writeInt(state.workerCount());
        buf.writeInt(state.parallelCount());
        buf.writeInt(state.patternBusCount());
        buf.writeInt(state.threadCount());
        buf.writeInt(state.runningThreadCount());
        buf.writeBoolean(state.overclocked());
        buf.writeBoolean(state.activeCooling());
        buf.writeBoolean(state.autoClearCoolingWaste());
        buf.writeInt(state.selectedBuildLength());
        buf.writeBoolean(state.buildInProgress());
        buf.writeInt(state.previewMissingBlocks());
        buf.writeInt(state.previewConflictBlocks());
        buf.writeInt(state.previewReusedBlocks());
        buf.writeInt(state.previewRequiredItems());
        buf.writeUtf(state.previewStatusKey(), 256);
        buf.writeInt(state.previewStatusArg1());
        buf.writeInt(state.previewStatusArg2());
        buf.writeVarLong(state.energyUsage());
        buf.writeVarLong(state.coolantAmount());
        buf.writeVarLong(state.coolantCapacity());
        buf.writeVarInt(state.availableThreads());
        buf.writeVarInt(state.effectiveParallel());

        List<ItemStack> outputs = state.workerCraftOutputs();
        buf.writeVarInt(Math.min(outputs.size(), MAX_WORKER_OUTPUTS));
        int writtenOutputs = 0;
        for (ItemStack stack : outputs) {
            if (writtenOutputs++ >= MAX_WORKER_OUTPUTS) {
                break;
            }
            buf.writeItem(stack);
        }

        List<Integer> tiers = state.parallelCoreTiers();
        buf.writeVarInt(Math.min(tiers.size(), MAX_PARALLEL_CORE_TIERS));
        int writtenTiers = 0;
        for (int tier : tiers) {
            if (writtenTiers++ >= MAX_PARALLEL_CORE_TIERS) {
                break;
            }
            buf.writeVarInt(tier);
        }
    }

    public static NECraftingUiState readCrafting(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int workerCount = buf.readInt();
        int parallelCount = buf.readInt();
        int patternBusCount = buf.readInt();
        int threadCount = buf.readInt();
        int runningThreadCount = buf.readInt();
        boolean overclocked = buf.readBoolean();
        boolean activeCooling = buf.readBoolean();
        boolean autoClearCoolingWaste = buf.readBoolean();
        int selectedBuildLength = buf.readInt();
        boolean buildInProgress = buf.readBoolean();
        int previewMissingBlocks = buf.readInt();
        int previewConflictBlocks = buf.readInt();
        int previewReusedBlocks = buf.readInt();
        int previewRequiredItems = buf.readInt();
        String previewStatusKey = buf.readUtf(256);
        int previewStatusArg1 = buf.readInt();
        int previewStatusArg2 = buf.readInt();
        long energyUsage = buf.readVarLong();
        long coolantAmount = buf.readVarLong();
        long coolantCapacity = buf.readVarLong();
        int availableThreads = buf.readVarInt();
        int effectiveParallel = buf.readVarInt();

        int outputCount = buf.readVarInt();
        if (outputCount > MAX_WORKER_OUTPUTS) {
            throw new IllegalArgumentException("Crafting worker output count exceeds protocol limit: " + outputCount);
        }
        List<ItemStack> outputs = new ArrayList<>(outputCount);
        for (int i = 0; i < outputCount; i++) {
            outputs.add(buf.readItem());
        }

        int tierCount = buf.readVarInt();
        if (tierCount > MAX_PARALLEL_CORE_TIERS) {
            throw new IllegalArgumentException("Crafting parallel tier count exceeds protocol limit: " + tierCount);
        }
        List<Integer> tiers = new ArrayList<>(tierCount);
        for (int i = 0; i < tierCount; i++) {
            tiers.add(buf.readVarInt());
        }

        return new NECraftingUiState(
                pos,
                formed,
                active,
                workerCount,
                parallelCount,
                patternBusCount,
                threadCount,
                runningThreadCount,
                overclocked,
                activeCooling,
                autoClearCoolingWaste,
                selectedBuildLength,
                buildInProgress,
                previewMissingBlocks,
                previewConflictBlocks,
                previewReusedBlocks,
                previewRequiredItems,
                previewStatusKey,
                previewStatusArg1,
                previewStatusArg2,
                energyUsage,
                coolantAmount,
                coolantCapacity,
                availableThreads,
                effectiveParallel,
                outputs,
                tiers);
    }

    public static void writeIntegratedWorkingStation(FriendlyByteBuf buf, NEIntegratedWorkingStationUiState state) {
        buf.writeVarLong(Math.max(0, state.energy()));
        buf.writeVarLong(Math.max(0, state.maxEnergy()));
        buf.writeVarInt(Math.max(0, state.progress()));
        buf.writeVarInt(Math.max(0, state.maxProgress()));
        buf.writeVarInt(Math.max(0, state.requiredEnergy()));
        buf.writeBoolean(state.working());
        buf.writeBoolean(state.autoExport());
        state.inputFluid().writeToPacket(buf);
        state.outputFluid().writeToPacket(buf);
    }

    public static NEIntegratedWorkingStationUiState readIntegratedWorkingStation(FriendlyByteBuf buf) {
        return new NEIntegratedWorkingStationUiState(
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                FluidStack.readFromPacket(buf),
                FluidStack.readFromPacket(buf));
    }

    public static void writeStructureTerminal(FriendlyByteBuf buf, NEStructureTerminalConfigState state) {
        buf.writeVarInt(state.length());
        buf.writeVarInt(state.minLength());
        buf.writeVarInt(state.maxLength());
        buf.writeVarInt(state.tier());
        buf.writeEnum(state.hostType());
        buf.writeEnum(state.operationMode());
        List<cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry> materials =
                state.materials();
        buf.writeVarInt(Math.min(materials.size(), MAX_STRUCTURE_TERMINAL_MATERIALS));
        int written = 0;
        for (var material : materials) {
            if (written++ >= MAX_STRUCTURE_TERMINAL_MATERIALS) {
                break;
            }
            buf.writeItem(material.item());
            buf.writeVarInt(Math.max(0, material.required()));
            buf.writeVarInt(Math.max(0, material.available()));
        }
    }

    public static NEStructureTerminalConfigState readStructureTerminal(FriendlyByteBuf buf) {
        int length = buf.readVarInt();
        int minLength = buf.readVarInt();
        int maxLength = buf.readVarInt();
        int tier = buf.readVarInt();
        var hostType = buf.readEnum(cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType.class);
        var mode = buf.readEnum(cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode.class);
        int materialCount = buf.readVarInt();
        if (materialCount > MAX_STRUCTURE_TERMINAL_MATERIALS) {
            throw new IllegalArgumentException(
                    "Structure Terminal material count exceeds protocol limit: " + materialCount);
        }
        List<cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry> materials =
                new ArrayList<>(materialCount);
        for (int i = 0; i < materialCount; i++) {
            materials.add(new cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry(
                    buf.readItem(), buf.readVarInt(), buf.readVarInt()));
        }
        return new NEStructureTerminalConfigState(length, minLength, maxLength, tier, hostType, mode, materials);
    }

    private NELDLibStateCodecs() {}
}
