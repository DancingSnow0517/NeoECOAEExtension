package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Method;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Isolated AE2LT 2.0.9 overload-accounting adapter. */
public final class ECOOverloadCpuAccountingBridge {
    private static final String API = "com.moakiee.ae2lt.api.crafting.Ae2LtCraftingIntegration";
    private ECOOverloadCpuAccountingBridge() {}
    @Nullable
    public interface Registration { boolean canDispatch(); void registerAccepted(long amount); }
    public static Registration prepare(Object cpuLogic, IPatternDetails pattern, UUID jobId, @Nullable AEKey output) {
        try {
            Class<?> api = Class.forName(API, false, ECOOverloadCpuAccountingBridge.class.getClassLoader());
            for (Method m : api.getMethods()) if (m.getName().equals("prepareOverload") && m.getParameterCount() == 4)
                return new ReflectiveRegistration(m.invoke(null, cpuLogic, pattern, jobId, output));
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        // AE2/ordinary CPUs remain dispatchable when the optional AE2LT API is absent.
        return new Registration() { public boolean canDispatch() { return true; } public void registerAccepted(long amount) {} };
    }
    private record ReflectiveRegistration(Object value) implements Registration {
        public boolean canDispatch() { return invokeBoolean("canDispatch", true); }
        public void registerAccepted(long amount) { invoke("registerAccepted", amount); }
        private boolean invokeBoolean(String n, boolean fallback) { try { return (Boolean)value.getClass().getMethod(n).invoke(value); } catch (ReflectiveOperationException e) { return fallback; } }
        private void invoke(String n, long amount) { try { value.getClass().getMethod(n, long.class).invoke(value, amount); } catch (ReflectiveOperationException ignored) {} }
    }
}
