package cn.dancingsnow.neoecoae.util;

/**
 * 26.1: items/blocks registered by a test after the global bootstrap have no default data components
 * ("Components not bound yet" on {@code new ItemStack(item)}). Call {@link #bindAll()} after registering them;
 * it re-runs the vanilla component binding (see {@link TestFmlLoaderSessionListener#bootstrapGame()}).
 */
public final class TestComponents {
    private TestComponents() {}

    public static void bindAll() {
        TestFmlLoaderSessionListener.bootstrapGame();
    }
}
