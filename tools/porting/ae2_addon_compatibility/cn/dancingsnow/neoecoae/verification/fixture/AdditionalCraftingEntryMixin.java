package cn.dancingsnow.neoecoae.verification.fixture;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** An independent addon entry whose name is unknown to the production planning mixin. */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class AdditionalCraftingEntryMixin {
    @Unique
    public Future<ICraftingPlan> independentAddonCalculation(ICraftingService service, Level level,
            ICraftingSimulationRequester requester, AEKey goal, long amount, CalculationStrategy strategy) {
        return service.beginCraftingCalculation(level, requester, goal, amount, strategy);
    }
}
