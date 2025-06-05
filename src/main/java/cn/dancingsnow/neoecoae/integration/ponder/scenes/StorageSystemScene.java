package cn.dancingsnow.neoecoae.integration.ponder.scenes;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;

public class StorageSystemScene {

    public static void creating(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("storage_system_build", "Build a ECO Storage System");
        builder.configureBasePlate(0, 0, 5);
        builder.showBasePlate();
        builder.markAsFinished();
    }

    public static void energy(SceneBuilder builder, SceneBuildingUtil util) {
        builder.title("storage_system_energy", "Store");
        builder.configureBasePlate(0, 0, 5);
        builder.showBasePlate();
        builder.markAsFinished();
    }
}
