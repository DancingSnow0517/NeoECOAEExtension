package cn.dancingsnow.neoecoae.api.me.pattern;

import appeng.api.crafting.IPatternDetails;

/**
 * Explicit opt-in to execution optimizations. Register during common initialization.
 *
 * <p>Authorizing the verified assembler path guarantees that repeated assembly with the same concrete
 * input keys and level has deterministic outputs/remainders and no external side effects. ECO still
 * simulates and compares the concrete result before minting a fast-path credential. Merely implementing
 * {@code IPatternDetails} never grants this guarantee, and ordinary provider dispatch needs no registration.
 * Keep the guarantee stable while registered; unregister and register again when it changes so cached
 * verification and already-issued credentials are invalidated.</p>
 */
public interface ECOExecutionOptimizationAdapter {
    boolean supports(IPatternDetails pattern);

    ECOExecutionOptimization describe(IPatternDetails pattern);
}
