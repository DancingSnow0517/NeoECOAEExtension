package cn.dancingsnow.neoecoae.integration.ponder.scenes;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.integration.ponder.instructions.PlaceCableBusInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class StorageSystemScene {

    public static void creating(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("storage_system_build", "ECO Storage System");
        builder.configureBasePlate(0, 0, 7);
        builder.rotateCameraY(-45);
        builder.showBasePlate();
        builder.idle(20);
        Selection structureSelection = util.select().fromTo(4, 1, 1, 5, 3, 4);
        BlockPos controllerPos = util.grid().at(4, 2, 2);
        BlockPos drivePos = util.grid().at(4, 2, 3);
        BlockPos interfacePos = util.grid().at(5, 2, 1);
        BlockPos cellPos = util.grid().at(5, 3, 3);
        BlockPos ventPos = util.grid().at(5, 2, 3);
        Selection driveSelection = util.select().fromTo(4, 1, 3, 4, 3, 3);

        Selection energyCellSelection = util.select().position(5, 1, 3).add(util.select().position(5, 3, 3));
        builder.world().showSection(
            structureSelection,
            Direction.EAST
        );

        builder.overlay().showOutlineWithText(structureSelection, 40)
            .text("The ECO Storage System consists of the following parts.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(controllerPos), 40)
            .text("the controller")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(driveSelection, 40)
            .text("the drives...")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(drivePos));
        builder.idle(45);

        builder.rotateCameraY(90 + 45);
        builder.idle(15);

        builder.overlay().showOutlineWithText(util.select().position(interfacePos), 40)
            .text("the interface...")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(interfacePos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(energyCellSelection, 40)
            .text("the energy cells...")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(cellPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(ventPos), 40)
            .text("the vent...")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(ventPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(structureSelection, 40)
            .text("and remaining storage casings...")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos.above()));
        builder.idle(45);

//        builder.overlay().showOutlineWithText(util.select().position(interfacePos), 40)
//            .text("The Storage Interface enables ECO Storage System to communicate with ME Network")
//            .attachKeyFrame()
//            .placeNearTarget()
//            .pointAt(util.vector().topOf(interfacePos));
//        builder.idle(45);
//


        builder.rotateCameraY(360 - 60 - 45);
        builder.idle(15);


        builder.markAsFinished();
    }

    public static void energy(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("storage_system_energy", "Store energy using ECO Energy Cells");
        builder.configureBasePlate(0, 0, 7);
        builder.rotateCameraY(90);
        builder.showBasePlate();
        builder.idle(20);
        Selection structureSelection = util.select().fromTo(2, 1, 1, 3, 3, 4);
        Selection cableSelection1 = util.select().position(4, 1, 1);
        Selection cableSelection2 = util.select().position(4, 2, 1);
        Selection networkSelection = util.select().fromTo(5, 1, 1, 5, 1, 3)
            .add(cableSelection1)
            .add(cableSelection2);
        Selection acceptorSelection = util.select().position(5, 1, 3);
        builder.world().showSection(structureSelection, Direction.DOWN);
        Selection cells = util.select().position(3, 1, 3)
            .add(util.select().position(3, 3, 3));
        builder.overlay().showOutlineWithText(cells, 40)
            .text("The energy cells stores energy for the ECO Storage System structure.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(3, 3, 3));

        builder.idle(45);

        builder.world().showSection(networkSelection, Direction.NORTH);

        builder.addInstruction(PlaceCableBusInstruction.builder(cableSelection1)
            .cable(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT))
            .cableChannels(1)
            .cableConnect(Direction.EAST, Direction.UP)
            .powered(true)
            .applyCableState()
            .build()
        );
        builder.addInstruction(PlaceCableBusInstruction.builder(cableSelection2)
            .cable(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT))
            .cableChannels(1)
            .cableConnect(Direction.WEST, Direction.DOWN)
            .powered(true)
            .applyCableState()
            .build()
        );

        builder.idle(5);
        builder.overlay().showOutlineWithText(networkSelection, 40)
            .text("If you connect the Storage System to a ME Network...")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(5, 1, 1));

        builder.idle(45);

        builder.overlay().showOutlineWithText(acceptorSelection, 40)
            .text("Then provide energy generation")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(5, 1, 3));

        builder.idle(45);

        builder.overlay().showOutlineWithText(cells, 40)
            .text("The cells would charge up")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(3, 1, 3));

        builder.idle(10);
        builder.world().setBlocks(cells, getEnergyCell(1), false);
        builder.idle(10);
        builder.world().setBlocks(cells, getEnergyCell(2), false);
        builder.idle(10);
        builder.world().setBlocks(cells, getEnergyCell(3), false);
        builder.idle(10);
        builder.world().setBlocks(cells, getEnergyCell(4), false);
        builder.idle(5);

        builder.markAsFinished();
    }

    private static BlockState getEnergyCell(int level) {
        return NEBlocks.ENERGY_CELL_L4.getDefaultState()
            .setValue(ECOEnergyCellBlock.LEVEL, level)
            .setValue(ECOEnergyCellBlock.FACING, Direction.EAST);
    }

    public static void interface_(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("storage_system_interface", "Store");
        builder.configureBasePlate(0, 0, 7);
        //builder.rotateCameraY(-90);
        builder.showBasePlate();
        builder.idle(20);

        builder.markAsFinished();
    }

    public static void drive(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("storage_system_drive", "ECO Drive");
        builder.configureBasePlate(0, 0, 3);
        builder.showBasePlate();

        builder.idleSeconds(1);
        Selection position = util.select().position(1, 1, 1);
        builder.world().showSection(position, Direction.DOWN);

        builder.idleSeconds(1);
        ItemStack cellStack = NEItems.ECO_ITEM_CELL_16M.asStack();
        builder.overlay().showOutlineWithText(position, 60)
            .text("Right click with ECO cell item to put it on drives")
            .attachKeyFrame()
            .placeNearTarget()
            .colored(PonderPalette.WHITE);
        builder.overlay().showControls(util.vector().topOf(1, 1, 1), Pointing.DOWN, 60)
            .rightClick()
            .withItem(cellStack);
        builder.world().modifyBlockEntityNBT(position, ECODriveBlockEntity.class, tag -> {
            CompoundTag stackTag = new CompoundTag();
            stackTag.putInt("count", 1);
            stackTag.putString("id", "neoecoae:eco_item_storage_cell_16m");
            tag.put("cellStack", stackTag);
        }, true);
        builder.idleSeconds(4);

        builder.overlay().showOutlineWithText(position, 60)
            .text("Snaking Right click with empty hand to tack ECO Cell Item from drives")
            .attachKeyFrame()
            .placeNearTarget()
            .colored(PonderPalette.WHITE);
        builder.overlay().showControls(util.vector().topOf(1, 1, 1), Pointing.DOWN, 60)
            .rightClick()
            .whileSneaking();
        builder.world().setBlock(new BlockPos(1, 1, 1), NEBlocks.ENERGY_CELL_L4.getDefaultState(), false);
        builder.world().setBlock(new BlockPos(1, 1, 1), NEBlocks.ECO_DRIVE.getDefaultState(), false);
        builder.idleSeconds(4);

        builder.markAsFinished();
    }
}
