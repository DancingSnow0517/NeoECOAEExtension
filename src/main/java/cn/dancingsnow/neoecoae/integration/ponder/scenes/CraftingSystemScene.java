package cn.dancingsnow.neoecoae.integration.ponder.scenes;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class CraftingSystemScene {

    public static void creating(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("crafting_system_build", "ECO Crafting System");
        builder.configureBasePlate(0, 0, 7);
        builder.rotateCameraY(-45);
        builder.showBasePlate();
        builder.idle(20);
        Selection structureSelection = util.select().fromTo(3, 1, 1, 4, 3, 5);
        BlockPos controllerPos = util.grid().at(3, 2, 2);
        Selection upperParallel = util.select().position(3, 3, 4);
        Selection lowerParallel = util.select().position(3,1,4);
        BlockPos upperParallelPos = util.grid().at(3, 3, 4);
        BlockPos workerPos =  util.grid().at(3, 2, 4);
        BlockPos interfacePos = util.grid().at(4, 2, 1);
        BlockPos inputHatchPos = util.grid().at(4, 3, 1);
        Selection fluidHatches = util.select().position(4, 1, 1).add(util.select().position(inputHatchPos));
        Selection upperBus = util.select().position(4, 3, 4);
        Selection lowerBus = util.select().position(4, 1, 4);
        BlockPos upperBusPos = util.grid().at(4, 3, 4);
        BlockPos ventPos = util.grid().at(4, 2, 4);

        builder.world().showSection(
                structureSelection,
                Direction.EAST
        );

        builder.overlay().showOutlineWithText(structureSelection, 40)
                .text("The ECO Crafting System consists of the following parts.")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(controllerPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(controllerPos), 40)
                .text("the controller...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(controllerPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(upperParallel.add(lowerParallel), 40)
                .text("the parallel cores...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(upperParallelPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(workerPos), 40)
                .text("the worker...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workerPos));
        builder.idle(45);

        builder.rotateCameraY(90 + 45);
        builder.idle(15);

        builder.overlay().showOutlineWithText(util.select().position(interfacePos), 40)
                .text("the interface...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(interfacePos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(fluidHatches, 40)
                .text("the fluid hatches...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(inputHatchPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(upperBus.add(lowerBus), 40)
                .text("the pattern buses...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(upperBusPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(ventPos), 40)
                .text("the vent...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(ventPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(structureSelection, 40)
                .text("and remaining crafting casings...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(controllerPos.above()));
        builder.idle(45);

        builder.markAsFinished();
    }
}
