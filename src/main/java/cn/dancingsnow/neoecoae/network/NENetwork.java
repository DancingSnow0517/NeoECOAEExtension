package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.AbstractCraftingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEBaseMachineMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEIntegratedWorkingStationMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

/**
 * Unified mod network channel for UI state sync and future packets.
 * <p>
 * All machine UI S2C packets (Storage, Computation, Crafting, IWS, etc.)
 * share this single channel. New packet types are registered with an
 * incrementing index via {@link #register()}.
 * </p>
 */
public final class NENetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NeoECOAE.MOD_ID, "ui"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;
    private static final int MAX_STORAGE_UI_TYPES = 64;
    private static final int MAX_STRUCTURE_TERMINAL_MATERIALS = 64;

    public static void register() {
        registerS2C(
                NEStorageUiStatePacket.class,
                NEStorageUiStatePacket::encode,
                NEStorageUiStatePacket::decode,
                NEStorageUiStatePacket::handle);

        registerS2C(
                NEComputationUiStatePacket.class,
                NEComputationUiStatePacket::encode,
                NEComputationUiStatePacket::decode,
                NEComputationUiStatePacket::handle);

        registerS2C(
                NECraftingUiStatePacket.class,
                NECraftingUiStatePacket::encode,
                NECraftingUiStatePacket::decode,
                NECraftingUiStatePacket::handle);

        registerC2S(
                NECraftingUiActionPacket.class,
                NECraftingUiActionPacket::encode,
                NECraftingUiActionPacket::decode,
                NECraftingUiActionPacket::handle);

        registerC2S(
                NEOpenCraftingUiPacket.class,
                NEOpenCraftingUiPacket::encode,
                NEOpenCraftingUiPacket::decode,
                NEOpenCraftingUiPacket::handle);

        registerS2C(
                NEStructureTerminalUiStatePacket.class,
                NEStructureTerminalUiStatePacket::encode,
                NEStructureTerminalUiStatePacket::decode,
                NEStructureTerminalUiStatePacket::handle);

        registerC2S(
                NEStructureTerminalActionPacket.class,
                NEStructureTerminalActionPacket::encode,
                NEStructureTerminalActionPacket::decode,
                NEStructureTerminalActionPacket::handle);

        registerS2C(
                NEStructureTerminalConfigPacket.class,
                NEStructureTerminalConfigPacket::encode,
                NEStructureTerminalConfigPacket::decode,
                NEStructureTerminalConfigPacket::handle);

        registerC2S(
                NEStructureTerminalConfigActionPacket.class,
                NEStructureTerminalConfigActionPacket::encode,
                NEStructureTerminalConfigActionPacket::decode,
                NEStructureTerminalConfigActionPacket::handle);

        registerC2S(
                NEIntegratedWorkingStationActionPacket.class,
                NEIntegratedWorkingStationActionPacket::encode,
                NEIntegratedWorkingStationActionPacket::decode,
                NEIntegratedWorkingStationActionPacket::handle);

        registerS2C(
                NEIWSStatePacket.class, NEIWSStatePacket::encode, NEIWSStatePacket::decode, NEIWSStatePacket::handle);

        registerS2C(
                NEFluidHatchStatePacket.class,
                NEFluidHatchStatePacket::encode,
                NEFluidHatchStatePacket::decode,
                NEFluidHatchStatePacket::handle);
    }

    /**
     * Registers a PLAY_TO_CLIENT packet with an auto-incrementing id.
     * <p>
     * Keeps the {@link #register()} method readable as more machine UI
     * state packets are added in future phases.
     * </p>
     */
    @SuppressWarnings("SameParameterValue")
    private static <MSG> void registerS2C(
            Class<MSG> clazz,
            java.util.function.BiConsumer<MSG, FriendlyByteBuf> encoder,
            java.util.function.Function<FriendlyByteBuf, MSG> decoder,
            java.util.function.BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(
                packetId++, clazz, encoder, decoder, handler, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    @SuppressWarnings("SameParameterValue")
    private static <MSG> void registerC2S(
            Class<MSG> clazz,
            java.util.function.BiConsumer<MSG, FriendlyByteBuf> encoder,
            java.util.function.Function<FriendlyByteBuf, MSG> decoder,
            java.util.function.BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(
                packetId++, clazz, encoder, decoder, handler, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /**
     * S2C packet carrying a {@link NEStorageUiState} snapshot.
     * <p>
     * Encoder/decoder are common-safe. Client-side handling is delegated to
     * {@link NEClientUiPacketHandlers} via {@link DistExecutor} so that the
     * dedicated server never loads screen classes.
     * </p>
     */
    public record NEStorageUiStatePacket(NEStorageUiState state) {

        public static void encode(NEStorageUiStatePacket pkt, FriendlyByteBuf buf) {
            NEStorageUiState s = pkt.state();
            buf.writeBlockPos(s.pos());
            buf.writeLong(s.storedEnergy());
            buf.writeLong(s.maxEnergy());
            buf.writeBoolean(s.formed());

            List<NEStorageUiTypeState> types = s.typeStates();
            buf.writeVarInt(types.size());
            for (NEStorageUiTypeState ts : types) {
                buf.writeResourceLocation(ts.typeId());
                buf.writeUtf(ts.displayName(), 128);
                buf.writeLong(ts.usedTypes());
                buf.writeLong(ts.totalTypes());
                buf.writeLong(ts.usedBytes());
                buf.writeLong(ts.totalBytes());
            }
        }

        public static NEStorageUiStatePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            long storedEnergy = buf.readLong();
            long maxEnergy = buf.readLong();
            boolean formed = buf.readBoolean();

            int typeCount = buf.readVarInt();
            if (typeCount > MAX_STORAGE_UI_TYPES) {
                throw new IllegalArgumentException("Storage UI type count exceeds protocol limit: " + typeCount);
            }
            List<NEStorageUiTypeState> typeStates = new ArrayList<>(typeCount);
            for (int i = 0; i < typeCount; i++) {
                ResourceLocation typeId = buf.readResourceLocation();
                String displayName = buf.readUtf(128);
                long usedTypes = buf.readLong();
                long totalTypes = buf.readLong();
                long usedBytes = buf.readLong();
                long totalBytes = buf.readLong();
                typeStates.add(
                        new NEStorageUiTypeState(typeId, displayName, usedTypes, totalTypes, usedBytes, totalBytes));
            }

            return new NEStorageUiStatePacket(new NEStorageUiState(pos, typeStates, storedEnergy, maxEnergy, formed));
        }

        public static void handle(NEStorageUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleStorageUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * S2C packet carrying a {@link NEComputationUiState} snapshot.
     * <p>
     * Encoder/decoder are common-safe. Client-side handling is delegated to
     * {@link NEClientUiPacketHandlers} via {@link DistExecutor} so that the
     * dedicated server never loads screen classes.
     * </p>
     */
    public record NEComputationUiStatePacket(NEComputationUiState state) {

        public static void encode(NEComputationUiStatePacket pkt, FriendlyByteBuf buf) {
            NEComputationUiState s = pkt.state();
            buf.writeBlockPos(s.pos());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.active());
            buf.writeInt(s.usedThreads());
            buf.writeInt(s.maxThreads());
            buf.writeLong(s.availableStorage());
            buf.writeLong(s.totalStorage());
            buf.writeInt(s.parallelCount());
            buf.writeInt(s.accelerators());
        }

        public static NEComputationUiStatePacket decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean formed = buf.readBoolean();
            boolean active = buf.readBoolean();
            int usedThreads = buf.readInt();
            int maxThreads = buf.readInt();
            long availableStorage = buf.readLong();
            long totalStorage = buf.readLong();
            int parallelCount = buf.readInt();
            int accelerators = buf.readInt();

            return new NEComputationUiStatePacket(new NEComputationUiState(
                    pos,
                    formed,
                    active,
                    usedThreads,
                    maxThreads,
                    availableStorage,
                    totalStorage,
                    parallelCount,
                    accelerators));
        }

        public static void handle(NEComputationUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleComputationUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * S2C packet carrying a {@link NECraftingUiState} snapshot.
     */
    public record NECraftingUiStatePacket(NECraftingUiState state) {

        public static void encode(NECraftingUiStatePacket pkt, FriendlyByteBuf buf) {
            NECraftingUiState s = pkt.state();
            buf.writeBlockPos(s.pos());
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.active());
            buf.writeInt(s.workerCount());
            buf.writeInt(s.parallelCount());
            buf.writeInt(s.patternBusCount());
            buf.writeInt(s.threadCount());
            buf.writeInt(s.runningThreadCount());
            buf.writeBoolean(s.overclocked());
            buf.writeBoolean(s.activeCooling());
            buf.writeBoolean(s.autoClearCoolingWaste());
            buf.writeInt(s.selectedBuildLength());
            buf.writeBoolean(s.buildInProgress());
            buf.writeInt(s.previewMissingBlocks());
            buf.writeInt(s.previewConflictBlocks());
            buf.writeInt(s.previewReusedBlocks());
            buf.writeInt(s.previewRequiredItems());
            buf.writeUtf(s.previewStatusKey(), 256);
            buf.writeInt(s.previewStatusArg1());
            buf.writeInt(s.previewStatusArg2());
            buf.writeVarLong(s.energyUsage());
            buf.writeVarLong(s.coolantAmount());
            buf.writeVarLong(s.coolantCapacity());
            buf.writeVarInt(s.availableThreads());
            buf.writeVarInt(s.effectiveParallel());
        }

        public static NECraftingUiStatePacket decode(FriendlyByteBuf buf) {
            return new NECraftingUiStatePacket(new NECraftingUiState(
                    buf.readBlockPos(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(256),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt()));
        }

        public static void handle(NECraftingUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleCraftingUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * C2S packet for Crafting Controller action buttons.
     *
     * @deprecated Building operations have been migrated to
     *             {@link NEStructureTerminalActionPacket}.
     *             This packet is retained only for backward compatibility
     *             and will be removed in a future phase.
     */
    @Deprecated
    public record NECraftingUiActionPacket(BlockPos pos, Action action) {

        public enum Action {
            INCREASE_BUILD_LENGTH,
            DECREASE_BUILD_LENGTH,
            PREVIEW_STRUCTURE,
            AUTO_BUILD,
            TOGGLE_OVERCLOCK,
            TOGGLE_ACTIVE_COOLING,
            TOGGLE_AUTO_CLEAR_COOLING_WASTE
        }

        public static void encode(NECraftingUiActionPacket pkt, FriendlyByteBuf buf) {
            buf.writeBlockPos(pkt.pos());
            buf.writeEnum(pkt.action());
        }

        public static NECraftingUiActionPacket decode(FriendlyByteBuf buf) {
            return new NECraftingUiActionPacket(buf.readBlockPos(), buf.readEnum(Action.class));
        }

        public static void handle(NECraftingUiActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            var sender = ctx.getSender();
            if (sender == null) {
                ctx.setPacketHandled(true);
                return;
            }
            ctx.enqueueWork(() -> {
                // Server-side safety checks
                if (!(sender.level().getBlockEntity(pkt.pos()) instanceof ECOCraftingSystemBlockEntity be)) {
                    return;
                }
                if (!(sender.containerMenu instanceof NECraftingControllerMenu menu)) {
                    return;
                }
                if (!menu.getMachinePos().equals(pkt.pos())) {
                    return;
                }

                if (!menu.stillValid(sender)) {
                    return;
                }

                switch (pkt.action()) {
                    case INCREASE_BUILD_LENGTH -> be.increaseBuildLength();
                    case DECREASE_BUILD_LENGTH -> be.decreaseBuildLength();
                    case PREVIEW_STRUCTURE -> be.previewStructure(sender);
                    case AUTO_BUILD -> be.autoBuild(sender);
                    case TOGGLE_OVERCLOCK -> be.toggleOverclocked();
                    case TOGGLE_ACTIVE_COOLING -> be.toggleActiveCooling();
                    case TOGGLE_AUTO_CLEAR_COOLING_WASTE -> be.toggleAutoClearCoolingWaste();
                }

                // Immediately push updated state to the client
                menu.sendStateNow(sender);
            });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * C2S packet requesting to open the Crafting Controller UI from another
     * machine screen. The server resolves the target crafting controller
     * from the source machine position and opens the native crafting menu.
     */
    public record NEOpenCraftingUiPacket(BlockPos sourcePos) {

        public static void encode(NEOpenCraftingUiPacket pkt, FriendlyByteBuf buf) {
            buf.writeBlockPos(pkt.sourcePos());
        }

        public static NEOpenCraftingUiPacket decode(FriendlyByteBuf buf) {
            return new NEOpenCraftingUiPacket(buf.readBlockPos());
        }

        /**
         * Resolves the {@link ECOCraftingSystemBlockEntity} position from a
         * source block position. Returns {@code null} when the source is not
         * part of a crafting multiblock or the controller cannot be reliably
         * located.
         *
         * <p>Resolution rules (in order):
         * <ol>
         *   <li>If the source BE is itself an {@code ECOCraftingSystemBlockEntity},
         *       return its position.</li>
         *   <li>If the source BE is any {@link AbstractCraftingBlockEntity}
         *       (worker, pattern bus, parallel core, vent, fluid hatch, etc.),
         *       walk {@code cluster → controller} and return the controller
         *       position.</li>
         *   <li>Otherwise return {@code null} — Storage and Computation
         *       systems have no reliable path to a crafting controller.</li>
         * </ol>
         */
        private static @Nullable BlockPos resolveCraftingControllerPos(ServerPlayer player, BlockPos sourcePos) {
            var be = player.level().getBlockEntity(sourcePos);
            if (be instanceof ECOCraftingSystemBlockEntity controller) {
                return controller.getBlockPos();
            }
            if (be instanceof AbstractCraftingBlockEntity<?> craftingBe) {
                var cluster = craftingBe.getCluster();
                if (cluster != null) {
                    var controller = cluster.getController();
                    if (controller != null) {
                        return controller.getBlockPos();
                    }
                }
            }
            return null;
        }

        public static void handle(NEOpenCraftingUiPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            var sender = ctx.getSender();
            if (sender == null) {
                ctx.setPacketHandled(true);
                return;
            }
            ctx.enqueueWork(() -> {
                // 1. Verify the player has the expected menu open
                if (!(sender.containerMenu instanceof NEBaseMachineMenu menu)) {
                    return;
                }
                // 2. Verify the source position matches the open menu
                if (!menu.getMachinePos().equals(pkt.sourcePos())) {
                    return;
                }
                // 3. Verify the player is still within interaction range
                if (!menu.stillValid(sender)) {
                    return;
                }

                // 4. Resolve the actual crafting controller position
                BlockPos targetPos = resolveCraftingControllerPos(sender, pkt.sourcePos());
                if (targetPos == null) {
                    return;
                }

                // 5. Double-check the target BE is a crafting controller
                var targetBe = sender.level().getBlockEntity(targetPos);
                if (!(targetBe instanceof ECOCraftingSystemBlockEntity)) {
                    return;
                }

                // 6. Target must be in the same level as the player
                if (targetBe.getLevel() != sender.level()) {
                    return;
                }

                Component title = targetBe.getBlockState().getBlock().getName();

                NetworkHooks.openScreen(
                        (ServerPlayer) sender,
                        new SimpleMenuProvider(
                                (windowId, inv, p) -> new NECraftingControllerMenu(windowId, inv, targetPos), title),
                        buf -> buf.writeBlockPos(targetPos));
            });
            ctx.setPacketHandled(true);
        }
    }

    /**
     * S2C packet carrying a {@link NEStructureTerminalUiState} snapshot
     * for the Structure Terminal UI.
     */
    public record NEStructureTerminalUiStatePacket(NEStructureTerminalUiState state) {

        public static void encode(NEStructureTerminalUiStatePacket pkt, FriendlyByteBuf buf) {
            var s = pkt.state();
            buf.writeBlockPos(s.hostPos());
            buf.writeUtf(s.structureName(), 256);
            buf.writeBoolean(s.formed());
            buf.writeBoolean(s.buildInProgress());
            buf.writeInt(s.selectedBuildLength());
            buf.writeInt(s.minBuildLength());
            buf.writeInt(s.maxBuildLength());
            buf.writeInt(s.previewMissingBlocks());
            buf.writeInt(s.previewConflictBlocks());
            buf.writeInt(s.previewReusedBlocks());
            buf.writeInt(s.previewRequiredItems());
            buf.writeInt(s.placedBlocks());
            buf.writeInt(s.totalBlocks());
            buf.writeUtf(s.previewStatusKey(), 256);
            buf.writeInt(s.previewStatusArg1());
            buf.writeInt(s.previewStatusArg2());
            buf.writeVarInt(s.materials().size());
            for (var mat : s.materials()) {
                buf.writeItem(mat.item());
                buf.writeInt(mat.required());
                buf.writeInt(mat.available());
            }
        }

        public static NEStructureTerminalUiStatePacket decode(FriendlyByteBuf buf) {
            BlockPos hostPos = buf.readBlockPos();
            String structureName = buf.readUtf(256);
            boolean formed = buf.readBoolean();
            boolean buildInProgress = buf.readBoolean();
            int buildLength = buf.readInt();
            int minLen = buf.readInt();
            int maxLen = buf.readInt();
            int missing = buf.readInt();
            int conflicts = buf.readInt();
            int reused = buf.readInt();
            int required = buf.readInt();
            int placed = buf.readInt();
            int total = buf.readInt();
            String statusKey = buf.readUtf(256);
            int arg1 = buf.readInt();
            int arg2 = buf.readInt();
            int matCount = buf.readVarInt();
            if (matCount > MAX_STRUCTURE_TERMINAL_MATERIALS) {
                throw new IllegalArgumentException(
                        "Structure Terminal material count exceeds protocol limit: " + matCount);
            }
            var mats = new java.util.ArrayList<NEStructureTerminalUiState.BuildMaterialEntry>(matCount);
            for (int i = 0; i < matCount; i++) {
                mats.add(new NEStructureTerminalUiState.BuildMaterialEntry(
                        buf.readItem(), buf.readInt(), buf.readInt()));
            }
            return new NEStructureTerminalUiStatePacket(new NEStructureTerminalUiState(
                    hostPos,
                    structureName,
                    formed,
                    buildInProgress,
                    buildLength,
                    minLen,
                    maxLen,
                    missing,
                    conflicts,
                    reused,
                    required,
                    placed,
                    total,
                    statusKey,
                    arg1,
                    arg2,
                    mats));
        }

        public static void handle(NEStructureTerminalUiStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleStructureTerminalUiState(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * C2S packet for Structure Terminal action buttons.
     */
    public record NEStructureTerminalActionPacket(BlockPos pos, Action action) {

        public enum Action {
            INCREASE_BUILD_LENGTH,
            DECREASE_BUILD_LENGTH,
            PREVIEW_STRUCTURE,
            AUTO_BUILD
        }

        public static void encode(NEStructureTerminalActionPacket pkt, FriendlyByteBuf buf) {
            buf.writeBlockPos(pkt.pos());
            buf.writeEnum(pkt.action());
        }

        public static NEStructureTerminalActionPacket decode(FriendlyByteBuf buf) {
            return new NEStructureTerminalActionPacket(buf.readBlockPos(), buf.readEnum(Action.class));
        }

        public static void handle(NEStructureTerminalActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            // Deprecated packet — now handled via StructureTerminalItem.useOn()
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.setPacketHandled(true);
        }
    }

    /**
     * S2C packet carrying the current build length + range for the
     * Structure Terminal config UI.
     */
    public record NEStructureTerminalConfigPacket(
            int currentLength,
            int minLength,
            int maxLength,
            StructureTerminalHostType hostType,
            StructureTerminalMode operationMode,
            List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {

        public static void encode(NEStructureTerminalConfigPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.currentLength());
            buf.writeInt(pkt.minLength());
            buf.writeInt(pkt.maxLength());
            buf.writeEnum(pkt.hostType());
            buf.writeEnum(pkt.operationMode());
            buf.writeVarInt(Math.min(pkt.materials().size(), MAX_STRUCTURE_TERMINAL_MATERIALS));
            int written = 0;
            for (var mat : pkt.materials()) {
                if (written++ >= MAX_STRUCTURE_TERMINAL_MATERIALS) {
                    break;
                }
                buf.writeItem(mat.item());
                buf.writeInt(mat.required());
                buf.writeInt(mat.available());
            }
        }

        public static NEStructureTerminalConfigPacket decode(FriendlyByteBuf buf) {
            int currentLength = buf.readInt();
            int minLength = buf.readInt();
            int maxLength = buf.readInt();
            StructureTerminalHostType hostType = buf.readEnum(StructureTerminalHostType.class);
            StructureTerminalMode operationMode = buf.readEnum(StructureTerminalMode.class);
            int matCount = buf.readVarInt();
            if (matCount > MAX_STRUCTURE_TERMINAL_MATERIALS) {
                throw new IllegalArgumentException(
                        "Structure Terminal config material count exceeds protocol limit: " + matCount);
            }
            var mats = new java.util.ArrayList<NEStructureTerminalUiState.BuildMaterialEntry>(matCount);
            for (int i = 0; i < matCount; i++) {
                mats.add(new NEStructureTerminalUiState.BuildMaterialEntry(
                        buf.readItem(), buf.readInt(), buf.readInt()));
            }
            return new NEStructureTerminalConfigPacket(
                    currentLength, minLength, maxLength, hostType, operationMode, mats);
        }

        public static void handle(NEStructureTerminalConfigPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleStructureTerminalConfig(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    /**
     * C2S packet for Structure Terminal config actions (+/-/reset).
     * Does not carry a BlockPos — the server reads the hand from the
     * currently open menu.
     */
    public record NEStructureTerminalConfigActionPacket(Action action) {

        public enum Action {
            INCREASE,
            DECREASE,
            RESET,
            SELECT_CRAFTING,
            SELECT_STORAGE,
            SELECT_COMPUTATION,
            SELECT_BUILD_MODE,
            SELECT_MIRRORED_BUILD_MODE,
            SELECT_DISMANTLE_MODE
        }

        public static void encode(NEStructureTerminalConfigActionPacket pkt, FriendlyByteBuf buf) {
            buf.writeEnum(pkt.action());
        }

        public static NEStructureTerminalConfigActionPacket decode(FriendlyByteBuf buf) {
            return new NEStructureTerminalConfigActionPacket(buf.readEnum(Action.class));
        }

        public static void handle(
                NEStructureTerminalConfigActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            var sender = ctx.getSender();
            if (sender == null) {
                ctx.setPacketHandled(true);
                return;
            }
            ctx.enqueueWork(() -> {
                if (!(sender.containerMenu instanceof NEStructureTerminalMenu menu)) {
                    return;
                }
                if (!menu.stillValid(sender)) {
                    return;
                }
                var stack = menu.getTerminalStack(sender);
                if (stack == null) {
                    return;
                }

                int current = StructureTerminalItem.getBuildLength(stack);
                switch (pkt.action()) {
                    case SELECT_CRAFTING -> StructureTerminalItem.setHostType(
                            stack, StructureTerminalHostType.CRAFTING);
                    case SELECT_STORAGE -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.STORAGE);
                    case SELECT_COMPUTATION -> StructureTerminalItem.setHostType(
                            stack, StructureTerminalHostType.COMPUTATION);
                    case SELECT_BUILD_MODE -> StructureTerminalItem.setOperationMode(
                            stack, StructureTerminalMode.BUILD);
                    case SELECT_MIRRORED_BUILD_MODE -> StructureTerminalItem.setOperationMode(
                            stack, StructureTerminalMode.MIRRORED_BUILD);
                    case SELECT_DISMANTLE_MODE -> StructureTerminalItem.setOperationMode(
                            stack, StructureTerminalMode.DISMANTLE);
                    default -> {
                        int newLength =
                                switch (pkt.action()) {
                                    case INCREASE -> current + 1;
                                    case DECREASE -> current - 1;
                                    case RESET -> StructureTerminalItem.DEFAULT_BUILD_LENGTH;
                                    default -> current;
                                };
                        StructureTerminalItem.setBuildLength(stack, newLength);
                    }
                }
                ;

                // Sync fresh value from NBT (not cached value) to client
                menu.syncToClient(sender);
            });
            ctx.setPacketHandled(true);
        }
    }

    // ── IWS (Integrated Working Station) action packet ──

    public enum IWSAction {
        TOGGLE_AUTO_EXPORT,
        CLEAR_INPUT_FLUID,
        CLEAR_OUTPUT_FLUID,
        INPUT_TANK_CONTAINER_CLICK,
        /** Client requests current fluid/auto-export state without changing anything. */
        REQUEST_STATE
    }

    public record NEIntegratedWorkingStationActionPacket(BlockPos pos, IWSAction action) {

        public static void encode(NEIntegratedWorkingStationActionPacket pkt, FriendlyByteBuf buf) {
            buf.writeBlockPos(pkt.pos());
            buf.writeEnum(pkt.action());
        }

        public static NEIntegratedWorkingStationActionPacket decode(FriendlyByteBuf buf) {
            return new NEIntegratedWorkingStationActionPacket(buf.readBlockPos(), buf.readEnum(IWSAction.class));
        }

        public static void handle(
                NEIntegratedWorkingStationActionPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            var sender = ctx.getSender();
            if (sender == null) {
                ctx.setPacketHandled(true);
                return;
            }
            ctx.enqueueWork(() -> {
                if (!(sender.containerMenu instanceof NEIntegratedWorkingStationMenu menu)) {
                    return;
                }
                if (!menu.getMachinePos().equals(pkt.pos())) {
                    return;
                }
                if (!menu.stillValid(sender)) {
                    return;
                }
                var be = sender.level().getBlockEntity(pkt.pos());
                if (!(be instanceof ECOIntegratedWorkingStationBlockEntity iws)) {
                    return;
                }
                switch (pkt.action()) {
                    case REQUEST_STATE -> {
                        // Only sync state back, no machine changes
                        sendIwsStateTo(sender, iws);
                        ctx.setPacketHandled(true);
                        return;
                    }
                    case TOGGLE_AUTO_EXPORT -> iws.toggleAutoExport();
                    case CLEAR_INPUT_FLUID -> iws.clearFluid();
                    case CLEAR_OUTPUT_FLUID -> iws.clearFluidOut();
                    case INPUT_TANK_CONTAINER_CLICK -> iws.handleInputTankContainerClick(sender);
                }
                iws.setChanged();
                iws.markForUpdate();
                menu.broadcastChanges();
                // Send updated state back to this client immediately
                sendIwsStateTo(sender, iws);
            });
            ctx.setPacketHandled(true);
        }
    }

    // ── IWS state sync packet (S2C, sent after every IWSAction) ──

    public record NEIWSStatePacket(
            BlockPos pos, CompoundTag inputTankTag, CompoundTag outputTankTag, boolean autoExport) {
        public static void encode(NEIWSStatePacket pkt, FriendlyByteBuf buf) {
            buf.writeBlockPos(pkt.pos());
            buf.writeNbt(pkt.inputTankTag());
            buf.writeNbt(pkt.outputTankTag());
            buf.writeBoolean(pkt.autoExport());
        }

        public static NEIWSStatePacket decode(FriendlyByteBuf buf) {
            return new NEIWSStatePacket(buf.readBlockPos(), buf.readNbt(), buf.readNbt(), buf.readBoolean());
        }

        public static void handle(NEIWSStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleIwsStatePacket(pkt)));
            ctx.setPacketHandled(true);
        }
    }

    private static void sendIwsStateTo(ServerPlayer player, ECOIntegratedWorkingStationBlockEntity iws) {
        var inputTag = new CompoundTag();
        var outputTag = new CompoundTag();
        iws.getInputTank().writeToNBT(inputTag);
        iws.getOutputTank().writeToNBT(outputTag);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new NEIWSStatePacket(iws.getBlockPos(), inputTag, outputTag, iws.isShouldAutoExport()));
    }

    public record NEFluidHatchStatePacket(BlockPos pos, CompoundTag tankTag) {
        public static void encode(NEFluidHatchStatePacket pkt, FriendlyByteBuf buf) {
            buf.writeBlockPos(pkt.pos());
            buf.writeNbt(pkt.tankTag());
        }

        public static NEFluidHatchStatePacket decode(FriendlyByteBuf buf) {
            return new NEFluidHatchStatePacket(buf.readBlockPos(), buf.readNbt());
        }

        public static void handle(NEFluidHatchStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> NEClientUiPacketHandlers.handleFluidHatchStatePacket(pkt)));
            ctx.setPacketHandled(true);
        }
    }
}
