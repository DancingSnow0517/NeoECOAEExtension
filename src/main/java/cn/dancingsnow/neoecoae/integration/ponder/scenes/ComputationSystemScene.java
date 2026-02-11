package cn.dancingsnow.neoecoae.integration.ponder.scenes;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.integration.ponder.instructions.ModifyBlockEntityInstruction;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class ComputationSystemScene {

    public static void creating(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("computation_system_build", "ECO Computation System");
        builder.configureBasePlate(0, 0, 7);
        builder.rotateCameraY(-45);
        builder.showBasePlate();
        builder.idle(20);
        Selection structureSelection = util.select().fromTo(3, 1, 1, 4, 3, 5);
        BlockPos controllerPos = util.grid().at(3, 2, 2);
        Selection upperDrive = util.select().position(3, 3, 4);
        Selection lowerDrive = util.select().position(3,1,4);
        BlockPos upperDrivePos = util.grid().at(3, 3, 4);
        BlockPos transmitterPos =  util.grid().at(3, 2, 4);
        BlockPos coolerPos = util.grid().at(3, 2, 5);
        BlockPos interfacePos = util.grid().at(4, 2, 1);
        Selection upperParallel = util.select().position(4, 3, 4);
        Selection lowerParallel = util.select().position(4, 1, 4);
        BlockPos upperParallelPos = util.grid().at(4, 3, 4);
        BlockPos threadingPos = util.grid().at(4, 2, 4);

        builder.addInstruction(ModifyBlockEntityInstruction.<ECOComputationDriveBlockEntity>of(
            upperDrive,
            t -> {
                t.setFormed(true);
                t.setTier(ECOTier.L9);
                t.setLowerDrive(false);
            }
        ));

        builder.addInstruction(ModifyBlockEntityInstruction.<ECOComputationDriveBlockEntity>of(
            lowerDrive,
            t -> {
                t.setFormed(true);
                t.setTier(ECOTier.L9);
                t.setLowerDrive(true);
            }
        ));

        builder.world().showSection(
            structureSelection,
            Direction.EAST
        );

        builder.overlay().showOutlineWithText(structureSelection, 40)
                .text("The ECO Computation System consists of the following parts.")
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

        builder.overlay().showOutlineWithText(upperDrive.add(lowerDrive), 40)
                .text("the drives...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(upperDrivePos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(transmitterPos), 40)
                .text("the transmitter...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(transmitterPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(coolerPos), 40)
                .text("the cooling controller...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(coolerPos));
        builder.idle(45);

        builder.rotateCameraY(90 + 45);
        builder.idle(15);

        builder.overlay().showOutlineWithText(util.select().position(interfacePos), 40)
                .text("the interface...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(interfacePos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(upperParallel.add(lowerParallel), 40)
                .text("the parallel cores...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(upperParallelPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(util.select().position(threadingPos), 40)
                .text("the threading core...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(threadingPos));
        builder.idle(45);

        builder.overlay().showOutlineWithText(structureSelection, 40)
                .text("and remaining computation casings...")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(controllerPos.above()));
        builder.idle(45);

        builder.markAsFinished();
    }
}
