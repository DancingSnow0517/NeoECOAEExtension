package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOAdvancedAeCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.ECOJobOutputReceiver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class ECOExternalCpuMixinGameTest {
    @GameTest(template = "empty")
    public static void optionalCpuMixinsApplyOnlyWithTheirDependency(GameTestHelper helper) throws Exception {
        helper.assertTrue(
                ECOJobOutputReceiver.class.isAssignableFrom(CraftingCpuLogic.class),
                "native AE2 CPU output receiver mixin was not applied");
        boolean advancedAePresent = ModList.get().isLoaded("advanced_ae");
        helper.assertTrue(
                ECOAdvancedAeCraftingOutputRouter.class.isAssignableFrom(CraftingService.class) == advancedAePresent,
                "AdvancedAE crafting service mixin does not match the installed mod set");
        if (advancedAePresent) {
            Class<?> logic = Class.forName("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic");
            helper.assertTrue(
                    ECOJobOutputReceiver.class.isAssignableFrom(logic),
                    "AdvancedAE CPU output receiver mixin was not applied");
        }
        helper.succeed();
    }
}
