package cn.dancingsnow.neoecoae.api.me.output;

/**
 * Atomically claims a dynamic or substituted crafting output against one ECO job.
 *
 * <p>Implementations validate the job identity, consume the reserved expected output, route the actual output,
 * update progress and perform terminal-job checks as one server-thread operation. Callers should use
 * {@link ECOCraftingOutputClaimRequest} instead of reading ECO's waiting inventory or invoking private job methods.</p>
 */
public interface ECOCraftingOutputClaimSink {

    ECOCraftingOutputClaimResult claimCraftingOutput(ECOCraftingOutputClaimRequest request);
}
