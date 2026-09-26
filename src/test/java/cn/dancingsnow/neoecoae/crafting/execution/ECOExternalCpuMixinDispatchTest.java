package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting.AdvancedAeCraftingCpuLogicMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exercises both injection handlers, including the optional-engine selection before dispatch. */
class ECOExternalCpuMixinDispatchTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({
            "false, false, false", "false, true, false", "false, false, true", "false, true, true",
            "true, false, false", "true, true, false", "true, false, true", "true, true, true"
    })
    void thunderboltFallbackUsesEcoButOmniSequenceKeepsItsOwnAccounting(
            boolean advanced, boolean thunderbolt, boolean omniSequence) throws Exception {
        Class<?> type = advanced ? AdvancedAeCraftingCpuLogicMixin.class : Ae2CraftingCpuFastPathMixin.class;
        Object mixin = mock(type, CALLS_REAL_METHODS);
        var dispatcher = mock(ECOExternalCpuFastPath.class);
        var field = type.getDeclaredField(advanced ? "neoecoae$batchDispatcher" : "neoecoae$fastPath");
        field.setAccessible(true);
        field.set(mixin, dispatcher);
        var crafting = mock(CraftingService.class);
        var energy = mock(IEnergyService.class);
        var level = mock(Level.class);
        when(dispatcher.execute(mixin, null, null, 1, crafting, energy, level)).thenReturn(1);
        var mods = mock(ModList.class);
        when(mods.isLoaded("thunderbolt")).thenReturn(thunderbolt);
        when(mods.isLoaded("molecularmanipulator")).thenReturn(omniSequence);
        var callback = new CallbackInfoReturnable<Integer>("executeCrafting", true, 0);
        var handler = type.getDeclaredMethod(advanced ? "neoecoae$tryFastPath" : "neoecoae$dispatch",
                int.class, CraftingService.class, IEnergyService.class, Level.class, CallbackInfoReturnable.class);
        handler.setAccessible(true);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            handler.invoke(mixin, 1, crafting, energy, level, callback);
        }
        if (omniSequence) {
            verifyNoInteractions(dispatcher);
            assertFalse(callback.isCancelled());
        } else {
            verify(dispatcher).execute(mixin, null, null, 1, crafting, energy, level);
            assertTrue(callback.isCancelled());
            assertEquals(1, callback.getReturnValue());
        }
    }
}
