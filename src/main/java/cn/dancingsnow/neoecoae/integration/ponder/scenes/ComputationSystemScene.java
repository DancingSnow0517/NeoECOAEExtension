package cn.dancingsnow.neoecoae.integration.ponder.scenes;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.integration.ponder.instructions.ModifyBlockEntityInstruction;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;

public class ComputationSystemScene {

    public static void creating(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("computation_system_build", "ECO Computation System");
        builder.configureBasePlate(0, 0, 7);
        builder.rotateCameraY(-45);
        builder.showBasePlate();
        builder.idle(20);
        Selection structureSelection = util.select().fromTo(3, 1, 1, 4, 3, 5);
        Selection upperDrive = util.select().position(3, 3, 4);
        Selection lowerDrive = util.select().position(3,1,4);
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

        builder.markAsFinished();
    }
}
