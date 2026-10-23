package cn.dancingsnow.neoecoae.api.multiblock;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Defines building/preview geometry; pair custom formation rules with NEBlockEntity.registerCalculatorFactory. */
public final class ECOMultiblockDefinitions {
    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();
    private ECOMultiblockDefinitions() {}

    @FunctionalInterface
    public interface Provider {
        @Nullable MultiBlockDefinition definition(Context context);
    }
    public record Context(BlockEntity host, ECOMachineKind kind, IECOTier tier) {}

    public static void register(ResourceLocation id, int priority, Provider provider) {
        REGISTRY.register(id, priority, provider);
    }
    public static boolean unregister(ResourceLocation id) { return REGISTRY.unregister(id); }
    public static long revision() { return REGISTRY.revision(); }

    @Nullable
    public static MultiBlockDefinition resolve(BlockEntity host, ECOMachineKind kind, IECOTier tier,
                                               Supplier<MultiBlockDefinition> fallback) {
        var context = new Context(host, kind, tier);
        for (var entry : REGISTRY.entries()) {
            var definition = entry.extension().definition(context);
            if (definition != null) return definition;
        }
        var definition = tier.getBuildDefinition(kind);
        return definition != null ? definition : fallback.get();
    }
}
