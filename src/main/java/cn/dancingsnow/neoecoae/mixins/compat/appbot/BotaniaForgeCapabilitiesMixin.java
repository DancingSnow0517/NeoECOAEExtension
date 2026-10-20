package cn.dancingsnow.neoecoae.mixins.compat.appbot;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AppBot's nested AE2 capability event may run before Botania's capability event. */
@Pseudo
@Mixin(targets = "vazkii.botania.api.BotaniaForgeCapabilities", remap = false)
public abstract class BotaniaForgeCapabilitiesMixin {
    @Inject(
        method = "getBlockApiLookupById(Lvazkii/botania/api/capability/BlockApiNoContext;)Lnet/neoforged/neoforge/capabilities/BlockCapability;",
        at = @At("RETURN"), cancellable = true
    )
    private static void neoecoae$resolveEarlyLookup(@Coerce Object lookup,
            CallbackInfoReturnable<BlockCapability<?, ?>> callback) {
        neoecoae$resolveLookup(lookup, false, callback);
    }

    @Inject(
        method = "getBlockApiLookupById(Lvazkii/botania/api/capability/BlockApiWithContext;)Lnet/neoforged/neoforge/capabilities/BlockCapability;",
        at = @At("RETURN"), cancellable = true
    )
    private static void neoecoae$resolveEarlyContextLookup(@Coerce Object lookup,
            CallbackInfoReturnable<BlockCapability<?, ?>> callback) {
        neoecoae$resolveLookup(lookup, true, callback);
    }

    @Unique
    private static void neoecoae$resolveLookup(Object lookup, boolean hasContext,
            CallbackInfoReturnable<BlockCapability<?, ?>> callback) {
        if (callback.getReturnValue() != null) return;
        try {
            ResourceLocation id = (ResourceLocation) lookup.getClass().getMethod("getId").invoke(lookup);
            Class<?> api = (Class<?>) lookup.getClass().getMethod("getApiClass").invoke(lookup);
            Class<?> context = hasContext
                ? (Class<?>) lookup.getClass().getMethod("getContextClass").invoke(lookup)
                : Void.class;
            // NeoForge interns capabilities by ID. Botania's later registration obtains the
            // same capability; do not prepopulate its map, which would reject a duplicate.
            callback.setReturnValue(BlockCapability.create(id, api, context));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot resolve Botania's early block capability", exception);
        }
    }
}
