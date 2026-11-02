package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;

import java.util.Map;

/**
 * Binary compatibility target for integrations compiled against the original NeoECOAE package layout.
 * The execution implementation subclasses this type and shares its task map with the live job state.
 */
@Deprecated(forRemoval = false)
public abstract class ExecutingCraftingJob {
    protected Map<IPatternDetails, Object> tasks;

    protected ExecutingCraftingJob() {
    }

    /** Legacy Mixin target for reading the remaining count of a pattern task. */
    public static class TaskProgress {
        public long value;

        protected TaskProgress() {
        }
    }
}
