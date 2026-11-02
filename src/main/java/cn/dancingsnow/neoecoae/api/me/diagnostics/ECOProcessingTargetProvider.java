package cn.dancingsnow.neoecoae.api.me.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The actual destination chosen by the most recent successful provider push. */
public interface ECOProcessingTargetProvider {
    @Nullable Target neoecoae$getProcessingTarget();

    record Target(ResourceLocation dimension, BlockPos position) {
        public Target { position = position.immutable(); }
    }
}
