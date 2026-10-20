package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimization;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationRegistry;
import net.minecraft.resources.ResourceLocation;

/** AE2-specific guarantees live here rather than in the generic execution contract. */
public final class AE2PatternOptimizationIntegration {
    private static boolean initialized;

    private AE2PatternOptimizationIntegration() {}

    public static synchronized void init() {
        if (initialized) return;
        ECOExecutionOptimizationRegistry.register(
            new ResourceLocation("neoecoae", "ae2_assembler"), 0,
            new ECOExecutionOptimizationAdapter() {
                @Override
                public boolean supports(IPatternDetails pattern) {
                    return pattern instanceof AECraftingPattern
                        || pattern instanceof AESmithingTablePattern
                        || pattern instanceof AEStonecuttingPattern;
                }

                @Override
                public ECOExecutionOptimization describe(IPatternDetails pattern) {
                    return ECOExecutionOptimization.VERIFIED_ASSEMBLER;
                }
            });
        initialized = true;
    }
}
