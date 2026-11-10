package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.DataSynchronization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AEBaseMenu.class, remap = false)
public interface AEBaseMenuAccessor {
    @Accessor("dataSync")
    DataSynchronization neoecoae$getDataSync();
}
