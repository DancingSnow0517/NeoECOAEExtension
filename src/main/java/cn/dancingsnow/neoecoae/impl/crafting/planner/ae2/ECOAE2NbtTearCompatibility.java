package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Optional bridge for AE2 Utility's provider-scoped NBT Tear Card input matching. */
final class ECOAE2NbtTearCompatibility {
    private static final Optional<Bindings> BINDINGS = findBindings();

    private ECOAE2NbtTearCompatibility() {
    }

    static Scope open(IPatternDetails pattern, ICraftingService craftingService) {
        if (BINDINGS.isEmpty() || !hasActiveTearCard(pattern, craftingService)) {
            return Scope.INACTIVE;
        }
        Bindings bindings = BINDINGS.get();
        if (!bindings.craftingServiceType().isInstance(craftingService)) {
            return Scope.INACTIVE;
        }
        try {
            bindings.beginCalculation().invoke(null, craftingService);
            bindings.setPattern().invoke(null, pattern);
            return new Scope(bindings);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Scope.INACTIVE;
        }
    }

    static void addInventoryVariants(
        IPatternDetails.IInput input,
        Map<AEKey, Long> choices,
        Map<AEKey, Long> inventory,
        Level level,
        Scope scope
    ) {
        if (!scope.active() || choices.isEmpty()) {
            return;
        }
        long amount = choices.values().stream().filter(value -> value > 0L).findFirst().orElse(0L);
        if (amount <= 0L) {
            return;
        }
        for (AEKey candidate : inventory.keySet()) {
            if (choices.containsKey(candidate)) {
                continue;
            }
            try {
                if (input.isValid(candidate, level)) {
                    choices.put(candidate, amount);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A third-party matcher rejecting one inventory key must not reject the whole snapshot.
            }
        }
    }

    private static boolean hasActiveTearCard(IPatternDetails pattern, ICraftingService craftingService) {
        if (BINDINGS.isEmpty()) {
            return false;
        }
        Bindings bindings = BINDINGS.get();
        if (!bindings.craftingServiceType().isInstance(craftingService)) {
            return false;
        }
        try {
            Object providers = bindings.getProviders().invoke(craftingService, pattern);
            if (!(providers instanceof Iterable<?> iterable)) {
                return false;
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
            return false;
        }
        return false;
    }

    private static Optional<Bindings> findBindings() {
        try {
            ClassLoader loader = ECOAE2NbtTearCompatibility.class.getClassLoader();
            Class<?> craftingService = Class.forName("appeng.me.service.CraftingService", false, loader);
            Class<?> logicAccess = Class.forName(
                "com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess", false, loader);
            Class<?> tearCard = Class.forName("com.lhy.ae2utility.item.NbtTearCardItem", false, loader);
            Class<?> simulationEnvironment = Class.forName(
                "com.lhy.ae2utility.card.NbtTearSimulationEnv", false, loader);
            Class<?> patternContext = Class.forName(
                "com.lhy.ae2utility.card.NbtTearPatternContext", false, loader);
            return Optional.of(new Bindings(
                craftingService,
                logicAccess,
                tearCard,
                craftingService.getMethod("getProviders", IPatternDetails.class),
                logicAccess.getMethod("ae2utility$getEffectiveTearCardStack"),
                simulationEnvironment.getMethod("beginCalculation", craftingService),
                simulationEnvironment.getMethod("clear"),
                patternContext.getMethod("set", IPatternDetails.class),
                patternContext.getMethod("clear")
            ));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    static final class Scope implements AutoCloseable {
        private static final Scope INACTIVE = new Scope(null);

        private final Bindings bindings;

        private Scope(Bindings bindings) {
            this.bindings = bindings;
        }

        boolean active() {
            return bindings != null;
        }

        @Override
        public void close() {
            if (bindings == null) {
                return;
            }
            try {
                bindings.clearPattern().invoke(null);
                bindings.clearCalculation().invoke(null);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // The context is only an optional bridge and must not affect planning cleanup.
            }
        }
    }

    private record Bindings(
        Class<?> craftingServiceType,
        Class<?> logicAccessType,
        Class<?> tearCardType,
        Method getProviders,
        Method getEffectiveTearCard,
        Method beginCalculation,
        Method clearCalculation,
        Method setPattern,
        Method clearPattern
    ) {
    }
}
