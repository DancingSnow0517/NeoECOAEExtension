package cn.dancingsnow.neoecoae.api.me.planning;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import java.util.Objects;

/** Delegating requester used by future ECO integrations that intentionally use AE2's calculation entry point. */
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
