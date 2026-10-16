package cn.dancingsnow.neoecoae.crafting.planner;

/** Source-compatible AE planning name for the version-independent cancellation contract. */
@FunctionalInterface
public interface ECOCancellation extends cn.dancingsnow.neoecoae.core.concurrent.Cancellation {
    ECOCancellation NONE = () -> {};
    ECOCancellation INTERRUPTIBLE = cn.dancingsnow.neoecoae.core.concurrent.Cancellation.INTERRUPTIBLE::checkpoint;
}
