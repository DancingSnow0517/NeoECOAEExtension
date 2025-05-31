package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.MachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.MachineEnergyCellBlockEntity;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NEStorageCluster extends NECluster<NEStorageCluster> {

    @Getter
    private ECOStorageSystemBlockEntity controller = null;
    private final List<ECODriveBlockEntity> drives = new ArrayList<>();
    private final List<MachineEnergyCellBlockEntity> energyCells = new ArrayList<>();
    private MachineInterfaceBlockEntity<NEStorageCluster> theInterface = null;
    private final List<MachineCasingBlockEntity<NEStorageCluster>> casings = new ArrayList<>();

    public NEStorageCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEStorageCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECODriveBlockEntity driveBlockEntity) {
            drives.add(driveBlockEntity);
        }
        if (blockEntity instanceof MachineEnergyCellBlockEntity energyCellBlockEntity) {
            energyCells.add(energyCellBlockEntity);
        }
        if (blockEntity instanceof MachineInterfaceBlockEntity) {
            //noinspection unchecked
            theInterface = (MachineInterfaceBlockEntity<NEStorageCluster>) blockEntity;
        }
        if (blockEntity instanceof ECOStorageSystemBlockEntity systemBlockEntity) {
            controller = systemBlockEntity;
        }
        //noinspection rawtypes
        if (blockEntity instanceof MachineCasingBlockEntity casing) {
            //noinspection unchecked
            casings.add(casing);
        }
    }

    @Override
    public boolean shouldCasingHide(NEBlockEntity<NEStorageCluster, ?> blockEntity) {
        if (blockEntity instanceof MachineCasingBlockEntity) {
            Vec3 casingPos = blockEntity.getBlockPos().getCenter();
            Vec3 controllerPos = controller.getBlockPos().getCenter();
            return casingPos.distanceToSqr(controllerPos) <= 3;
        }
        return false;
    }
}
