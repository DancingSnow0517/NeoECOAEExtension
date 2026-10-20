package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IRPCBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.RejectedExecutionException;

/** The project's managed block-entity contract backed by LDLib 1, including its lifecycle mixin. */
public interface NEManagedBlockEntity extends IManaged, IAutoPersistBlockEntity,
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
