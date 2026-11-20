package cn.dancingsnow.neoecoae.mixins.ldlib;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.async.AsyncThreadData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Prevents repeated block-entity lifecycle callbacks from registering the same async logic more than once.
 * Only patches LDLib2's legacy list implementation; 2.2.45+ manages registrations with its own counted map.
 */
@Mixin(value = AsyncThreadData.class, remap = false)
public abstract class AsyncThreadDataMixin {
    /**
    * 我不希望，也绝对不想要看到 @Redirect 后面注意！！
    */
    @WrapOperation(
        method = "addAsyncLogic",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CopyOnWriteArrayList;add(Ljava/lang/Object;)Z"
        )
    )
    private boolean neoecoae$addAsyncLogicIfAbsent(
        CopyOnWriteArrayList<Object> logics,
        Object logic,
        Operation<Boolean> original
    ) {
        return logics.addIfAbsent(logic);
    }
}
