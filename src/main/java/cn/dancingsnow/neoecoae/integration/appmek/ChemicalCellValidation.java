package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import com.mojang.brigadier.Command;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.registries.MekanismGases;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Runnable development check for the chemical storage matrix type boundary.
 */
public final class ChemicalCellValidation {
    private static final long CHEMICAL_AMOUNT = 1_000L;

    private ChemicalCellValidation() {}

    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("neoecoae_validate_chemical_cells")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            try {
                                String summary = run();
                                context.getSource().sendSuccess(() -> Component.literal(summary), true);
                                return Command.SINGLE_SUCCESS;
                            } catch (IllegalStateException e) {
                                context.getSource().sendFailure(Component.literal(e.getMessage()));
                                return 0;
                            }
                        }));
    }

    public static String run() {
        IECOStorageCell chemicalCell = requireCell(NEAppMekItems.ECO_CHEMICAL_CELL_16M.asStack(), "chemical");
        IECOStorageCell itemCell = requireCell(NEItems.ECO_ITEM_CELL_16M.asStack(), "item");
        IECOStorageCell fluidCell = requireCell(NEItems.ECO_FLUID_CELL_16M.asStack(), "fluid");

        validateInsertMatrix(chemicalCell, itemCell, fluidCell);
        return "NeoECOAE chemical cell validation passed: item keys rejected by chemical cells, "
                + "chemical keys accepted by chemical cells, and item/fluid cells reject chemical keys.";
    }

    public static String runInsertMatrixOnly() {
        IECOStorageCell chemicalCell = requireInventory(NEAppMekItems.ECO_CHEMICAL_CELL_16M.asStack(), "chemical");
        IECOStorageCell itemCell = requireInventory(NEItems.ECO_ITEM_CELL_16M.asStack(), "item");
        IECOStorageCell fluidCell = requireInventory(NEItems.ECO_FLUID_CELL_16M.asStack(), "fluid");

        validateInsertMatrix(chemicalCell, itemCell, fluidCell);
        return "NeoECOAE chemical insert matrix validation passed.";
    }

    private static void validateInsertMatrix(
            IECOStorageCell chemicalCell, IECOStorageCell itemCell, IECOStorageCell fluidCell) {
        AEItemKey itemKey = AEItemKey.of(Items.STONE);
        MekanismKey chemicalKey = MekanismKey.of(new GasStack(MekanismGases.HYDROGEN, CHEMICAL_AMOUNT));
        IActionSource source = IActionSource.empty();

        long itemIntoChemical = chemicalCell.insert(itemKey, 1, Actionable.SIMULATE, source);
        require(itemIntoChemical == 0, "Chemical cell accepted an AE item key: inserted=" + itemIntoChemical);

        long chemicalIntoChemical = chemicalCell.insert(chemicalKey, CHEMICAL_AMOUNT, Actionable.SIMULATE, source);
        require(chemicalIntoChemical > 0, "Chemical cell rejected a valid Mekanism chemical key");

        long chemicalIntoItem = itemCell.insert(chemicalKey, CHEMICAL_AMOUNT, Actionable.SIMULATE, source);
        require(chemicalIntoItem == 0, "Item cell accepted a Mekanism chemical key: inserted=" + chemicalIntoItem);

        long chemicalIntoFluid = fluidCell.insert(chemicalKey, CHEMICAL_AMOUNT, Actionable.SIMULATE, source);
        require(chemicalIntoFluid == 0, "Fluid cell accepted a Mekanism chemical key: inserted=" + chemicalIntoFluid);
    }

    private static IECOStorageCell requireCell(ItemStack stack, String label) {
        require(ECOStorageCells.isCellHandled(stack), "No ECO handler registered for " + label + " cell");
        IECOStorageCell cell = ECOStorageCells.getCellInventory(stack, null);
        require(cell != null, "No ECO cell inventory for " + label + " cell");
        return cell;
    }

    private static IECOStorageCell requireInventory(ItemStack stack, String label) {
        IECOStorageCell cell = ECOStorageCells.getCellInventory(stack, null);
        if (cell == null && stack.getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem) {
            cell = cn.dancingsnow.neoecoae.items.ECOStorageCellItem.getCellInventory(stack, null);
        }
        require(cell != null, "No ECO cell inventory for " + label + " cell");
        return cell;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
