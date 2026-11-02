package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachment;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachmentRegistry;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimSink;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Optional public AE2LT accounting API; no foreign CPU fields or mixin targets are inspected. */
public final class ECOOverloadCpuAccountingBridge {
    private static final String API = "com.moakiee.ae2lt.api.crafting.Ae2LtCraftingIntegration";
    private static final ResourceLocation ATTACHMENT = ResourceLocation.fromNamespaceAndPath("ae2lt", "overload_outputs");
    private static final Registration ORDINARY = new Registration() {
        public boolean canDispatch() { return true; }
        public void registerAccepted(long amount) { }
    };
    private static final @Nullable Class<?> API_CLASS = findApi();
    private static final @Nullable Method PREPARE = method("prepareOverload", Object.class,
            IPatternDetails.class, UUID.class, AEKey.class);
    private static final @Nullable Method PREPARE_WITH_WAITING = method("prepareOverload", Object.class,
            IPatternDetails.class, UUID.class, AEKey.class, KeyCounter.class);
    private static final @Nullable Method STRICT = method("strictOutputMatch", Object.class,
            AEKey.class, long.class, long.class);
    private static final @Nullable Method CLAIM = method("claimOverloadOutput", Object.class,
            UUID.class, AEKey.class, long.class, Actionable.class, BiFunction.class);
    private static final @Nullable Method SAVE = method("saveOverloadState", Object.class,
            UUID.class, HolderLookup.Provider.class);
    private static final @Nullable Method LOAD = method("loadOverloadState", Object.class,
            UUID.class, CompoundTag.class, HolderLookup.Provider.class);
    private static final @Nullable Method CLEAR = method("clearOverloadState", Object.class);
    private static final @Nullable ECOAe2LtReleasedAccounting RELEASED = releasedAccounting();
    private static boolean initialized;

    private ECOOverloadCpuAccountingBridge() { }

    public static synchronized void initialize() {
        if (initialized || RELEASED == null && (CLAIM == null || SAVE == null || LOAD == null || CLEAR == null)) return;
        ECOCraftingJobAttachmentRegistry.register(ATTACHMENT, context -> {
            if (!(context.cpu() instanceof cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU cpu)) return null;
            var sink = cpu.getOutputClaimSink();
            return sink == null ? null : new Attachment(sink, context.craftingJobId());
        });
        initialized = true;
    }

    public interface Registration {
        boolean canDispatch();
        void registerAccepted(long amount);
    }

    public static Registration prepare(Object owner, IPatternDetails pattern, UUID jobId, @Nullable AEKey output) {
        return prepare(owner, pattern, jobId, output, null);
    }

    public static Registration prepare(Object owner, IPatternDetails pattern, UUID jobId,
            @Nullable AEKey output, @Nullable KeyCounter waiting) {
        if (RELEASED != null) return RELEASED.prepare(owner, pattern, jobId, output, waiting);
        if (PREPARE == null) return ORDINARY;
        Object registration = owner != null && waiting != null && PREPARE_WITH_WAITING != null
                ? invoke(PREPARE_WITH_WAITING, null, owner, pattern, jobId, output, waiting)
                : invoke(PREPARE, null, owner, pattern, jobId, output);
        try {
            var allowed = registration.getClass().getMethod("canDispatch");
            var accepted = registration.getClass().getMethod("registerAccepted", long.class);
            return new Registration() {
                public boolean canDispatch() { return (boolean) invoke(allowed, registration); }
                public void registerAccepted(long amount) {
                    if (!(boolean) invoke(accepted, registration, amount))
                        throw new IllegalStateException("AE2LT rejected accepted-output accounting");
                }
            };
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException("Incompatible AE2LT overload registration", unavailable);
        }
    }

    public static long strictMatch(Object owner, AEKey actual, long rawMatch, long exactWaiting) {
        if (RELEASED != null) return RELEASED.strictMatch(owner, actual, rawMatch, exactWaiting);
        return STRICT == null ? rawMatch : (long) invoke(STRICT, null, owner, actual, rawMatch, exactWaiting);
    }

    public static long claim(ECOCraftingOutputClaimSink owner, UUID jobId, AEKey actual,
            long amount, Actionable mode) {
        if (amount <= 0 || CLAIM == null && RELEASED == null) return 0;
        BiFunction<GenericStack, Actionable, Long> receiver = (expected, action) -> owner.claimCraftingOutput(
                new ECOCraftingOutputClaimRequest(jobId, expected.what(), actual, expected.amount(), action)).claimedAmount();
        return RELEASED != null ? RELEASED.claim(owner, jobId, actual, amount, mode, receiver)
            : (long) invoke(CLAIM, null, owner, jobId, actual, amount, mode, receiver);
    }

    /** Discards live state before replacing a checkpoint; the attachment restores the new job afterward. */
    public static void clear(Object owner) {
        if (RELEASED != null) RELEASED.clear(owner);
        else if (CLEAR != null) invoke(CLEAR, null, owner);
    }

    private record Attachment(ECOCraftingOutputClaimSink owner, UUID jobId) implements ECOCraftingJobAttachment {
        public ResourceLocation id() { return ATTACHMENT; }
        public CompoundTag save(HolderLookup.Provider registries) {
            return RELEASED != null ? RELEASED.save(owner, jobId, registries)
                : (CompoundTag) invoke(SAVE, null, owner, jobId, registries);
        }
        public void load(CompoundTag tag, HolderLookup.Provider registries) {
            if (RELEASED != null) RELEASED.load(owner, jobId, tag, registries);
            else invoke(LOAD, null, owner, jobId, tag, registries);
        }
        public void clear(ECOCraftingJobResult result) { ECOOverloadCpuAccountingBridge.clear(owner); }
    }

    private static @Nullable Class<?> findApi() {
        try { return Class.forName(API, false, ECOOverloadCpuAccountingBridge.class.getClassLoader()); }
        catch (ClassNotFoundException | LinkageError absent) { return null; }
    }

    private static @Nullable Method method(String name, Class<?>... types) {
        try { return API_CLASS == null ? null : API_CLASS.getMethod(name, types); }
        catch (NoSuchMethodException | LinkageError unavailable) { return null; }
    }

    private static @Nullable ECOAe2LtReleasedAccounting releasedAccounting() {
        if (API_CLASS == null || PREPARE_WITH_WAITING != null && STRICT != null && CLAIM != null
            && SAVE != null && LOAD != null && CLEAR != null) return null;
        try { return new ECOAe2LtReleasedAccounting(); }
        catch (LinkageError unavailable) { return null; }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("AE2LT overload accounting failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot determine AE2LT overload accounting", failure);
        }
    }
}
