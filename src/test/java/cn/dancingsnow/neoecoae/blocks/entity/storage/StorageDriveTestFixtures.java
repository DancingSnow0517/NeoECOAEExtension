package cn.dancingsnow.neoecoae.blocks.entity.storage;

import net.minecraft.nbt.CompoundTag;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

final class StorageDriveTestFixtures {
    private StorageDriveTestFixtures() {}

    static ECODriveBlockEntity drive() {
        var drive = mock(ECODriveBlockEntity.class, CALLS_REAL_METHODS);
        // Mockito skips constructors and field initializers, including the persisted NBT containers.
        for (String name : new String[] {"legacy2042RestoreData", "unresolvedCellStack"}) {
            try {
                var field = ECODriveBlockEntity.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(drive, new CompoundTag());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot initialize storage drive fixture: " + name, failure);
            }
        }
        return drive;
    }
}
