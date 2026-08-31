package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Scope-aware ordinary dispatch policy. Plain providers are bounded only by the task and CPU dispatch budgets. */
public final class ECOAdaptiveDispatchStrategy implements ECOCraftingDispatchStrategy {
    public static final int GENERIC_INITIAL_WINDOW = 16;
    public static final int GENERIC_MIN_WINDOW = 1;
    private final Map<Object, AdaptiveWindowState> states = new IdentityHashMap<>();
    private int cursor;

    public static int genericWindowCeiling(long remaining) {
        if (remaining < 1_000L) return 64;
        if (remaining < 10_000L) return 128;
        if (remaining < 100_000L) return 256;
        if (remaining < 1_000_000L) return 512;
        if (remaining < 100_000_000L) return 1024;
        return 2048;
    }

    @Override public synchronized DispatchDecision choose(DispatchContext context) {
        List<ICraftingProvider> ordered = new ArrayList<>(context.candidateProviders());
        if (!ordered.isEmpty()) {
            int offset = Math.floorMod(cursor++, ordered.size());
            var rotated = new ArrayList<ICraftingProvider>(ordered.size());
            rotated.addAll(ordered.subList(offset, ordered.size()));
            rotated.addAll(ordered.subList(0, offset));
            ordered = rotated;
        }
        long total = 0L;
        long tick = TickHandler.instance().getCurrentTick();
        for (var provider : ordered) {
            AdaptiveWindowState state = states.computeIfAbsent(scope(provider), ignored -> new AdaptiveWindowState());
            state.resetTick(tick, genericWindowCeiling(context.taskRemaining()));
            long allowance = provider instanceof ECOParallelCraftingProvider capable
                ? Math.max(0L, capable.eco$getAvailableParallelSlots())
                : context.dispatchBudget();
            total = Math.min(Integer.MAX_VALUE, total + allowance);
        }
        return new DispatchDecision(ordered, (int) Math.min(context.dispatchBudget(), total));
    }

    public synchronized void onAccepted(ICraftingProvider provider) { state(provider).consume(1); }
    public synchronized void onBusy(ICraftingProvider provider, boolean hadAccepted) { if (!hadAccepted) state(provider).shrink(); }
    public synchronized void onRejected(ICraftingProvider provider) { state(provider).shrink(); }
    public synchronized void onException(ICraftingProvider provider) { state(provider).resetWindow(); }
    private AdaptiveWindowState state(ICraftingProvider provider) { return states.computeIfAbsent(scope(provider), ignored -> new AdaptiveWindowState()); }
    private Object scope(ICraftingProvider provider) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity bus && bus.getCraftingController() != null) {
            return bus.getCraftingController().getDispatchScope();
        }
        return provider;
    }
    static final class AdaptiveWindowState {
        int currentWindow = GENERIC_INITIAL_WINDOW; int lastCeiling = 2048;
        long lastTick = Long.MIN_VALUE; long usedThisTick;
        void resetTick(long tick, int ceiling) {
            if (lastTick != tick) {
                if (lastTick != Long.MIN_VALUE && usedThisTick >= currentWindow) grow(lastCeiling);
                lastTick = tick; usedThisTick = 0;
            }
            lastCeiling = ceiling;
        }
        long remainingCredit() { return Math.max(0L, currentWindow - usedThisTick); }
        void consume(long count) { usedThisTick += count; }
        void grow(int ceiling) { currentWindow = Math.min(ceiling, Math.min(2048, Math.max(GENERIC_MIN_WINDOW, currentWindow * 2))); }
        void shrink() { currentWindow = Math.max(GENERIC_MIN_WINDOW, currentWindow / 2); }
        void resetWindow() { currentWindow = GENERIC_MIN_WINDOW; }
    }
}
