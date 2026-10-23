package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting.AdvancedAeCraftingCpuLogicMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.mockito.Mockito.*;

class ECOExternalCpuCompletionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completionPublishesTheTerminalDecisionEvenWhenTheCpuHasLostItsGrid(boolean advanced) throws Exception {
        var type = advanced ? AdvancedAeCraftingCpuLogicMixin.class : Ae2CraftingCpuFastPathMixin.class;
        var mixin = mock(type, CALLS_REAL_METHODS);
        var jobField = type.getDeclaredField("job");
        jobField.setAccessible(true);
        var job = mock(jobField.getType(), withSettings().extraInterfaces(ECOExternalCpuJob.class));
        var link = mock(CraftingLink.class);
        var jobId = UUID.randomUUID();
        when(link.getCraftingID()).thenReturn(jobId);
        when(((ECOExternalCpuJob) job).neoecoae$link()).thenReturn(link);
        jobField.set(mixin, job);
        var ownerField = type.getDeclaredField(advanced ? "cpu" : "cluster");
        ownerField.setAccessible(true);
        var owner = mock(ownerField.getType());
        var level = mock(Level.class);
        when((Level) ownerField.getType().getMethod("getLevel").invoke(owner)).thenReturn(level);
        ownerField.set(mixin, owner);
        var hook = type.getDeclaredMethod(advanced ? "neoecoae$releaseCompletedWorkerOutputs"
            : "neoecoae$releaseWorkerOutput", boolean.class, CallbackInfo.class);
        hook.setAccessible(true);

        try (var lifecycle = mockStatic(ECOCraftingJobLifecycle.class)) {
            hook.invoke(mixin, true, new CallbackInfo("finishJob", false));
            lifecycle.verify(() -> ECOCraftingJobLifecycle.finish(level, jobId, true));
            lifecycle.verifyNoMoreInteractions();
        }
    }
}
