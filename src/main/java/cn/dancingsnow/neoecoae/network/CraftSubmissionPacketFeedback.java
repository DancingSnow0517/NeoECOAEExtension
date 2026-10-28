package cn.dancingsnow.neoecoae.network;

import appeng.crafting.execution.CraftingSubmitResult;
import appeng.menu.me.crafting.CraftConfirmMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps submission failures out of an unobserved server-task future. */
final class CraftSubmissionPacketFeedback {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");

    private CraftSubmissionPacketFeedback() {}

    static void submit(CraftConfirmMenu menu, String route, boolean forced, Runnable submission) {
        try {
            submission.run();
        } catch (RuntimeException | LinkageError failure) {
            menu.setAutoStart(false);
            menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(CraftingSubmitResult.INCOMPLETE_PLAN);
            LOGGER.error("[craft-submit] Submission threw: route={}, container={}, forced={}",
                route, menu.containerId, forced, failure);
        }
    }
}
