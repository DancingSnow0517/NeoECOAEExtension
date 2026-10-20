package cn.dancingsnow.neoecoae.api.me.lifecycle;

/** Optional observer for stable ECO crafting lifecycle events. */
public interface ECOCraftingLifecycleListener {

    default void onJobStarted(ECOCraftingJobContext context) {
    }

    default void onPatternDispatched(ECOCraftingDispatchEvent event) {
    }

    default void onJobFinished(ECOCraftingJobContext context, ECOCraftingJobResult result) {
    }
}
