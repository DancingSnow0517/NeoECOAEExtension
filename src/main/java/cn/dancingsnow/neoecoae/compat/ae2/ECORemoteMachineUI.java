package cn.dancingsnow.neoecoae.compat.ae2;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Opens GT's own UI through LDLib, without requiring a client-loaded target chunk or linking GT classes. */
final class ECORemoteMachineUI {
    private record Holder(BlockEntity blockEntity, Object machine) {}
    private static final UIFactory<Holder> FACTORY = new UIFactory<>(new ResourceLocation("neoecoae", "remote_pattern_machine")) {
        @Override protected ModularUI createUITemplate(Holder holder, Player player) {
            if (holder == null || holder.machine() == null) return null;
            try {
                return (ModularUI) holder.machine().getClass().getMethod("createUI", Player.class).invoke(holder.machine(), player);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { return null; }
        }

        @Override protected Holder readHolderFromSyncData(FriendlyByteBuf buffer) {
            return FMLEnvironment.dist == Dist.CLIENT ? ClientHolderReader.read(buffer) : null;
        }

        @Override protected void writeHolderToSyncData(FriendlyByteBuf buffer, Holder holder) {
            var be = holder.blockEntity();
            buffer.writeBlockPos(be.getBlockPos());
            buffer.writeVarInt(Block.getId(be.getBlockState()));
            buffer.writeNbt(be.saveWithFullMetadata());
        }
    };

    // The factory is registered on both physical sides. Keep ClientLevel references out of
    // its class so JVM verification during dedicated-server initialization cannot load them.
    private static final class ClientHolderReader {
        private static Holder read(FriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var state = Block.stateById(buffer.readVarInt());
            var blockEntity = BlockEntity.loadStatic(pos, state, buffer.readNbt());
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (blockEntity == null || level == null) return null;
            blockEntity.setLevel(level);
            return new Holder(blockEntity, ECOProviderActions.member(blockEntity, "getMetaMachine"));
        }
    }

    static void initialize() { UIFactory.register(FACTORY); }
    static boolean open(BlockEntity blockEntity, Object machine, ServerPlayer player) {
        return FACTORY.openUI(new Holder(blockEntity, machine), player);
    }
}
