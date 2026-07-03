package cn.dancingsnow.neoecoae.gui.ldlib.support;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingModuleCell;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingRecipeUiEntry;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NECraftingUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageInterfaceUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageHugeStackState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class NELDLibStateCodecs {
    private static final int MAX_STORAGE_UI_TYPES = 64;
    private static final int MAX_STORAGE_DRIVES = 384;
    private static final int MAX_STORAGE_HUGE_STACKS = 128;
    private static final int MAX_CRAFTING_RECIPE_ENTRIES = 64;
    private static final int MAX_WORKER_OUTPUTS = 128;
    private static final int MAX_PARALLEL_CORE_TIERS = 128;
    private static final int MAX_CRAFTING_MODULE_CELLS = 384;
    private static final int MAX_STRUCTURE_TERMINAL_MATERIALS = 512;

    public static void writeStorage(FriendlyByteBuf buf, NEStorageUiState state) {
        buf.writeBlockPos(state.pos());
        buf.writeLong(state.storedEnergy());
        buf.writeLong(state.maxEnergy());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.infiniteSlotVisible());
        buf.writeBoolean(state.infiniteMode());
        buf.writeVarInt(Math.max(0, state.infiniteComponentCount()));
        buf.writeBoolean(state.canTakeInfiniteComponent());
        buf.writeBoolean(state.infiniteDomainEmpty());
        List<NEStorageUiMatrixState> matrices = state.matrixStates();
        buf.writeVarInt(Math.min(matrices.size(), MAX_STORAGE_DRIVES));
        int matricesWritten = 0;
        for (NEStorageUiMatrixState matrix : matrices) {
            if (matricesWritten++ >= MAX_STORAGE_DRIVES) {
                break;
            }
            buf.writeVarInt(matrix.row());
            buf.writeVarInt(matrix.column());
            buf.writeItem(matrix.stack());
            buf.writeVarInt(matrix.tier());
            buf.writeLong(matrix.usedTypes());
            buf.writeLong(matrix.totalTypes());
            buf.writeLong(matrix.usedBytes());
            buf.writeLong(matrix.totalBytes());
            buf.writeBoolean(matrix.infiniteMember());
        }
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
            buf.writeUtf(type.safeUsedAmount(), 128);
        }
        List<NEStorageHugeStackState> hugeStacks = state.hugeStacks();
        buf.writeVarInt(Math.min(hugeStacks.size(), MAX_STORAGE_HUGE_STACKS));
        int hugeWritten = 0;
        for (NEStorageHugeStackState hugeStack : hugeStacks) {
            if (hugeWritten++ >= MAX_STORAGE_HUGE_STACKS) {
                break;
            }
            AEKey.writeKey(buf, hugeStack.key());
            buf.writeUtf(hugeStack.amount(), 128);
        }
    }

    public static NEStorageUiState readStorage(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long storedEnergy = buf.readLong();
        long maxEnergy = buf.readLong();
        boolean formed = buf.readBoolean();
        boolean infiniteSlotVisible = buf.readBoolean();
        boolean infiniteMode = buf.readBoolean();
        int infiniteComponentCount = buf.readVarInt();
        boolean canTakeInfiniteComponent = buf.readBoolean();
        boolean infiniteDomainEmpty = buf.readBoolean();
        int matrixCount = buf.readVarInt();
        if (matrixCount > MAX_STORAGE_DRIVES) {
            throw new IllegalArgumentException("Storage drive count exceeds protocol limit: " + matrixCount);
        }
        List<NEStorageUiMatrixState> matrices = new ArrayList<>(matrixCount);
        for (int i = 0; i < matrixCount; i++) {
            matrices.add(new NEStorageUiMatrixState(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readItem(),
                    buf.readVarInt(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readBoolean()));
        }
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
                    buf.readLong(),
                    buf.readUtf(128)));
        }
        int hugeStackCount = buf.readVarInt();
        if (hugeStackCount > MAX_STORAGE_HUGE_STACKS) {
            throw new IllegalArgumentException("Storage huge stack count exceeds protocol limit: " + hugeStackCount);
        }
        List<NEStorageHugeStackState> hugeStacks = new ArrayList<>(hugeStackCount);
        for (int i = 0; i < hugeStackCount; i++) {
            hugeStacks.add(new NEStorageHugeStackState(AEKey.readKey(buf), buf.readUtf(128)));
        }
        return new NEStorageUiState(
                pos,
                types,
                matrices,
                hugeStacks,
                storedEnergy,
                maxEnergy,
                formed,
                infiniteSlotVisible,
                infiniteMode,
                infiniteComponentCount,
                canTakeInfiniteComponent,
                infiniteDomainEmpty);
    }

    public static void writeStorageInterface(FriendlyByteBuf buf, NEStorageInterfaceUiState state) {
        buf.writeBlockPos(state.pos());
        buf.writeBoolean(state.formed());
        buf.writeEnum(state.mode());
        buf.writeLong(Math.max(0L, state.exportedLastTick()));
        buf.writeLong(Math.max(0L, state.exportedTotal()));
        buf.writeBoolean(state.targetOnline());
        buf.writeBoolean(state.hasController());
    }

    public static NEStorageInterfaceUiState readStorageInterface(FriendlyByteBuf buf) {
        return new NEStorageInterfaceUiState(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readEnum(ECOStorageInterfaceMode.class),
                buf.readLong(),
                buf.readLong(),
                buf.readBoolean(),
                buf.readBoolean());
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
        List<NECraftingRecipeUiEntry> recipes = state.recipeEntries();
        buf.writeVarInt(Math.min(recipes.size(), MAX_CRAFTING_RECIPE_ENTRIES));
        int writtenRecipes = 0;
        for (NECraftingRecipeUiEntry entry : recipes) {
            if (writtenRecipes++ >= MAX_CRAFTING_RECIPE_ENTRIES) {
                break;
            }
            buf.writeUtf(entry.id(), 128);
            buf.writeItem(entry.output());
            buf.writeVarLong(Math.max(0L, entry.outputAmount()));
            buf.writeVarLong(Math.max(0L, entry.craftCount()));
            buf.writeVarLong(Math.max(0L, entry.totalTicks()));
            buf.writeVarLong(Math.max(0L, entry.remainingTicks()));
            buf.writeEnum(entry.status());
        }
    }

    public static NEComputationUiState readComputation(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int usedThreads = buf.readInt();
        int maxThreads = buf.readInt();
        long availableStorage = buf.readLong();
        long totalStorage = buf.readLong();
        int parallelCount = buf.readInt();
        int accelerators = buf.readInt();
        CpuSelectionMode cpuSelectionMode = buf.readEnum(CpuSelectionMode.class);
        int recipeCount = buf.readVarInt();
        if (recipeCount > MAX_CRAFTING_RECIPE_ENTRIES) {
            throw new IllegalArgumentException("Computation recipe entry count exceeds protocol limit: " + recipeCount);
        }
        List<NECraftingRecipeUiEntry> recipes = new ArrayList<>(recipeCount);
        for (int i = 0; i < recipeCount; i++) {
            recipes.add(new NECraftingRecipeUiEntry(
                    buf.readUtf(128),
                    buf.readItem(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readEnum(NECraftingRecipeUiEntry.Status.class)));
        }
        return new NEComputationUiState(
                pos,
                formed,
                active,
                usedThreads,
                maxThreads,
                availableStorage,
                totalStorage,
                parallelCount,
                accelerators,
                cpuSelectionMode,
                recipes);
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
        buf.writeUtf(state.coolantFluidId(), 256);
        buf.writeVarInt(state.coolantMaxOverclock());
        buf.writeVarInt(state.availableThreads());
        buf.writeVarInt(state.effectiveParallel());
        buf.writeVarInt(state.maxRecipeSlots());
        buf.writeVarInt(state.occupiedRecipeSlots());
        buf.writeVarInt(state.batchParallel());
        buf.writeVarLong(Math.max(0L, state.performanceAverageNanos()));

        List<NECraftingRecipeUiEntry> recipes = state.recipeEntries();
        buf.writeVarInt(Math.min(recipes.size(), MAX_CRAFTING_RECIPE_ENTRIES));
        int writtenRecipes = 0;
        for (NECraftingRecipeUiEntry entry : recipes) {
            if (writtenRecipes++ >= MAX_CRAFTING_RECIPE_ENTRIES) {
                break;
            }
            buf.writeUtf(entry.id(), 128);
            buf.writeItem(entry.output());
            buf.writeVarLong(Math.max(0L, entry.outputAmount()));
            buf.writeVarLong(Math.max(0L, entry.craftCount()));
            buf.writeVarLong(Math.max(0L, entry.totalTicks()));
            buf.writeVarLong(Math.max(0L, entry.remainingTicks()));
            buf.writeEnum(entry.status());
        }

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

        List<NECraftingModuleCell> moduleCells = state.moduleCells();
        buf.writeVarInt(Math.min(moduleCells.size(), MAX_CRAFTING_MODULE_CELLS));
        int writtenCells = 0;
        for (NECraftingModuleCell cell : moduleCells) {
            if (writtenCells++ >= MAX_CRAFTING_MODULE_CELLS) {
                break;
            }
            buf.writeVarInt(cell.column());
            buf.writeEnum(cell.row());
            buf.writeVarInt(cell.tier());
            buf.writeBlockPos(cell.pos());
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
        String coolantFluidId = buf.readUtf(256);
        int coolantMaxOverclock = buf.readVarInt();
        int availableThreads = buf.readVarInt();
        int effectiveParallel = buf.readVarInt();
        int maxRecipeSlots = buf.readVarInt();
        int occupiedRecipeSlots = buf.readVarInt();
        int batchParallel = buf.readVarInt();
        long performanceAverageNanos = buf.readVarLong();

        int recipeCount = buf.readVarInt();
        if (recipeCount > MAX_CRAFTING_RECIPE_ENTRIES) {
            throw new IllegalArgumentException("Crafting recipe entry count exceeds protocol limit: " + recipeCount);
        }
        List<NECraftingRecipeUiEntry> recipes = new ArrayList<>(recipeCount);
        for (int i = 0; i < recipeCount; i++) {
            recipes.add(new NECraftingRecipeUiEntry(
                    buf.readUtf(128),
                    buf.readItem(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readEnum(NECraftingRecipeUiEntry.Status.class)));
        }

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

        int moduleCellCount = buf.readVarInt();
        if (moduleCellCount > MAX_CRAFTING_MODULE_CELLS) {
            throw new IllegalArgumentException("Crafting module cell count exceeds protocol limit: " + moduleCellCount);
        }
        List<NECraftingModuleCell> moduleCells = new ArrayList<>(moduleCellCount);
        for (int i = 0; i < moduleCellCount; i++) {
            moduleCells.add(new NECraftingModuleCell(
                    buf.readVarInt(),
                    buf.readEnum(NECraftingModuleCell.Row.class),
                    buf.readVarInt(),
                    buf.readBlockPos()));
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
                coolantFluidId,
                coolantMaxOverclock,
                availableThreads,
                effectiveParallel,
                maxRecipeSlots,
                occupiedRecipeSlots,
                batchParallel,
                performanceAverageNanos,
                recipes,
                outputs,
                tiers,
                moduleCells);
    }

    public static void writeIntegratedWorkingStation(FriendlyByteBuf buf, NEIntegratedWorkingStationUiState state) {
        buf.writeVarLong(Math.max(0, state.energy()));
        buf.writeVarLong(Math.max(0, state.maxEnergy()));
        buf.writeVarInt(Math.max(0, state.progress()));
        buf.writeVarInt(Math.max(0, state.maxProgress()));
        buf.writeVarInt(Math.max(0, state.requiredEnergy()));
        buf.writeBoolean(state.working());
        buf.writeBoolean(state.autoExport());
    }

    public static NEIntegratedWorkingStationUiState readIntegratedWorkingStation(FriendlyByteBuf buf) {
        return new NEIntegratedWorkingStationUiState(
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    public static void writeStructureTerminal(FriendlyByteBuf buf, NEStructureTerminalConfigState state) {
        buf.writeVarInt(state.length());
        buf.writeVarInt(state.minLength());
        buf.writeVarInt(state.maxLength());
        buf.writeVarInt(state.tier());
        buf.writeEnum(state.hostType());
        buf.writeEnum(state.operationMode());
        buf.writeBoolean(state.operationModePending());
        buf.writeBoolean(state.previewMirrored());
        buf.writeBoolean(state.previewFormed());
        buf.writeVarInt(state.previewLayer());
        buf.writeVarInt(state.previewMaterialScroll());
        buf.writeBoolean(state.linkedHost());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.buildInProgress());
        buf.writeVarInt(state.previewMissingBlocks());
        buf.writeVarInt(state.previewConflictBlocks());
        buf.writeVarInt(state.previewReusedBlocks());
        buf.writeVarInt(state.previewRequiredItems());
        buf.writeUtf(state.previewStatusKey(), 256);
        buf.writeVarInt(state.previewStatusArg1());
        buf.writeVarInt(state.previewStatusArg2());
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
        boolean modePending = buf.readBoolean();
        boolean previewMirrored = buf.readBoolean();
        boolean previewFormed = buf.readBoolean();
        int previewLayer = buf.readVarInt();
        int previewMaterialScroll = buf.readVarInt();
        boolean linkedHost = buf.readBoolean();
        boolean formed = buf.readBoolean();
        boolean buildInProgress = buf.readBoolean();
        int previewMissingBlocks = buf.readVarInt();
        int previewConflictBlocks = buf.readVarInt();
        int previewReusedBlocks = buf.readVarInt();
        int previewRequiredItems = buf.readVarInt();
        String previewStatusKey = buf.readUtf(256);
        int previewStatusArg1 = buf.readVarInt();
        int previewStatusArg2 = buf.readVarInt();
        int materialCount = readBoundedNonNegativeVarInt(
                buf, MAX_STRUCTURE_TERMINAL_MATERIALS, "Structure Terminal material count");
        List<cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry> materials =
                new ArrayList<>(materialCount);
        for (int i = 0; i < materialCount; i++) {
            materials.add(new cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry(
                    buf.readItem(), buf.readVarInt(), buf.readVarInt()));
        }
        return new NEStructureTerminalConfigState(
                length,
                minLength,
                maxLength,
                tier,
                hostType,
                mode,
                modePending,
                previewMirrored,
                previewFormed,
                previewLayer,
                previewMaterialScroll,
                linkedHost,
                formed,
                buildInProgress,
                previewMissingBlocks,
                previewConflictBlocks,
                previewReusedBlocks,
                previewRequiredItems,
                previewStatusKey,
                previewStatusArg1,
                previewStatusArg2,
                materials);
    }

    private static int readBoundedNonNegativeVarInt(FriendlyByteBuf buf, int max, String fieldName) {
        int value = buf.readVarInt();
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(fieldName + " outside protocol limit: " + value);
        }
        return value;
    }

    private NELDLibStateCodecs() {}
}
