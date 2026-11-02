package cn.dancingsnow.neoecoae.util;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;

/**
 * Auto-registered (see junit-platform.properties) safety net: a test class that fails while a
 * {@code mockStatic} is open, or never closes one, would otherwise leave it registered on the shared test
 * thread and make every later {@code mockStatic} of the same class fail ("static mocking is already
 * registered in the current thread"), so results depended on class execution order. After each test
 * class all inline/static mocks are dropped and the stub FML environment is re-installed.
 */
public final class StaticMockLeakGuard implements AfterAllCallback {
    @Override
    public void afterAll(ExtensionContext context) {
        // A test that left an unfinished stubbing would otherwise make the re-install below throw.
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().reset();
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().resetOngoingStubbing();
        Mockito.framework().clearInlineMocks();
        TestFmlLoaderSessionListener.reinstall();
    }
}
