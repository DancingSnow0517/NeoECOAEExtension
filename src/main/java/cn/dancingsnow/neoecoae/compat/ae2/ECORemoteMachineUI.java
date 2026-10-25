package cn.dancingsnow.neoecoae.compat.ae2;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

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
            var pos = buffer.readBlockPos();
            var state = Block.stateById(buffer.readVarInt());
            var blockEntity = BlockEntity.loadStatic(pos, state, buffer.readNbt());
            if (blockEntity == null || Minecraft.getInstance().level == null) return null;
            blockEntity.setLevel(Minecraft.getInstance().level);
            return new Holder(blockEntity, ECOProviderActions.member(blockEntity, "getMetaMachine"));
        }

        @Override protected void writeHolderToSyncData(FriendlyByteBuf buffer, Holder holder) {
            var be = holder.blockEntity();
            buffer.writeBlockPos(be.getBlockPos());
            buffer.writeVarInt(Block.getId(be.getBlockState()));
            buffer.writeNbt(be.saveWithFullMetadata());
        }
    };

    static void initialize() { UIFactory.register(FACTORY); }
    static boolean open(BlockEntity blockEntity, Object machine, ServerPlayer player) {
        return FACTORY.openUI(new Holder(blockEntity, machine), player);
    }
}
