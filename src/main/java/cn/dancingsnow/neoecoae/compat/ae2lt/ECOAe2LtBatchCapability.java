package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Reflection-only optional AE2LT batch bridge. */
public final class ECOAe2LtBatchCapability {
    private static final String LIGHTNING = "com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider";
    private static final String TIANSHU = "com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer";

    private ECOAe2LtBatchCapability() {}

    @Nullable
    public static Session open(Object provider) {
        if (provider == null || ModList.get() == null || !ModList.get().isLoaded("ae2lt")) return null;
        Class<?> api = findApi(provider.getClass());
        if (api == null) return null;
        try {
            return new ReflectiveSession(provider, api);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // An unsupported API must not claim the provider and suppress ordinary dispatch.
            return null;
        }
    }

    @Nullable
    private static Class<?> findApi(Class<?> type) {
        if (type.getName().equals(LIGHTNING) || type.getName().equals(TIANSHU)) return type;
        for (Class<?> parent : type.getInterfaces()) {
            Class<?> api = findApi(parent);
            if (api != null) return api;
        }
        return type.getSuperclass() == null ? null : findApi(type.getSuperclass());
    }

    public interface Session {
        long inspect(IPatternDetails details, KeyCounter[] inputs, long requested);
        boolean unbounded();
        long submit(IPatternDetails details, KeyCounter[] inputs, long requested);
    }

    private static final class ReflectiveSession implements Session {
        private final Object target;
        private final UUID nonce = UUID.randomUUID();
        private final Method inspect;
        private final Method submit;
        private final Constructor<?> requestConstructor;
        private final RecordComponent[] requestComponents;
        private final Object apiVersion;
        private final Object capabilityId;
        private long maxSafeBatch;

        ReflectiveSession(Object target, Class<?> api) throws ReflectiveOperationException {
            this.target = target;
            Class<?> requestType = Class.forName(api.getName()
                    + (api.getName().equals(LIGHTNING) ? "$BatchRequest" : "$SynthesisRequest"),
                    false, api.getClassLoader());
            requestComponents = requestType.getRecordComponents();
            requestConstructor = requestType.getConstructor(Arrays.stream(requestComponents)
                    .map(RecordComponent::getType).toArray(Class<?>[]::new));
            inspect = api.getMethod("inspect", requestType);
            submit = api.getMethod("submit", requestType);
            apiVersion = api.getField("API_VERSION").get(null);
            capabilityId = api.getField("CAPABILITY_ID").get(null);
            for (var component : requestComponents) {
                switch (component.getName()) {
                    case "apiVersion", "targetCapabilityId", "processingId", "inputsPerCraft",
                            "requestedAmount", "nonce" -> { }
                    default -> throw new NoSuchMethodException("Unknown request field: " + component.getName());
                }
            }
        }

        @Override
        public long inspect(IPatternDetails details, KeyCounter[] inputs, long requested) {
            try {
                Object result = inspect.invoke(target, request(details, inputs, requested));
                maxSafeBatch = number(result, "maxSafeBatch");
                return Math.max(0L, Math.min(requested,
                        Math.min(maxSafeBatch, number(result, "acceptedAmount"))));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                maxSafeBatch = 0L;
                return 0L;
            }
        }

        @Override
        public boolean unbounded() {
            return maxSafeBatch == Long.MAX_VALUE;
        }

        @Override
        public long submit(IPatternDetails details, KeyCounter[] inputs, long requested) {
            try {
                Object result = submit.invoke(target, request(details, inputs, requested));
                long accepted = number(result, "acceptedAmount");
                long unaccepted = number(result, "unacceptedAmount");
                if (accepted < 0L || accepted > requested || unaccepted != requested - accepted) {
                    throw new IllegalStateException("Invalid AE2LT batch ownership result");
                }
                return unaccepted;
            } catch (ReflectiveOperationException failure) {
                // Submission may already have transferred inputs; never refund or replay an unknown result.
                throw new IllegalStateException("Cannot determine AE2LT batch ownership", failure);
            }
        }

        private Object request(IPatternDetails details, KeyCounter[] inputs, long requested)
                throws ReflectiveOperationException {
            var snapshot = Arrays.stream(inputs).map(ECOFastPathStacks::copyCounter).toList();
            Object[] arguments = new Object[requestComponents.length];
            for (int index = 0; index < arguments.length; index++) {
                arguments[index] = switch (requestComponents[index].getName()) {
                    case "apiVersion" -> apiVersion;
                    case "targetCapabilityId" -> capabilityId;
                    case "processingId" -> details.getDefinition();
                    case "inputsPerCraft" -> snapshot;
                    case "requestedAmount" -> requested;
                    case "nonce" -> nonce;
                    default -> throw new IllegalStateException("Unsupported AE2LT request field");
                };
            }
            return requestConstructor.newInstance(arguments);
        }

        private static long number(Object value, String name) throws ReflectiveOperationException {
            return ((Number) value.getClass().getMethod(name).invoke(value)).longValue();
        }
    }
}
