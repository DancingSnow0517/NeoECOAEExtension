package cn.dancingsnow.neoecoae.api.multiblock;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/** Shared acceptance rules for placement, preview and formation. Predicates must be pure and never load chunks. */
public final class ECOMultiblockRequirements {
    private static final Set<String> DERIVED_PROPERTIES = Set.of("formed", "working", "mirrored", "invisible",
        "classic_visible", "has_cell", "lit", "normal_network_switch", "high_energy_network_switch");
    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();
    private ECOMultiblockRequirements() {}
    public record Context(@Nullable Level level, BlockPos position, BlockState expected, BlockState actual) {}
    @FunctionalInterface public interface Provider { @Nullable Boolean accepts(Context context); }
    public static void register(ResourceLocation id, int priority, Provider provider) { REGISTRY.register(id, priority, provider); }
    public static boolean unregister(ResourceLocation id) { return REGISTRY.unregister(id); }
    public static long revision() { return REGISTRY.revision(); }

    public static void registerAlternatives(ResourceLocation id, int priority, Block expected, Set<Block> alternatives) {
        Set<Block> copy = Set.copyOf(alternatives);
        register(id, priority, context -> context.expected().is(expected) && copy.contains(context.actual().getBlock())
            ? matchingProperties(context.expected(), context.actual()) : null);
    }

    public static boolean accepts(Context context, boolean fallback) {
        for (var entry : REGISTRY.entries()) {
            Boolean accepted = entry.extension().accepts(context);
            if (accepted != null) return accepted;
        }
        return fallback;
    }

    public static boolean matchingProperties(BlockState expected, BlockState actual) {
        for (Property<?> property : expected.getProperties()) {
            if (DERIVED_PROPERTIES.contains(property.getName())) continue;
            if (actual.hasProperty(property) && !Objects.equals(expected.getValue(property), actual.getValue(property))) return false;
        }
        return true;
    }
}
