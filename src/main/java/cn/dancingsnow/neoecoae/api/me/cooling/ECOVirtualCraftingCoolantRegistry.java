package cn.dancingsnow.neoecoae.api.me.cooling;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Additional fluids allowed to fund virtual crafting. Cryotheum Solution is always allowed.
 *
 * <p>Register fluid IDs during common setup; the fluids need not be resolved yet. Registration only grants
 * permission: a matching cooling recipe must still provide tier-9 cooling and enough coolant points.
 * Execution and server-side diagnostics read the registry live, including already buffered coolant.</p>
 */
public final class ECOVirtualCraftingCoolantRegistry {
    public static final Identifier CRYOTHEUM_SOLUTION = Identifier.parse("neoecoae:cryotheum_solution");
    private static final CopyOnWriteArraySet<Identifier> CUSTOM_COOLANTS = new CopyOnWriteArraySet<>();

    private ECOVirtualCraftingCoolantRegistry() {}

    public static void register(Identifier fluidId) {
        Objects.requireNonNull(fluidId, "fluidId");
        if (!CRYOTHEUM_SOLUTION.equals(fluidId)) CUSTOM_COOLANTS.add(fluidId);
    }

    /** Removes a custom registration. The built-in Cryotheum Solution remains allowed. */
    public static void unregister(Identifier fluidId) {
        if (fluidId != null) CUSTOM_COOLANTS.remove(fluidId);
    }

    /** Stable display order, with the built-in coolant first. */
    public static List<Identifier> registeredFluidIds() {
        var ids = new java.util.ArrayList<Identifier>();
        ids.add(CRYOTHEUM_SOLUTION);
        CUSTOM_COOLANTS.stream().sorted().forEach(ids::add);
        return List.copyOf(ids);
    }

    public static boolean isAllowed(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return false;
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        return CRYOTHEUM_SOLUTION.equals(id) || CUSTOM_COOLANTS.contains(id);
    }
}
