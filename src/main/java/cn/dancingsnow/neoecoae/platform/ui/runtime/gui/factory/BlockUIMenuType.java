package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory;

import cn.dancingsnow.neoecoae.api.CraftingPatternMenuHost;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import java.util.Objects;
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
import org.jetbrains.annotations.Nullable;

public final class BlockUIMenuType {
    public interface BlockUI {
        ModularUI createUI(BlockUIHolder holder);
        default boolean stillValid(BlockUIHolder holder) { return inReach(holder); }
    }
    private static boolean inReach(BlockUIHolder holder) {
        return holder.player.level().hasChunkAt(holder.pos)
            && (holder.player.distanceToSqr(holder.pos.getCenter()) <= 64
                // Opened from the crafting status page; the exemption ends with the menu it produced.
                || cn.dancingsnow.neoecoae.crafting.display.processing.ECORemoteMachineReach.allows(holder.player, holder.pos));
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
        private static final String HOSTED_PATTERN = "neoecoae:hosted_pattern";
        private final boolean hostedPattern;
        private final @Nullable ECOMachineInterfaceBlockEntity<?> patternInterface;
        private final BooleanSupplier menuValid;
        private CompoundTag menuData = new CompoundTag();

        public BlockUIHolder(Player player, BlockPos pos) {
            this(player, pos, player.level().getBlockState(pos));
        }

        /** Both sides must construct the retained tree from the opening server's state. */
        public BlockUIHolder(Player player, BlockPos pos, BlockState blockState) {
            this(player, pos, blockState, false);
        }

        private BlockUIHolder(Player player, BlockPos pos, BlockState blockState, boolean hostedPattern) {
            this.player = player;
            this.hostedPattern = hostedPattern;
            this.pos = pos.immutable();
            this.blockState = blockState;
            original = blockState.getBlock();
            originalBlockEntity = player.level().getBlockEntity(this.pos);
            var owner = originalBlockEntity instanceof CraftingPatternMenuHost host ? host : null;
            patternInterface = hostedPattern ? (owner == null ? null : owner.getCraftingPatternMenuInterface())
                : originalBlockEntity instanceof ECOMachineInterfaceBlockEntity<?> direct ? direct : null;
            if (hostedPattern && owner != null && patternInterface != null && !player.level().isClientSide) {
                Object layoutKey = owner.getCraftingPatternMenuLayoutKey();
                var delegateValid = patternInterface.captureMenuValidity();
                menuValid = () -> delegateValid.getAsBoolean() && Objects.equals(layoutKey, owner.getCraftingPatternMenuLayoutKey());
                menuData = patternInterface.createMenuData(player).copy();
                menuData.putBoolean(HOSTED_PATTERN, true);
            } else if (!hostedPattern && !player.level().isClientSide && originalBlockEntity instanceof MenuData data) {
                menuValid = data.captureMenuValidity();
                menuData = data.createMenuData(player);
            } else {
                menuValid = () -> true;
            }
        }

        public static BlockUIHolder readFromSyncData(Player player, FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            var state = Block.stateById(buffer.readVarInt());
            var data = Objects.requireNonNull(buffer.readNbt());
            var holder = new BlockUIHolder(player, pos, state, data.getBoolean(HOSTED_PATTERN));
            holder.menuData = data;
            return holder;
        }

        public void writeToSyncData(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(Block.getId(blockState));
            buffer.writeNbt(menuData);
        }

        public CompoundTag menuData() { return menuData; }

        public @Nullable ECOMachineInterfaceBlockEntity<?> patternInterface() { return patternInterface; }
        public boolean isHostedPattern() { return hostedPattern; }

        public BlockUI target() {
            if (original instanceof BlockUI ui) return ui;
            return originalBlockEntity instanceof BlockUI ui ? ui : null;
        }
        @Override
        public com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(Player player) {
            var target = target();
            var ui = hostedPattern ? (patternInterface == null ? null : patternInterface.createUI(this))
                : target == null ? null : target.createUI(this);
            return ui == null ? null : ui.nativeUI(this);
        }
        @Override public boolean isInvalid() {
            var level = player.level();
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).getBlock() != original
                    || level.getBlockEntity(pos) != originalBlockEntity
                    || originalBlockEntity != null && originalBlockEntity.isRemoved()) return true;
            if (!menuValid.getAsBoolean()) return true;
            if (hostedPattern) return !(originalBlockEntity instanceof CraftingPatternMenuHost owner)
                || patternInterface == null || patternInterface != owner.getCraftingPatternMenuInterface()
                || patternInterface.isRemoved() || patternInterface.getLevel() != level || !pos.equals(patternInterface.getBlockPos())
                || !patternInterface.supportsCraftingInterfaceUi() || !inReach(this);
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
    /** Opens the retained crafting interface without taking over the addon's normal host panel. */
    public static boolean openCraftingPatternInterface(ServerPlayer player, ECOMachineInterfaceBlockEntity<?> delegate) {
        var pos = delegate.getBlockPos();
        if (delegate.getLevel() != player.level() || !player.level().hasChunkAt(pos) || !delegate.supportsCraftingInterfaceUi()) return false;
        var owner = player.level().getBlockEntity(pos);
        if (owner == delegate) return openUI(player, pos);
        var holder = new BlockUIHolder(player, pos, player.level().getBlockState(pos), true);
        return holder.patternInterface() == delegate && !holder.isInvalid() && FACTORY.openUI(holder, player);
    }
    public static boolean openUI(ServerPlayer player,BlockPos pos){return FACTORY.openUI(new BlockUIHolder(player,pos),player);}
}
