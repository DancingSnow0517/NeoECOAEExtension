package cn.dancingsnow.neoecoae.mixins.client.minecraft;

import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Forge 1.20.1 has no sprite source registration event. */
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {
    @Invoker("register")
    static SpriteSourceType neoecoae$register(String name, Codec<? extends SpriteSource> codec) {
        throw new AssertionError();
    }
}
