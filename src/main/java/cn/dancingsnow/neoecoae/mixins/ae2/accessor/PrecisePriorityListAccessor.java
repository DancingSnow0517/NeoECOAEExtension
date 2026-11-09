package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.stacks.KeyCounter;
import appeng.util.prioritylist.PrecisePriorityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access avoids getItems(), which allocates a complete HashSet on every observation. */
@Mixin(value = PrecisePriorityList.class, remap = false)
public interface PrecisePriorityListAccessor {
    @Accessor("list") KeyCounter neoecoae$partitionKeys();
}
