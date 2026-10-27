package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Real GT single-block machine, native EU input and output extraction. */
final class GtProcessor {
    final Object machine;
    final IItemTransfer imports;
    final IItemTransfer exports;
    final IFluidTransfer fluidsOut;
    final PatternProviderBlockEntity provider;
    final GtlFixture recipe;
    final NeoMachineRig rig;
    final BlockPos position;
    final int circuit;
    long suppliedEU;
    long produced;
    boolean toolsInstalled;

    GtProcessor(NeoMachineRig rig, GtlFixture recipe, BlockPos pos, String type, boolean withTools) throws Exception {
        this(rig, recipe, pos, type, withTools, 0);
    }

    GtProcessor(NeoMachineRig rig, GtlFixture recipe, BlockPos pos, String type, boolean withTools, int recipeIndex) throws Exception {
        this.rig = rig;
        this.recipe = recipe;
        position = pos;
        circuit = recipe.recipes.get(recipeIndex).getAsJsonObject().get("circuit").getAsInt();
        var id = new ResourceLocation("gtceu", "hv_" + type);
        NeoMachineRig.check(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id), "Missing real GT machine " + id);
        rig.place(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id));
        machine = Class.forName("com.gregtechceu.gtceu.api.machine.MetaMachine")
                .getMethod("getMachine", net.minecraft.world.level.BlockGetter.class, BlockPos.class).invoke(null, rig.level, pos);
        NeoMachineRig.check(machine != null, "GT machine block entity did not initialize");
        machine.getClass().getMethod("setFrontFacing", Direction.class).invoke(machine, Direction.NORTH);
        imports = (IItemTransfer) machine.getClass().getField("importItems").get(machine);
        exports = (IItemTransfer) machine.getClass().getField("exportItems").get(machine);
        fluidsOut = (IFluidTransfer) machine.getClass().getField("exportFluids").get(machine);
        rig.place(pos.west(), AEBlocks.PATTERN_PROVIDER.block());
        provider = (PatternProviderBlockEntity) rig.level.getBlockEntity(pos.west());
        provider.getLogic().getPatternInv().setItemDirect(0, recipe.patterns.get(recipeIndex).getDefinition().toStack());
        provider.getLogic().updatePatterns();
        if (withTools) installTools();
        if (circuit > 0) {
            var inventory = (IItemTransfer) machine.getClass().getMethod("getCircuitInventory").invoke(machine);
            var configured = (ItemStack) Class.forName("com.gregtechceu.gtceu.common.item.IntCircuitBehaviour").getMethod("stack", int.class).invoke(null, circuit);
            inventory.setStackInSlot(0, configured);
        }
    }

    void installTools() {
        for (var options : recipe.reusableInputs) {
            NeoMachineRig.check(!options.isEmpty() && options.get(0) instanceof AEItemKey, "Unsupported reusable GT tool");
            ItemStack left = ((AEItemKey) options.get(0)).toStack();
            if (configurationTool(left)) continue;
            for (int slot = 0; slot < imports.getSlots() && !left.isEmpty(); slot++) left = imports.insertItem(slot, left, false);
            NeoMachineRig.check(left.isEmpty(), "GT tool inventory is full");
        }
        toolsInstalled = true;
    }

    void tick() throws Exception {
        rig.connect(provider.getMainNode());
        Object energy = machine.getClass().getField("energyContainer").get(machine);
        long capacity = ((Number) energy.getClass().getMethod("getEnergyCapacity").invoke(energy)).longValue();
        suppliedEU = Math.addExact(suppliedEU, ((Number) energy.getClass().getMethod("changeEnergy", long.class).invoke(energy, capacity)).longValue());
        for (int slot = 0; slot < exports.getSlots(); slot++) {
            var actual = exports.getStackInSlot(slot);
            if (actual.isEmpty()) continue;
            AEKey key = AEItemKey.of(actual);
            long accepted = rig.inventory().insert(key, actual.getCount(), Actionable.MODULATE, IActionSource.empty());
            NeoMachineRig.check(exports.extractItem(slot, Math.toIntExact(accepted), false).getCount() == accepted, "GT output transport lost custody");
            if (key.equals(recipe.target)) produced = Math.addExact(produced, accepted);
        }
        for (int tank = 0; tank < fluidsOut.getTanks(); tank++) {
            var actual = fluidsOut.getFluidInTank(tank);
            if (actual.isEmpty()) continue;
            AEKey key = AEFluidKey.of(actual.getFluid(), actual.getTag());
            long accepted = rig.inventory().insert(key, actual.getAmount(), Actionable.MODULATE, IActionSource.empty());
            var taken = fluidsOut.drain(tank, com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(actual.getFluid(), accepted, actual.getTag()), false, true);
            NeoMachineRig.check(taken.getAmount() == accepted, "GT fluid output transport lost custody");
            if (key.equals(recipe.target)) produced = Math.addExact(produced, accepted);
        }
    }

    private boolean configurationTool(ItemStack stack) {
        return circuit > 0 && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                .toString().equals("gtceu:programmed_circuit");
    }

    void verifyTools() throws Exception {
        if (!toolsInstalled) return;
        for (var options : recipe.reusableInputs) {
            if (configurationTool(((AEItemKey) options.get(0)).toStack())) continue;
            long remaining = 0;
            for (int slot = 0; slot < imports.getSlots(); slot++) {
                var stack = imports.getStackInSlot(slot);
                if (!stack.isEmpty() && options.contains(AEItemKey.of(stack))) remaining += stack.getCount();
            }
            NeoMachineRig.check(remaining == 1, "Reusable GT tool was consumed or duplicated: " + options);
        }
        if (circuit > 0) {
            var inventory = (IItemTransfer) machine.getClass().getMethod("getCircuitInventory").invoke(machine);
            var installed = inventory.getStackInSlot(0);
            NeoMachineRig.check(installed.getCount() == 1 && installed.hasTag()
                    && installed.getTag().getInt("Configuration") == circuit,
                    "GT machine consumed or changed its programmed circuit");
        }
    }
}
