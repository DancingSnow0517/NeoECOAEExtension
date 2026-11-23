package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class BlockUIMenuType {
    public interface BlockUI {
        ModularUI createUI(BlockUIHolder holder);
        default boolean stillValid(BlockUIHolder holder){return holder.player.level().hasChunkAt(holder.pos)
            &&(holder.player.distanceToSqr(holder.pos.getX()+0.5,holder.pos.getY()+0.5,holder.pos.getZ()+0.5)<=64
                // Opened from the crafting status page; the exemption ends with the menu it produced.
                ||cn.dancingsnow.neoecoae.crafting.display.processing.ECORemoteMachineReach.allows(holder.player,holder.pos));}
    }
    public interface MenuData {
        CompoundTag createMenuData(Player player);

        default BooleanSupplier captureMenuValidity() { return () -> true; }
    }

    public static final class BlockUIHolder implements IUIHolder {
        public final Player player;
        public final BlockPos pos;
        public final BlockState blockState;
        private final Block original;
        private final BlockEntity originalBlockEntity;
        private final BooleanSupplier menuValid;
        private CompoundTag menuData = new CompoundTag();

        public BlockUIHolder(Player player, BlockPos pos) {
            this(player, pos, player.level().getBlockState(pos));
        }

        /** Both sides must construct the retained tree from the opening server's state. */
        public BlockUIHolder(Player player, BlockPos pos, BlockState blockState) {
            this.player = player;
            this.pos = pos.immutable();
            this.blockState = blockState;
            original = blockState.getBlock();
            originalBlockEntity = player.level().getBlockEntity(this.pos);
            if (!player.level().isClientSide && originalBlockEntity instanceof MenuData data) {
                menuValid = data.captureMenuValidity();
                menuData = data.createMenuData(player);
            } else {
                menuValid = () -> true;
            }
        }

        public static BlockUIHolder readFromSyncData(Player player, FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            var holder = new BlockUIHolder(player, pos, Block.stateById(buffer.readVarInt()));
            holder.menuData = java.util.Objects.requireNonNull(buffer.readNbt());
            return holder;
        }

        public void writeToSyncData(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(Block.getId(blockState));
            buffer.writeNbt(menuData);
        }

        public CompoundTag menuData() { return menuData; }

        public BlockUI target() {
            if (original instanceof BlockUI ui) return ui;
            return originalBlockEntity instanceof BlockUI ui ? ui : null;
        }
        @Override public com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(Player player){var target=target();if(target==null)return null;var ui=target.createUI(this);return ui==null?null:ui.nativeUI(this);}
        @Override public boolean isInvalid() {
            var level = player.level();
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).getBlock() != original
                    || level.getBlockEntity(pos) != originalBlockEntity
                    || originalBlockEntity != null && originalBlockEntity.isRemoved()) return true;
            if (!menuValid.getAsBoolean()) return true;
            var target = target();
            return target == null || !target.stillValid(this);
        }
        @Override public boolean isRemote(){return player.level().isClientSide;}
        @Override public void markAsDirty() {
            if (originalBlockEntity != null && !originalBlockEntity.isRemoved()) originalBlockEntity.setChanged();
        }
    }
    private static final UIFactory<BlockUIHolder> FACTORY=new UIFactory<>(ResourceLocation.fromNamespaceAndPath("neoecoae","block_ui")){
        @Override protected com.lowdragmc.lowdraglib.gui.modular.ModularUI createUITemplate(BlockUIHolder holder,Player player){return holder.isInvalid()?null:holder.createUI(player);}
        @OnlyIn(Dist.CLIENT)
        @Override protected BlockUIHolder readHolderFromSyncData(FriendlyByteBuf buffer){return ClientHolderReader.read(buffer);}
        @Override protected void writeHolderToSyncData(FriendlyByteBuf buffer,BlockUIHolder holder){holder.writeToSyncData(buffer);}
    };
    // Keep client-only types out of the factory's verification during dedicated-server setup.
    @OnlyIn(Dist.CLIENT)
    private static final class ClientHolderReader {
        private static BlockUIHolder read(FriendlyByteBuf buffer) {
            return BlockUIHolder.readFromSyncData(net.minecraft.client.Minecraft.getInstance().player, buffer);
        }
    }
    private BlockUIMenuType(){}
    /** Called during common setup on both the client and the dedicated server. */
    public static void register(){UIFactory.register(FACTORY);}
    public static boolean openUI(ServerPlayer player,BlockPos pos){return FACTORY.openUI(new BlockUIHolder(player,pos),player);}
}
