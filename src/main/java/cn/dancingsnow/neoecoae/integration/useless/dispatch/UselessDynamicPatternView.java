package cn.dancingsnow.neoecoae.integration.useless.dispatch;

/** Runtime-safe view injected into Useless Mod's dynamic pattern details. */
public interface UselessDynamicPatternView {
    boolean neoecoae$isItemIdInput(int slot);

    boolean neoecoae$isTagInput(int slot);

    boolean neoecoae$isFluidTagInput(int slot);

    boolean neoecoae$usesDynamicOutputs();
}
