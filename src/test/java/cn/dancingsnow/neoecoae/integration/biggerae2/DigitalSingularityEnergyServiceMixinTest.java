package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.me.Grid;
import appeng.me.service.EnergyService;
import cn.dancingsnow.neoecoae.mixins.ae2.storage.DigitalSingularityEnergyServiceMixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DigitalSingularityEnergyServiceMixinTest {
    @Test
    void pinnedAe2HasAllThreeInjectionPointsForDemandBranchAndPaymentThreshold() throws Exception {
        var target = new ClassNode();
        try (var stream = EnergyService.class.getResourceAsStream("EnergyService.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(target, 0);
        }
        int demandReads = 0;
        int paymentReads = 0;
        for (var method : target.methods) {
            if (!Set.of("getIdlePowerUsage", "onServerEndTick").contains(method.name)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals("appeng/me/service/EnergyService")
                    && field.name.equals("drainPerTick") && field.desc.equals("D")) {
                    if (method.name.equals("getIdlePowerUsage")) demandReads++;
                    else paymentReads++;
                }
            }
        }
        assertEquals(1, demandReads);
        assertEquals(2, paymentReads, "Both the positive-demand branch and payment threshold must include the surcharge");
        var hook = DigitalSingularityEnergyServiceMixin.class.getDeclaredMethod(
            "neoecoae$includeSingularityPower", double.class).getAnnotation(ModifyExpressionValue.class);
        assertArrayEquals(new String[]{"getIdlePowerUsage", "onServerEndTick"}, hook.method());
        assertEquals("Lappeng/me/service/EnergyService;drainPerTick:D", hook.at()[0].target());
        assertEquals(3, hook.require());
        assertEquals(3, hook.allow());
    }

    @Test
    void energyHookRefreshesBeforePaymentAndRecoversOrdinaryDrainAfterCappedDemand() throws Exception {
        var service = mock(BiggerAE2SingularityPowerService.class);
        var grid = mock(Grid.class);
        when(grid.getService(BiggerAE2SingularityPowerService.class)).thenReturn(service);
        var mixin = mock(DigitalSingularityEnergyServiceMixin.class, CALLS_REAL_METHODS);
        var field = DigitalSingularityEnergyServiceMixin.class.getDeclaredField("grid");
        field.setAccessible(true);
        field.set(mixin, grid);
        var refresh = DigitalSingularityEnergyServiceMixin.class.getDeclaredMethod(
            "neoecoae$refreshSingularityPower", CallbackInfo.class);
        refresh.setAccessible(true);
        refresh.invoke(mixin, new CallbackInfo("onServerEndTick", false));
        verify(service).refresh();

        var include = DigitalSingularityEnergyServiceMixin.class.getDeclaredMethod(
            "neoecoae$includeSingularityPower", double.class);
        include.setAccessible(true);
        double ordinaryDrain = 257.5;
        double surcharge = 6 * (1_048_576d - BiggerAE2SingularityPowerService.BASE_IDLE_DRAIN);
        when(service.getAdditionalIdleDrain()).thenReturn(surcharge);
        assertEquals(ordinaryDrain + surcharge, (double) include.invoke(mixin, ordinaryDrain));
        when(service.getAdditionalIdleDrain()).thenReturn(0d);
        assertEquals(ordinaryDrain, (double) include.invoke(mixin, ordinaryDrain));
    }
}
