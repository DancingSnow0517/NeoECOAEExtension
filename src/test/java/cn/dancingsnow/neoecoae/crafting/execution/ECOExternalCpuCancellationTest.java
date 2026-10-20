package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class ECOExternalCpuCancellationTest {
    @Test
    void nativeCpuCancellationTerminatesTheMatchingEcoWorkerJob() throws Exception {
        var jobId = UUID.randomUUID();
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(jobId);
        var job = mock(ExecutingCraftingJob.class,
                withSettings().extraInterfaces(ECOExternalCpuJob.class));
        var access = (ECOExternalCpuJob) job;
        when(access.neoecoae$link()).thenReturn(link);

        var mixin = mock(Ae2CraftingCpuFastPathMixin.class, CALLS_REAL_METHODS);
        setField(mixin, "job", job);
        setField(mixin, "cluster", mock(CraftingCPUCluster.class));
        Method hook = Ae2CraftingCpuFastPathMixin.class.getDeclaredMethod(
                "neoecoae$releaseWorkerOutput", boolean.class, CallbackInfo.class);
        hook.setAccessible(true);

        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            assertDoesNotThrow(() -> hook.invoke(mixin, false,
                    new CallbackInfo("finishJob", false)));
            lifecycle.verify(() -> ECOCraftingJobLifecycle.cancelAndRecover(null, jobId));
        }
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getSuperclass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
