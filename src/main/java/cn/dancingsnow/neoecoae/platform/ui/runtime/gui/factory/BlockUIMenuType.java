package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class BlockUIMenuType {
    public interface BlockUI {
        ModularUI createUI(BlockUIHolder holder);
        default boolean stillValid(BlockUIHolder holder){return holder.player.level().hasChunkAt(holder.pos)
            &&holder.player.distanceToSqr(holder.pos.getX()+0.5,holder.pos.getY()+0.5,holder.pos.getZ()+0.5)<=64;}
    }
    public static final class BlockUIHolder implements IUIHolder {
        public final Player player;
        public final BlockPos pos;
        public final net.minecraft.world.level.block.state.BlockState blockState;
        private final net.minecraft.world.level.block.Block original;
        public BlockUIHolder(Player player,BlockPos pos){this.player=player;this.pos=pos.immutable();blockState=player.level().getBlockState(pos);original=blockState.getBlock();}
        public BlockUI target(){if(original instanceof BlockUI ui)return ui;var be=player.level().getBlockEntity(pos);return be instanceof BlockUI ui?ui:null;}
        @Override public com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(Player player){var target=target();if(target==null)return null;var ui=target.createUI(this);return ui==null?null:ui.nativeUI(this);}
        @Override public boolean isInvalid(){return player.level().getBlockState(pos).getBlock()!=original||target()==null||!target().stillValid(this);}
        @Override public boolean isRemote(){return player.level().isClientSide;}
        @Override public void markAsDirty(){var be=player.level().getBlockEntity(pos);if(be!=null)be.setChanged();}
    }
    private static final UIFactory<BlockUIHolder> FACTORY=new UIFactory<>(ResourceLocation.fromNamespaceAndPath("neoecoae","block_ui")){
        @Override protected com.lowdragmc.lowdraglib.gui.modular.ModularUI createUITemplate(BlockUIHolder holder,Player player){return holder.isInvalid()?null:holder.createUI(player);}
        @Override protected BlockUIHolder readHolderFromSyncData(FriendlyByteBuf buffer){return new BlockUIHolder(net.minecraft.client.Minecraft.getInstance().player,buffer.readBlockPos());}
        @Override protected void writeHolderToSyncData(FriendlyByteBuf buffer,BlockUIHolder holder){buffer.writeBlockPos(holder.pos);}
    };
    static{UIFactory.register(FACTORY);}
    private BlockUIMenuType(){}
    public static boolean openUI(ServerPlayer player,BlockPos pos){return FACTORY.openUI(new BlockUIHolder(player,pos),player);}
}
