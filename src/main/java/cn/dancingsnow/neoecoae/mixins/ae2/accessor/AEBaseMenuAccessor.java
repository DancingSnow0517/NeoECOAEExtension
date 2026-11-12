package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.menu.AEBaseMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AEBaseMenu.class)
public interface AEBaseMenuAccessor {
    @Accessor("dataSync")
    appeng.menu.guisync.DataSynchronization neoecoae$getDataSync();

    @Mutable
    @Accessor("blockEntity")
    void neoecoae$setBlockEntity(BlockEntity blockEntity);
}
