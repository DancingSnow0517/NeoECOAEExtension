package cn.dancingsnow.neoecoae.compat.ae2lt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;

import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOAe2LtCancellationBridgeTest {
    @Test
    void publishesThePrivateTimeWheelJobIdBeforeCancellationClearsIt() {
        UUID jobId = UUID.randomUUID();
        Level level = mock(Level.class);
        FakeCpu cpu = new FakeCpu(level);
        FakeLogic logic = new FakeLogic(cpu, new FakeJob(new FakeLink(jobId)));

        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            ECOAe2LtCancellationBridge.cancel(logic);
            lifecycle.verify(() -> ECOCraftingJobLifecycle.cancelAndRecover(level, jobId));
        }
    }

    @Test
    void anAlreadyClearedJobIsAQuietNoOp() {
        assertDoesNotThrow(() -> ECOAe2LtCancellationBridge.cancel(new FakeLogic(new FakeCpu(null), null)));
    }

    private static final class FakeLogic {
        private final FakeCpu cpu;
        private final FakeJob job;

        private FakeLogic(FakeCpu cpu, FakeJob job) {
            this.cpu = cpu;
            this.job = job;
        }
    }

    private static final class FakeCpu {
        private final Level level;

        private FakeCpu(Level level) {
            this.level = level;
        }

        private Level getLevel() {
            return level;
        }
    }

    private static final class FakeJob {
        private final FakeLink link;

        private FakeJob(FakeLink link) {
            this.link = link;
        }
    }

    private static final class FakeLink {
        private final UUID id;

        private FakeLink(UUID id) {
            this.id = id;
        }

        private UUID getCraftingID() {
            return id;
        }
    }
}
