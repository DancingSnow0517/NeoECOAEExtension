package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Reflection-only optional AE2LT 2.0.9 batch bridge. */
public final class ECOAe2LtBatchCapability {
    private static final String LIGHTNING = "com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider";
    private static final String TIANSHU = "com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer";
    private ECOAe2LtBatchCapability() {}
    @Nullable public static Session open(Object provider) {
        if (provider == null || ModList.get() == null || !ModList.get().isLoaded("ae2lt")) return null;
        if (hasType(provider, LIGHTNING)) return new ReflectiveSession(provider);
        if (hasType(provider, TIANSHU)) return new ReflectiveSession(provider);
        return null;
    }
    private static boolean hasType(Object value, String type) {
        for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass())
            for (Class<?> i : c.getInterfaces()) if (i.getName().equals(type)) return true;
        return false;
    }
    public interface Session { long inspect(IPatternDetails d, KeyCounter[] i, long n); boolean unbounded(); long submit(IPatternDetails d, KeyCounter[] i, long n); }
    private static final class ReflectiveSession implements Session {
        private final Object target; private final UUID nonce = UUID.randomUUID(); private long maxSafeBatch;
        ReflectiveSession(Object target) { this.target = target; }
        public long inspect(IPatternDetails d, KeyCounter[] i, long n) { try { Object r=call("inspect",d.getDefinition(),snapshot(i),n,nonce); maxSafeBatch=number(r,"maxSafeBatch",Long.MAX_VALUE); return Math.max(0,Math.min(n,number(r,"acceptedAmount",0))); } catch (ReflectiveOperationException|RuntimeException e) { maxSafeBatch=0; return 0; } }
        public boolean unbounded() { return maxSafeBatch == Long.MAX_VALUE; }
        public long submit(IPatternDetails d, KeyCounter[] i, long n) { try { Object r=call("submit",d.getDefinition(),snapshot(i),n,nonce); long a=number(r,"acceptedAmount",-1), u=number(r,"unacceptedAmount",Long.MIN_VALUE); return a>=0&&a<=n&&u==n-a?n-a:n; } catch (ReflectiveOperationException|RuntimeException e) { return n; } }
        private Object call(String name,Object... args) throws ReflectiveOperationException { for(Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==args.length) return m.invoke(target,args); throw new NoSuchMethodException(name); }
        private static List<List<appeng.api.stacks.GenericStack>> snapshot(KeyCounter[] i) { return Arrays.stream(i).map(ECOFastPathStacks::copyCounter).toList(); }
        private static long number(Object value,String name,long fallback) throws ReflectiveOperationException { if(value==null)return fallback; try{return ((Number)value.getClass().getMethod(name).invoke(value)).longValue();}catch(NoSuchMethodException e){return fallback;} }
    }
}
