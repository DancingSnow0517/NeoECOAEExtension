package cn.dancingsnow.neoecoae.compat.ae2;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens GT's own UI through LDLib, without requiring a client-loaded target chunk or linking GT classes. */
final class ECORemoteMachineUI {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");
    private record Holder(BlockEntity blockEntity, IUIHolder machine, CompoundTag updateTag) {}
    private static final UIFactory<Holder> FACTORY = new UIFactory<>(new ResourceLocation("neoecoae", "remote_pattern_machine")) {
        @Override protected ModularUI createUITemplate(Holder holder, Player player) {
            if (holder == null) return null;
            try {
                var ui = holder.machine().createUI(player);
                if (ui == null) LOGGER.warn("Remote machine {} at {} returned no UI on {}",
                    holder.machine().getClass().getName(), holder.blockEntity().getBlockPos(),
                    player.level().isClientSide ? "client" : "server");
                return ui;
            } catch (RuntimeException | LinkageError unsupported) {
                LOGGER.warn("Failed to create remote machine UI for {} at {}", holder.machine().getClass().getName(),
                    holder.blockEntity().getBlockPos(), unsupported);
                return null;
            }
        }

        @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
        @Override protected Holder readHolderFromSyncData(FriendlyByteBuf buffer) {
            return FMLEnvironment.dist == Dist.CLIENT ? ClientHolderReader.read(buffer) : null;
        }

        @Override protected void writeHolderToSyncData(FriendlyByteBuf buffer, Holder holder) {
            var be = holder.blockEntity();
            buffer.writeBlockPos(be.getBlockPos());
            buffer.writeVarInt(Block.getId(be.getBlockState()));
            buffer.writeNbt(holder.updateTag());
        }
    };

    // The factory is registered on both physical sides. Keep ClientLevel references out of
    // its class so JVM verification during dedicated-server initialization cannot load them.
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static final class ClientHolderReader {
        private static Holder read(FriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var state = Block.stateById(buffer.readVarInt());
            var data = buffer.readNbt();
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (data == null || level == null) {
                LOGGER.warn("Cannot restore remote machine UI at {}: missing update tag or client level", pos);
                return null;
            }
            var id = ResourceLocation.tryParse(data.getString("id"));
            var type = id == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id);
            if (type == null || !type.isValid(state)) {
                LOGGER.warn("Cannot restore remote machine UI host {} at {} for block state {}", id, pos, state);
                return null;
            }
            try {
                // GT's native factory uses the world's machine, including its initialized UI state.
                // Read the complete snapshot above even when reusing it: widget data follows in this buffer.
                var blockEntity = level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
                if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getType() != type
                    || !blockEntity.getBlockState().is(state.getBlock())) {
                    blockEntity = type.create(pos, state);
                    if (blockEntity == null) {
                        LOGGER.warn("Remote machine UI host {} at {} could not be created", id, pos);
                        return null;
                    }
                    // Distant targets still use a snapshot; listeners need the level before applying it.
                    blockEntity.setLevel(level);
                    blockEntity.handleUpdateTag(data);
                }
                if (!(ECOProviderActions.member(blockEntity, "getMetaMachine") instanceof IUIHolder machine)) {
                    LOGGER.warn("Remote machine UI host {} at {} does not expose an LDLib UI holder", id, pos);
                    return null;
                }
                return new Holder(blockEntity, machine, data);
            } catch (RuntimeException | LinkageError unsupported) {
                LOGGER.warn("Failed to restore remote machine UI host {} at {}", id, pos, unsupported);
                return null;
            }
        }
    }

    static void initialize() { UIFactory.register(FACTORY); }
    static boolean open(BlockEntity blockEntity, Object machine, ServerPlayer player) {
        if (!(machine instanceof IUIHolder uiHolder)) {
            LOGGER.warn("Remote machine UI host at {} does not expose an LDLib UI holder", blockEntity.getBlockPos());
            return false;
        }
        Holder holder;
        try {
            // Capture client fields before LDLib closes the current menu. Persisted inventories are not a UI snapshot.
            var data = blockEntity.getUpdateTag().copy();
            data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
            holder = new Holder(blockEntity, uiHolder, data);
        } catch (RuntimeException | LinkageError unsupported) {
            LOGGER.warn("Failed to capture remote machine UI host {} at {}", machine.getClass().getName(),
                blockEntity.getBlockPos(), unsupported);
            return false;
        }
        try {
            return FACTORY.openUI(holder, player);
        } catch (RuntimeException | LinkageError unsupported) {
            LOGGER.warn("Failed to open remote machine UI for {} at {}", machine.getClass().getName(),
                blockEntity.getBlockPos(), unsupported);
            return false;
        }
    }
}
