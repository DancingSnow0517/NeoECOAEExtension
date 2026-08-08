package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Detects AE2 Utility's provider-scoped NBT Tear Card semantics. */
final class ECOAE2NbtTearCompatibility {
    private static final String TEAR_CARD_CLASS = "com.lhy.ae2utility.item.NbtTearCardItem";
    private static final boolean INTEGRATION_PRESENT = classPresent(TEAR_CARD_CLASS);
    private static final Optional<Bindings> BINDINGS = findBindings();

    private ECOAE2NbtTearCompatibility() {}

    /**
     * NBT Tear validity depends on the provider selected by AE2. ECO snapshots patterns globally,
     * so accepting one provider's context would make the resulting operation invalid for others.
     */
    static boolean isProviderScoped(IPatternDetails pattern, ICraftingService craftingService) {
        if (BINDINGS.isEmpty()) {
            // A present but unknown integration version cannot be proven provider-independent.
            return INTEGRATION_PRESENT;
        }
        Bindings bindings = BINDINGS.get();
        if (!bindings.craftingServiceType().isInstance(craftingService)) {
            return false;
        }
        try {
            Object providers = bindings.getProviders().invoke(craftingService, pattern);
            if (!(providers instanceof Iterable<?> iterable)) {
                return true;
            }
            for (Object provider : iterable) {
                if (!bindings.logicAccessType().isInstance(provider)) {
                    continue;
                }
                Object card = bindings.getEffectiveTearCard().invoke(provider);
                if (card instanceof ItemStack stack
                        && !stack.isEmpty()
                        && bindings.tearCardType().isInstance(stack.getItem())) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return true;
        }
        return false;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, ECOAE2NbtTearCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Optional<Bindings> findBindings() {
        try {
            ClassLoader loader = ECOAE2NbtTearCompatibility.class.getClassLoader();
            Class<?> craftingService = Class.forName("appeng.me.service.CraftingService", false, loader);
            Class<?> logicAccess =
                    Class.forName("com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess", false, loader);
            Class<?> tearCard = Class.forName(TEAR_CARD_CLASS, false, loader);
            return Optional.of(new Bindings(
                    craftingService,
                    logicAccess,
                    tearCard,
                    craftingService.getMethod("getProviders", IPatternDetails.class),
                    logicAccess.getMethod("ae2utility$getEffectiveTearCardStack")));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private record Bindings(
            Class<?> craftingServiceType,
            Class<?> logicAccessType,
            Class<?> tearCardType,
            Method getProviders,
            Method getEffectiveTearCard) {}
}
