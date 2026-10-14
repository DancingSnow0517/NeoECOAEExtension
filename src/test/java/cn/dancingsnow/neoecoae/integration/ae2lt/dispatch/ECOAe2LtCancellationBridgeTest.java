package cn.dancingsnow.neoecoae.integration.ae2lt.dispatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import appeng.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOAe2LtCancellationBridgeTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void cancellationRecoversWorkersBeforeTheCpuClearsItsJob() {
        var jobId = UUID.randomUUID();
        var level = mock(Level.class);
        var logic = new FakeLogic(new FakeCpu(level), new FakeJob(jobId));

        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            ECOAe2LtCancellationBridge.cancel(logic);

            lifecycle.verify(() -> ECOCraftingJobLifecycle.cancelAndRecover(level, jobId));
            lifecycle.verifyNoMoreInteractions();
        }
    }

    @Test
    void cancellationStillPublishesTheDecisionWithoutACpu() {
        var jobId = UUID.randomUUID();
        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            ECOAe2LtCancellationBridge.cancel(new FakeLogic(null, new FakeJob(jobId)));

            lifecycle.verify(() -> ECOCraftingJobLifecycle.cancelAndRecover(null, jobId));
        }
    }

    @Test
    void releasedTimeWheelJobRecoversThroughItsCraftingLink() throws Exception {
        var jobId = UUID.randomUUID();
        var level = mock(Level.class);
        var cpu = mock(TimeWheelCraftingCPU.class);
        when(cpu.getLevel()).thenReturn(level);
        var logic = mock(Ae2LtTimeWheelCraftingCpuLogic.class);
        Field jobField = accessibleField(logic.getClass(), "job");
        var job = mock(jobField.getType());
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(jobId);
        accessibleField(jobField.getType(), "link").set(job, link);
        jobField.set(logic, job);
        accessibleField(logic.getClass(), "cpu").set(logic, cpu);

        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            ECOAe2LtCancellationBridge.cancel(logic);

            lifecycle.verify(() -> ECOCraftingJobLifecycle.cancelAndRecover(level, jobId));
        }
    }

    private static Field accessibleField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test
    void anAlreadyClearedJobIsAQuietNoOp() {
        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            assertDoesNotThrow(() -> ECOAe2LtCancellationBridge.cancel(new FakeLogic(new FakeCpu(null), null)));
            lifecycle.verifyNoInteractions();
        }
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
        private final UUID id;

        private FakeJob(UUID id) {
            this.id = id;
        }
    }
}
