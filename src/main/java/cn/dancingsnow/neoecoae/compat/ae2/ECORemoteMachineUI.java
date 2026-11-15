package cn.dancingsnow.neoecoae.compat.ae2;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private record Holder(BlockEntity blockEntity, Object machine) {}
    private static final UIFactory<Holder> FACTORY = new UIFactory<>(new ResourceLocation("neoecoae", "remote_pattern_machine")) {
        @Override protected ModularUI createUITemplate(Holder holder, Player player) {
            if (holder == null || !(holder.machine() instanceof IUIHolder machine)) return null;
            try {
                return machine.createUI(player);
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
            // LDLib's update tag contains the client fields and avoids loading server-only persisted inventories.
            var data = be.getUpdateTag().copy();
            data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
            buffer.writeNbt(data);
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
            if (data == null || level == null) return null;
            var id = ResourceLocation.tryParse(data.getString("id"));
            var type = id == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id);
            if (type == null || !type.isValid(state)) return null;
            try {
                var blockEntity = type.create(pos, state);
                if (blockEntity == null) return null;
                // Sync listeners may inspect the world while applying the opening snapshot.
                blockEntity.setLevel(level);
                blockEntity.handleUpdateTag(data);
                return new Holder(blockEntity, ECOProviderActions.member(blockEntity, "getMetaMachine"));
            } catch (RuntimeException | LinkageError unsupported) {
                LOGGER.warn("Failed to restore remote machine UI host {} at {}", id, pos, unsupported);
                return null;
            }
        }
    }

    static void initialize() { UIFactory.register(FACTORY); }
    static boolean open(BlockEntity blockEntity, Object machine, ServerPlayer player) {
        return FACTORY.openUI(new Holder(blockEntity, machine), player);
    }
}
