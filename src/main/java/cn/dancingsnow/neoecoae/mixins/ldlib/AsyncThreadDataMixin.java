package cn.dancingsnow.neoecoae.mixins.ldlib;

import com.lowdragmc.lowdraglib2.async.AsyncThreadData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Prevents repeated block-entity lifecycle callbacks from registering the same async logic more than once.
 */
@Mixin(value = AsyncThreadData.class, remap = false)
public abstract class AsyncThreadDataMixin {
    @Redirect(
        method = "addAsyncLogic",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CopyOnWriteArrayList;add(Ljava/lang/Object;)Z"
        )
    )
    private boolean neoecoae$addAsyncLogicIfAbsent(
        CopyOnWriteArrayList<Object> logics,
        Object e
    ) {
        return logics.addIfAbsent(e);
    }
}
