package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.parts.IPartHost;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native provider menus with a detached client host, including targets outside the client's chunk view. */
public final class ECORemoteProviderLocator implements MenuLocator {
    private final BlockPos position;
    private final Direction side;
    private final int state;
    private final CompoundTag data;
    private BlockEntity clientHost;

    public ECORemoteProviderLocator(BlockEntity host, Direction side) {
        this(host.getBlockPos(), side, Block.getId(host.getBlockState()), host.saveWithFullMetadata());
    }

    private ECORemoteProviderLocator(BlockPos pos, Direction side, int state, CompoundTag data) {
        this.position = pos.immutable(); this.side = side; this.state = state; this.data = data;
    }

    public static void initialize() {
        MenuLocators.register(ECORemoteProviderLocator.class, ECORemoteProviderLocator::write, ECORemoteProviderLocator::read);
        ECORemoteMachineUI.initialize();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeByte(side == null ? -1 : side.ordinal());
        buffer.writeVarInt(state);
        buffer.writeNbt(data);
    }

    private static ECORemoteProviderLocator read(FriendlyByteBuf buffer) {
        BlockPos position = buffer.readBlockPos();
        int side = buffer.readByte();
        return new ECORemoteProviderLocator(position, side < 0 ? null : Direction.from3DDataValue(side),
            buffer.readVarInt(), buffer.readNbt());
    }

    @Override public <T> T locate(Player player, Class<T> expected) {
        BlockEntity host;
        if (player.level().isClientSide) {
            if (clientHost == null) {
                clientHost = BlockEntity.loadStatic(position, Block.stateById(state), data);
                if (clientHost != null) clientHost.setLevel(player.level());
            }
            host = clientHost;
        } else {
            if (!player.level().hasChunkAt(position)) return null;
            host = player.level().getBlockEntity(position);
        }
        Object target = side != null && host instanceof IPartHost partHost ? partHost.getPart(side) : host;
        return expected.isInstance(target) ? expected.cast(target) : null;
    }
}
