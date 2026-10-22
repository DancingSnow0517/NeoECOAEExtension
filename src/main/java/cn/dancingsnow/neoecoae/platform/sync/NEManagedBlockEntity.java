package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IRPCBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.RejectedExecutionException;

/** The project's managed block-entity contract backed by LDLib 1, including its lifecycle mixin. */
public interface NEManagedBlockEntity extends IEnhancedManaged, IAutoPersistBlockEntity,
        IAsyncAutoSyncBlockEntity, IRPCBlockEntity {

    @Override
    default ManagedFieldHolder getFieldHolder() {
        return ManagedBlockEntityFields.get(getClass());
    }

    @Override
    default IManagedStorage getRootStorage() {
        return getSyncStorage();
    }

    @Override
    default void onChanged() {
        notifyPersistence();
    }

    @Override
    default void scheduleRenderUpdate() {
        var self = getSelf();
        var level = self.getLevel();
        if (level != null && level.isClientSide() && !self.isRemoved()
                && level.getBlockEntity(self.getBlockPos()) == self) {
            self.requestModelDataUpdate();
            var state = self.getBlockState();
            level.sendBlockUpdated(self.getBlockPos(), state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    default void notifyPersistence() {
        if (getSelf().getLevel() instanceof ServerLevel level && !level.getServer().isStopped()) {
            try {
                level.getServer().executeIfPossible(() -> {
                    if (!getSelf().isRemoved() && getSelf().getLevel() == level) getSelf().setChanged();
                });
            } catch (RejectedExecutionException ignored) {
                // A final async dirty notification may race normal server shutdown.
            }
        }
    }

    default void rpcToServer(String methodName, Object... args) {
        ManagedRpcNetwork.send(this, methodName, args);
    }

    default void rpcToPlayer(ServerPlayer player, String methodName, Object... args) {
        rpcToPlayer(this, player, methodName, args);
    }

    default void rpcToTracking(String methodName, Object... args) {
        rpcToTracking(this, methodName, args);
    }
}
