package cn.dancingsnow.neoecoae.compat.mekenergistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Method;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Optional Mek-Energistics smart queue; ECO owns scaling, extraction and accounting. */
public final class ECOMekEnergisticsBatchCapability {
    private static final String OWNER = "com.beipuo.mekenergistics.blockentity.api.MeAeSupportOwner";
    private static final ClassValue<Optional<Api>> APIS = new ClassValue<>() {
        @Override
        protected Optional<Api> computeValue(Class<?> type) {
            Class<?> owner = findOwner(type);
            if (owner == null) return Optional.empty();
            try {
                Method support = owner.getMethod("getPatternAeSupport");
                Class<?> supportType = support.getReturnType();
                return Optional.of(new Api(owner.getMethod("isSmartPatternMultiplicationEnabled"), support,
                        supportType.getMethod("getMainNode"),
                        supportType.getMethod("hasRegisteredPattern", IPatternDetails.class),
                        supportType.getMethod("enqueueSmartPattern", IPatternDetails.class, KeyCounter[].class)));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return Optional.empty();
            }
        }
    };

    private ECOMekEnergisticsBatchCapability() {}

    @Nullable
    public static Session open(ICraftingProvider provider) {
        Api api = APIS.get(provider.getClass()).orElse(null);
        return api == null ? null : new Session(provider, api);
    }

    private static Class<?> findOwner(Class<?> type) {
        if (type.getName().equals(OWNER)) return type;
        for (Class<?> parent : type.getInterfaces()) {
            Class<?> owner = findOwner(parent);
            if (owner != null) return owner;
        }
        return type.getSuperclass() == null ? null : findOwner(type.getSuperclass());
    }

    public static final class Session {
        private final ICraftingProvider provider;
        private final Api api;

        private Session(ICraftingProvider provider, Api api) {
            this.provider = provider;
            this.api = api;
        }

        public long inspect(IPatternDetails pattern, KeyCounter[] inputs, long requested) {
            if (requested <= 0) return 0;
            try {
                Object support = api.support.invoke(provider);
                if (!ready(support, pattern)) return 0;
                if (!(boolean) api.enabled.invoke(provider)) return Math.min(1, requested);
                return validPrototype(pattern, inputs) ? requested : 0;
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return 0;
            }
        }

        /** Returns unaccepted copies, matching the native processing dispatch contract. */
        public long submit(IPatternDetails pattern, KeyCounter[] inputs, long copies) {
            if (copies <= 0) return Math.max(0, copies);
            try {
                Object support = api.support.invoke(provider);
                if (!ready(support, pattern)) return copies;
                if (!(boolean) api.enabled.invoke(provider)) {
                    return copies == 1 && provider.pushPattern(pattern, inputs) ? 0 : copies;
                }
                if (!validPrototype(pattern, inputs)) return copies;
                KeyCounter[] total = new KeyCounter[inputs.length];
                try {
                    for (int i = 0; i < inputs.length; i++) {
                        var entry = inputs[i].getFirstEntry();
                        total[i] = new KeyCounter();
                        total[i].add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), copies));
                    }
                } catch (ArithmeticException overflow) {
                    return copies;
                }
                // Enqueue validates the complete total atomically, persists it and wakes the machine.
                // Never call pushPattern here: its fallback can disable smart multiplication.
                return (boolean) api.enqueue.invoke(support, pattern, total) ? 0 : copies;
            } catch (ReflectiveOperationException failure) {
                // Enqueue may have succeeded before saveChanges/alertAeTicker failed. Do not replay.
                throw new IllegalStateException("Cannot determine Mek-Energistics input ownership", failure);
            }
        }

        private boolean ready(Object support, IPatternDetails pattern) throws ReflectiveOperationException {
            return support != null && !provider.isBusy()
                    && api.node.invoke(support) instanceof IManagedGridNode node && node.isActive()
                    && (boolean) api.registered.invoke(support, pattern);
        }
    }

    private static boolean validPrototype(IPatternDetails pattern, KeyCounter[] inputs) {
        if (pattern == null || inputs == null || inputs.length == 0
                || pattern.getInputs().length != inputs.length) return false;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null || inputs[i].size() != 1) return false;
            var actual = inputs[i].getFirstEntry();
            var input = pattern.getInputs()[i];
            if (actual == null || actual.getLongValue() <= 0 || input == null || input.getMultiplier() <= 0
                    || input.getPossibleInputs() == null) return false;
            boolean matched = false;
            for (var possible : input.getPossibleInputs()) {
                if (possible == null || !possible.what().equals(actual.getKey())) continue;
                try {
                    matched = possible.amount() > 0
                            && Math.multiplyExact(possible.amount(), input.getMultiplier()) == actual.getLongValue();
                } catch (ArithmeticException overflow) {
                    return false;
                }
                break;
            }
            if (!matched) return false;
        }
        return true;
    }

    private record Api(Method enabled, Method support, Method node, Method registered, Method enqueue) {}
}
