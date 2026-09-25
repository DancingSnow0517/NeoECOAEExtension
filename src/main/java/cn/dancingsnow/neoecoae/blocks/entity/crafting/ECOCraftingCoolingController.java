package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.ticking.TickRateModulation;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Handles local coolant storage, recipe evaluation and coolant consumption for a crafting host. */
final class ECOCraftingCoolingController {
    private static final int MAX_COOLANT = ECOCraftingSystemBlockEntity.MAX_COOLANT;
    private static final int VIRTUAL_COOLANT_PER_LANE_TICK = 10_000;
    private static final int MAX_OVERCLOCK_TIMES = ECOCraftingSystemBlockEntity.MAX_OVERCLOCK_TIMES;

    private final ECOCraftingSystemBlockEntity host;

    ECOCraftingCoolingController(ECOCraftingSystemBlockEntity host) {
        this.host = host;
    }

    TickRateModulation tick() {
        // Stock the internal buffer even while idle, without FX cores, or with cooling disabled.
        if (host.getCoolant() >= MAX_COOLANT) {
            return TickRateModulation.IDLE;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        if (recipe == null || !canRefillWith(recipe.maxOverclock())) {
            return TickRateModulation.IDLE;
        }

        int targetCoolant = MAX_COOLANT;
        if (targetCoolant <= host.getCoolant()) {
            return TickRateModulation.IDLE;
        }
        int refillAmount = refillCoolant(recipe, targetCoolant - host.getCoolant());
        if (refillAmount <= 0) {
            return TickRateModulation.IDLE;
        }
        return host.getCoolant() < targetCoolant ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    boolean tryConsumeLocalCoolant(int amount, int requiredOverclock) {
        if (amount <= 0) {
            return true;
        }
        ensureCoolantAvailable(amount, requiredOverclock);
        int coolant = host.getCoolant();
        if (coolant < amount || requiredOverclock > 0 && host.getCoolantMaxOverclock() < requiredOverclock) {
            return false;
        }
        int remaining = coolant - amount;
        host.updateLocalCoolantState(
            remaining,
            remaining == 0 ? -1 : host.getCoolantMaxOverclock(),
            remaining == 0 ? FluidStack.EMPTY : host.getCurrentCoolantFluid()
        );
        return true;
    }

    int getLocalAvailableCoolant(int requested, int requiredOverclock) {
        if (requested <= 0 || !ensureCoolantAvailable(requested, requiredOverclock)) {
            return 0;
        }
        if (requiredOverclock > 0 && host.getCoolantMaxOverclock() < requiredOverclock) {
            return 0;
        }
        return Math.min(requested, host.getCoolant());
    }

    boolean tryConsumeVirtualLaneCoolant() {
        return !host.isActiveCooling() || host.tryConsumeCoolant(
            VIRTUAL_COOLANT_PER_LANE_TICK, MAX_OVERCLOCK_TIMES);
    }

    int getLocalCraftingCoolantCraftLimit(int coolantPerCraft, int requiredOverclock, int requestedCrafts) {
        if (!host.isLocallyActiveCooling() || requestedCrafts <= 0) {
            return Math.max(0, requestedCrafts);
        }
        if (host.usesTickBasedCoolant()) {
            return ensureCoolantAvailable(1, requiredOverclock) ? requestedCrafts : 0;
        }
        if (coolantPerCraft <= 0) {
            return Math.max(0, requestedCrafts);
        }
        int desiredCoolant = (int) Math.min(MAX_COOLANT, (long) coolantPerCraft * requestedCrafts);
        ensureCoolantAvailable(desiredCoolant, requiredOverclock);
        if (requiredOverclock > 0 && host.getCoolantMaxOverclock() < requiredOverclock) {
            return 0;
        }
        return Math.min(requestedCrafts, host.getCoolant() / coolantPerCraft);
    }

    int getCurrentCoolingMaxOverclock() {
        if (host.getCoolant() > 0 && host.getCoolantMaxOverclock() >= 0) {
            return host.getCoolantMaxOverclock();
        }
        CoolingRecipe recipe = getCoolingRecipe();
        return recipe == null ? -1 : recipe.maxOverclock();
    }

    @Nullable
    CoolingRecipe getCoolingRecipe() {
        if (host.getCluster() == null
            || host.getCluster().getInputHatch() == null
            || host.getCluster().getOutputHatch() == null
            || host.getLevel() == null) {
            return null;
        }
        FluidTank inputHatch = host.getCluster().getInputHatch().tank;
        if (inputHatch.getFluidAmount() <= 0) {
            return null;
        }
        FluidTank outputHatch = host.getCluster().getOutputHatch().tank;
        return host.getLevel().getRecipeManager().getRecipeFor(
            NERecipeTypes.COOLING.get(),
            new CoolingRecipe.Input(inputHatch.getFluid(), outputHatch.getFluid()),
            host.getLevel()
        ).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
    }

    private boolean canRefillWith(int maxOverclock) {
        return host.getCoolant() <= 0
            || host.getCoolantMaxOverclock() == maxOverclock
                && host.getCluster() != null
                && host.getCluster().getInputHatch() != null
                && FluidStack.isSameFluidSameComponents(host.getCurrentCoolantFluid(),
                    host.getCluster().getInputHatch().tank.getFluid());
    }

    private boolean ensureCoolantAvailable(int requiredCoolant, int requiredOverclock) {
        if (!host.isLocallyActiveCooling() || requiredCoolant <= 0) {
            return true;
        }
        if (host.getCoolant() >= requiredCoolant
            && (requiredOverclock <= 0 || host.getCoolantMaxOverclock() >= requiredOverclock)) {
            return true;
        }
        CoolingRecipe recipe = getCoolingRecipe();
        if (recipe == null || !canRefillWith(recipe.maxOverclock())
            || requiredOverclock > 0 && recipe.maxOverclock() < requiredOverclock) {
            return false;
        }
        int targetCoolant = Math.min(MAX_COOLANT, Math.max(requiredCoolant, host.getCoolant()));
        refillCoolant(recipe, targetCoolant - host.getCoolant());
        return host.getCoolant() >= requiredCoolant
            && (requiredOverclock <= 0 || host.getCoolantMaxOverclock() >= requiredOverclock);
    }

    private int refillCoolant(CoolingRecipe recipe, int deficit) {
        if (host.getCluster() == null
            || host.getCluster().getInputHatch() == null
            || host.getCluster().getOutputHatch() == null) {
            return 0;
        }
        FluidTank inputHatch = host.getCluster().getInputHatch().tank;
        FluidTank outputHatch = host.getCluster().getOutputHatch().tank;
        int inputAmount = recipe.inputAmount();
        if (deficit <= 0 || inputAmount <= 0 || recipe.coolant() <= 0) {
            return 0;
        }

        long requiredInput = ((long) deficit * inputAmount + recipe.coolant() - 1L) / recipe.coolant();
        long drainAmount = Math.min(requiredInput, inputHatch.getFluidAmount());
        drainAmount = Math.min(drainAmount, getMaxDrainByOutput(recipe, outputHatch));
        if (drainAmount <= 0) {
            return 0;
        }

        FluidStack coolantFluid = inputHatch.getFluid().copyWithAmount(1);
        int drained = inputHatch.drain((int) drainAmount, IFluidHandler.FluidAction.EXECUTE).getAmount();
        if (drained <= 0) {
            return 0;
        }

        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            int outputAmount = (int) ((long) drained * recipe.outputAmount() / inputAmount);
            if (outputAmount > 0) {
                outputHatch.fill(output.copyWithAmount(outputAmount), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        int coolantGain = (int) ((long) drained * recipe.coolant() / inputAmount);
        if (coolantGain <= 0) {
            return 0;
        }
        host.updateLocalCoolantState(
            Math.min(MAX_COOLANT, host.getCoolant() + coolantGain),
            recipe.maxOverclock(),
            coolantFluid
        );
        return coolantGain;
    }

    private long getMaxDrainByOutput(CoolingRecipe recipe, FluidTank outputHatch) {
        FluidStack output = recipe.output();
        if (output.isEmpty()) {
            return Long.MAX_VALUE;
        }
        FluidStack stored = outputHatch.getFluid();
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, output)) {
            return 0;
        }
        int outputAmount = recipe.outputAmount();
        if (outputAmount <= 0) {
            return Long.MAX_VALUE;
        }
        long outputSpace = outputHatch.getCapacity() - outputHatch.getFluidAmount();
        return outputSpace * recipe.inputAmount() / outputAmount;
    }
}
