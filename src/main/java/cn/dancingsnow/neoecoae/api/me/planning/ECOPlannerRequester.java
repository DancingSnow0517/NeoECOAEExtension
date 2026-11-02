package cn.dancingsnow.neoecoae.api.me.planning;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import java.util.Objects;

/** Delegating requester that opts one calculation entering through AE2's normal service path into ECO planning. */
public final class ECOPlannerRequester implements ECOPlannerRequest {
    private final ICraftingSimulationRequester delegate;
    private final ECOPlannerOptions options;

    public ECOPlannerRequester(ICraftingSimulationRequester delegate, ECOPlannerOptions options) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public IActionSource getActionSource() {
        return delegate.getActionSource();
    }

    @Override
    public ECOPlannerOptions neoecoae$getPlannerOptions() {
        return options;
    }
}
