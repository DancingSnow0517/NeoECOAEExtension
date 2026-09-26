package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker")
public interface PatternAccessContainerTrackerAccessor {
    @Accessor("server")
    InternalInventory neoecoae$getServerInventory();

    @Accessor("container")
    PatternContainer neoecoae$getContainer();
}
