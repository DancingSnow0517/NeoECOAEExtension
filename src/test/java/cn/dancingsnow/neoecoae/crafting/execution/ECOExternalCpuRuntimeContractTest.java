package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the actual selected dependency jars; this does not replace an in-game Mixin smoke test. */
class ECOExternalCpuRuntimeContractTest {
    @ParameterizedTest @ValueSource(strings = {"appeng.crafting.execution", "net.pedroksl.advanced_ae.common.logic"})
    void injectionAndAccessorTargetsExistInSelectedDependencies(String prefix) throws Exception {
        var logic = Class.forName(prefix + (prefix.startsWith("appeng") ? ".CraftingCpuLogic" : ".AdvCraftingCPULogic"));
        assertEquals(int.class, logic.getDeclaredMethod("executeCrafting", int.class, CraftingService.class,
                IEnergyService.class, Level.class).getReturnType());
        assertEquals(ICraftingSubmitResult.class, logic.getDeclaredMethod("trySubmitJob", IGrid.class,
                ICraftingPlan.class, IActionSource.class, ICraftingRequester.class).getReturnType());
        assertEquals(void.class, logic.getDeclaredMethod("tickCraftingLogic", IEnergyService.class,
                CraftingService.class).getReturnType());
        assertEquals(void.class, logic.getDeclaredMethod("finishJob", boolean.class).getReturnType());
        for (var name : new String[]{"readFromNBT", "writeToNBT"}) {
            assertEquals(void.class, logic.getDeclaredMethod(name, CompoundTag.class,
                    HolderLookup.Provider.class).getReturnType());
        }
        var job = Class.forName(prefix + ".ExecutingCraftingJob");
        assertEquals(job, logic.getDeclaredField("job").getType());
        assertEquals(ListCraftingInventory.class, logic.getDeclaredField("inventory").getType());
        assertEquals(Map.class, job.getDeclaredField("tasks").getType());
        assertEquals(ListCraftingInventory.class, job.getDeclaredField("waitingFor").getType());
        assertEquals(CraftingLink.class, job.getDeclaredField("link").getType());
        assertEquals(GenericStack.class, job.getDeclaredField("finalOutput").getType());
        assertEquals(boolean.class, job.getDeclaredField("suspended").getType());
        assertEquals(long.class, Class.forName(prefix + ".ExecutingCraftingJob$TaskProgress")
                .getDeclaredField("value").getType());
        assertEquals(void.class, job.getDeclaredField("timeTracker").getType()
                .getDeclaredMethod("addMaxItems", long.class, AEKeyType.class).getReturnType());
    }
}
