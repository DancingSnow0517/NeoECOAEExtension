package cn.dancingsnow.neoecoae.compat.dataenergistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional, reflection-isolated bridge to Data Energistics' live counted-provider registry.
 *
 * <p>The registry can attach a counted adapter to an otherwise plain {@link ICraftingProvider}; Useless Mod's
 * ME Pattern Assembly uses exactly that mechanism. Keeping every Data Energistics type behind this boundary lets
 * NeoECO continue to load when the optional mod is absent.</p>
 */
public final class ECODataEnergisticsCountedBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final ReflectionApi API = ReflectionApi.load();

    private ECODataEnergisticsCountedBridge() {
    }

    public static boolean isAvailable() {
        return API != null;
    }

    public static boolean supports(ICraftingProvider provider) {
        if (API == null || provider == null) return false;
        try {
            return (boolean) API.supportsCountedDispatch.invoke(null, provider);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.debug("Data Energistics counted-provider lookup failed for {}", provider, unwrap(failure));
            return false;
        }
    }

    /**
     * Captures and immediately re-prepares one live target. Preparation remains read-only; the returned admission
     * owns no CPU input until {@link PreparedAdmission#commit(KeyCounter[])} is called.
     */
    @Nullable
    public static PreparedAdmission prepare(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] prototype,
            long requestedCount,
            String patternIdentity,
            long captureTick) {
        if (API == null || requestedCount <= 1L) return null;
        try {
            long sequence = Integer.toUnsignedLong(System.identityHashCode(provider)) + 1L;
            Object providerId = API.providerIdConstructor.newInstance(1L, sequence);
            long capacityRevision = (long) API.mutationRevision.invoke(null);
            @SuppressWarnings("unchecked")
            List<Object> snapshots = (List<Object>) API.captureCapacity.invoke(
                null, provider, providerId, pattern, prototype, requestedCount,
                patternIdentity == null || patternIdentity.isBlank() ? pattern.toString() : patternIdentity,
                0L, capacityRevision, Math.max(0L, captureTick));
            Object availability = API.allTargets.invoke(null);
            for (Object snapshot : snapshots) {
                Object preparation = API.prepare.invoke(
                    null, provider, pattern, prototype, requestedCount, snapshot, availability);
                if (!(boolean) API.preparationAccepted.invoke(preparation)) continue;
                Object admission = API.preparationAdmission.invoke(preparation);
                if (admission == null) continue;
                long count = (long) API.admissionCount.invoke(admission);
                if (count > 1L && count <= requestedCount) {
                    return new PreparedAdmission(API, admission, count);
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.error("Data Energistics counted-provider preparation failed for " + provider, unwrap(failure));
            return null;
        }
    }

    public static final class PreparedAdmission {
        private final ReflectionApi api;
        private final Object admission;
        private final long count;

        private PreparedAdmission(ReflectionApi api, Object admission, long count) {
            this.api = api;
            this.admission = admission;
            this.count = count;
        }

        public long count() {
            return count;
        }

        public boolean commit(KeyCounter[] prototype) {
            try {
                return (boolean) api.admissionCommit.invoke(admission, (Object) prototype);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot access Data Energistics counted admission", failure);
            } catch (InvocationTargetException failure) {
                throw propagate(failure.getCause());
            }
        }

        /** Failure is conservative: an unreadable ownership state must never duplicate provider-owned inputs. */
        public boolean hasTransferredInputOwnership() {
            try {
                return (boolean) api.admissionTransferred.invoke(admission);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                LOGGER.error("Data Energistics counted admission could not report input ownership", unwrap(failure));
                return true;
            }
        }
    }

    @Nullable
    private static Throwable unwrap(Throwable failure) {
        return failure instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause() : failure;
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Data Energistics counted admission failed", failure);
    }

    private record ReflectionApi(
            Method supportsCountedDispatch,
            Method mutationRevision,
            Method captureCapacity,
            Method prepare,
            Constructor<?> providerIdConstructor,
            Method allTargets,
            Method preparationAccepted,
            Method preparationAdmission,
            Method admissionCount,
            Method admissionCommit,
            Method admissionTransferred) {

        @Nullable
        private static ReflectionApi load() {
            try {
                ClassLoader loader = ECODataEnergisticsCountedBridge.class.getClassLoader();
                Class<?> adapters = Class.forName(
                    "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters",
                    false, loader);
                Class<?> providerId = Class.forName(
                    "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId",
                    false, loader);
                Class<?> snapshot = Class.forName(
                    "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot",
                    false, loader);
                Class<?> availability = Class.forName(
                    "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability",
                    false, loader);
                Class<?> preparation = Class.forName(
                    "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation",
                    false, loader);
                Class<?> admission = Class.forName(
                    "com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission",
                    false, loader);
                return new ReflectionApi(
                    adapters.getMethod("supportsCountedDispatch", ICraftingProvider.class),
                    adapters.getMethod("mutationRevision"),
                    adapters.getMethod("captureCapacity", ICraftingProvider.class, providerId,
                        IPatternDetails.class, KeyCounter[].class, long.class, String.class,
                        long.class, long.class, long.class),
                    adapters.getMethod("prepare", ICraftingProvider.class, IPatternDetails.class,
                        KeyCounter[].class, long.class, snapshot, availability),
                    providerId.getConstructor(long.class, long.class),
                    availability.getMethod("all"),
                    preparation.getMethod("accepted"),
                    preparation.getMethod("admission"),
                    admission.getMethod("count"),
                    admission.getMethod("commit", KeyCounter[].class),
                    admission.getMethod("hasTransferredInputOwnership"));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
