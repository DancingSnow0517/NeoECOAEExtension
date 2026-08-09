package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NEStorageCluster extends NECluster<NEStorageCluster> {

    @Getter
    private ECOStorageSystemBlockEntity controller = null;
    @Getter
    private final List<ECODriveBlockEntity> drives = new ArrayList<>();
    @Getter
    private final List<ECOEnergyCellBlockEntity> energyCells = new ArrayList<>();
    private ECOMachineInterfaceBlockEntity<NEStorageCluster> theInterface = null;

    public ECOMachineInterfaceBlockEntity<NEStorageCluster> getTheInterface() {
        return theInterface;
    }
    private final List<ECOMachineCasingBlockEntity<NEStorageCluster>> casings = new ArrayList<>();

    public NEStorageCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEStorageCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECODriveBlockEntity driveBlockEntity) {
            drives.add(driveBlockEntity);
        }
        if (blockEntity instanceof ECOEnergyCellBlockEntity energyCellBlockEntity) {
            energyCells.add(energyCellBlockEntity);
        }
        if (blockEntity instanceof ECOMachineInterfaceBlockEntity) {
            //noinspection unchecked
            theInterface = (ECOMachineInterfaceBlockEntity<NEStorageCluster>) blockEntity;
        }
        if (blockEntity instanceof ECOStorageSystemBlockEntity systemBlockEntity) {
            controller = systemBlockEntity;
        }
        //noinspection rawtypes
        if (blockEntity instanceof ECOMachineCasingBlockEntity casing) {
            //noinspection unchecked
            casings.add(casing);
        }
    }

    @Override
    public boolean shouldCasingHide(NEBlockEntity<NEStorageCluster, ?> blockEntity) {
        if (blockEntity instanceof ECOMachineCasingBlockEntity) {
            Vec3 casingPos = blockEntity.getBlockPos().getCenter();
            Vec3 controllerPos = controller.getBlockPos().getCenter();
            return casingPos.distanceToSqr(controllerPos) <= 3;
        }
        return false;
    }

    @Override
    public boolean shouldCasingRenderInClassic(NEBlockEntity<NEStorageCluster, ?> blockEntity) {
        if (!(blockEntity instanceof ECOMachineCasingBlockEntity) || controller == null || !shouldCasingHide(blockEntity)) {
            return false;
        }

        BlockPos offset = blockEntity.getBlockPos().subtract(controller.getBlockPos());
        Direction facing = controller.getBlockState().getValue(ECOStorageSystemBlock.FACING);
        int localX = switch (facing) {
            case NORTH -> offset.getX();
            case EAST -> offset.getZ();
            case SOUTH -> -offset.getX();
            case WEST -> -offset.getZ();
            default -> throw new IllegalStateException("Storage controller must face horizontally");
        };
        int localZ = switch (facing) {
            case NORTH -> offset.getZ();
            case EAST -> -offset.getX();
            case SOUTH -> -offset.getZ();
            case WEST -> offset.getX();
            default -> throw new IllegalStateException("Storage controller must face horizontally");
        };

        // The formed Classic controller occupies local x/z [0, 1] and y [-1, 1].
        return !(localX >= 0 && localX <= 1
            && localZ >= 0 && localZ <= 1
            && offset.getY() >= -1 && offset.getY() <= 1);
    }
}
