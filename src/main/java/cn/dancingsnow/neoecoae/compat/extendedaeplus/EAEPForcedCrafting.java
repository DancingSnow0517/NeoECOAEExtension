package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraftforge.fml.ModList;

/** Optional EAEP protocol bridge. No EAEP classes are linked when the mod is absent. */
public final class EAEPForcedCrafting {
    private EAEPForcedCrafting() {}

    private static final class Holder {
        static final Access ACCESS = load(EAEPForcedCrafting.class.getClassLoader(), true);
    }

    private static boolean installed() {
        return ModList.get() != null && ModList.get().isLoaded("extendedae_plus");
    }

    public static ICraftingPlan force(ICraftingPlan plan) {
        var access = installed() ? Holder.ACCESS : null;
        return access != null ? access.force(plan) : new ECOForcedCraftingPlan(plan);
    }

    public static boolean isForced(ICraftingPlan plan) {
        if (plan instanceof ECOForcedCraftingPlan) return true;
        var access = installed() ? Holder.ACCESS : null;
        return access != null && access.marker().isInstance(plan);
    }

    /** EAEP keeps these outside emittedItems; ECO imports them into its persisted waiting inventory. */
    public static KeyCounter manualMissing(ICraftingPlan plan) {
        var access = installed() ? Holder.ACCESS : null;
        return access != null ? access.missing(plan) : new KeyCounter();
    }

    static Access load(ClassLoader loader, boolean installed) {
        if (!installed) return null;
        try {
            var marker = Class.forName("com.extendedae_plus.api.crafting.IForcedCraftingPlan", true, loader);
            var wrapper = Class.forName("com.extendedae_plus.crafting.ForcedCraftingPlan", true, loader);
            return new Access(wrapper.getConstructor(ICraftingPlan.class), marker,
                marker.getMethod("eap$getManualMissingItems"));
        } catch (ClassNotFoundException | NoSuchMethodException missingApi) {
            // Older Plus releases are installed without this optional protocol. Keep their normal
            // submissions working and use ECO's existing waiting-inventory fallback for forced plans.
            return null;
        }
    }

    record Access(Constructor<?> constructor, Class<?> marker, Method getter) {
        ICraftingPlan force(ICraftingPlan plan) {
            if (marker.isInstance(plan)) return plan;
            try {
                return (ICraftingPlan) constructor.newInstance(plan);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("ExtendedAEPlus could not create a forced crafting plan", failure);
            }
        }

        KeyCounter missing(ICraftingPlan plan) {
            if (!marker.isInstance(plan)) return new KeyCounter();
            try {
                return (KeyCounter) getter.invoke(plan);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("ExtendedAEPlus could not supply the missing crafting materials", failure);
            }
        }
    }
}
