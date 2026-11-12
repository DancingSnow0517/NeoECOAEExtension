package cn.dancingsnow.neoecoae.mixins.client.accessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Temporarily selects a bounded report target while extracting the export's native table. */
@Mixin(Minecraft.class)
public interface MinecraftRenderTargetAccessor {
    @Mutable @Accessor("mainRenderTarget")
    void neoecoae$setMainRenderTarget(RenderTarget target);
}
