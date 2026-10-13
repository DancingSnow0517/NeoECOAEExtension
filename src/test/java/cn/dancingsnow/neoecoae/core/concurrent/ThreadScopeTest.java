package cn.dancingsnow.neoecoae.core.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadScopeTest {

    @Test
    void openSetsCurrentAndCloseRemovesOutermostValue() {
        ThreadScope<String> scope = new ThreadScope<>();
        assertNull(scope.current());

        ThreadScope.Scope outer = scope.open("a");
        assertEquals("a", scope.current());

        outer.close();
        assertNull(scope.current());
    }

    @Test
    void nestingRestoresEachPreviousValueInOrder() {
        ThreadScope<String> scope = new ThreadScope<>();
        ThreadScope.Scope outer = scope.open("a");
        ThreadScope.Scope inner = scope.open("b");
        assertEquals("b", scope.current());

        inner.close();
        assertEquals("a", scope.current());

        outer.close();
        assertNull(scope.current());
    }

    @Test
    void exceptionInsideScopeRestoresPreviousValue() {
        ThreadScope<String> scope = new ThreadScope<>();
        ThreadScope.Scope outer = scope.open("a");

        assertThrows(IllegalStateException.class, () -> {
            try (ThreadScope.Scope ignored = scope.open("b")) {
                throw new IllegalStateException("boom");
            }
        });
        assertEquals("a", scope.current());

        outer.close();
        assertNull(scope.current());
    }

    @Test
    void nullValueIsStoredAndRestoredLikeAnyOther() {
        ThreadScope<String> scope = new ThreadScope<>();
        ThreadScope.Scope outer = scope.open("a");
        ThreadScope.Scope inner = scope.open(null);
        assertNull(scope.current());

        inner.close();
        assertEquals("a", scope.current());

        outer.close();
        assertNull(scope.current());
    }

    @Test
    void valuesAreIsolatedPerThread() throws InterruptedException {
        ThreadScope<String> scope = new ThreadScope<>();
        String[] seenElsewhere = {"unset"};
        try (ThreadScope.Scope ignored = scope.open("main")) {
            Thread other = new Thread(() -> seenElsewhere[0] = scope.current());
            other.start();
            other.join();
        }
        assertNull(seenElsewhere[0]);
    }
}
