package cn.dancingsnow.neoecoae.compat.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOOptionalUnboundedCellsTest {
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void absentModsNeverLoadTheirAdaptersOrThirdPartyClasses(boolean ae2LtInstalled, boolean extendedAeInstalled)
            throws Exception {
        var mods = mock(ModList.class);
        when(mods.isLoaded("ae2lt")).thenReturn(ae2LtInstalled);
        when(mods.isLoaded("extendedae")).thenReturn(extendedAeInstalled);
        String entryPoint = ECOOptionalUnboundedCells.class.getName();
        String ae2LtAdapter = "cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtUnboundedCells";
        String extendedAeAdapter = "cn.dancingsnow.neoecoae.compat.extendedae.ECOExtendedAEUnboundedCells";
        Set<String> localClasses = Set.of(entryPoint, ae2LtAdapter, extendedAeAdapter);
        var forbiddenLoads = new ArrayList<String>();
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if ((!ae2LtInstalled && (name.startsWith("com.moakiee.ae2lt.") || name.equals(ae2LtAdapter)))
                        || (!extendedAeInstalled && (name.startsWith("com.glodblock.github.extendedae.")
                            || name.equals(extendedAeAdapter)))) {
                    forbiddenLoads.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!localClasses.contains(name)) return super.loadClass(name, resolve);
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        assertNotNull(input);
                        byte[] bytes = input.readAllBytes();
                        result = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(result);
                return result;
            }
        };
        try (var mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(mods);
            Class<?> access = loader.loadClass(entryPoint);
            Set<AEKey> result = new HashSet<>();
            access.getMethod("collect", StorageCell.class, Set.class).invoke(null, mock(StorageCell.class), result);
            assertTrue(result.isEmpty());
            assertTrue(forbiddenLoads.isEmpty(), () -> "Loaded absent optional classes: " + forbiddenLoads);
        }
    }

    @Test void uninitializedLoaderOrMissingCellDoesNotConferInfiniteSupply() {
        try (var mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(null);
            Set<AEKey> result = new HashSet<>();
            ECOOptionalUnboundedCells.collect(mock(StorageCell.class), result);
            ECOOptionalUnboundedCells.collect(null, result);
            assertTrue(result.isEmpty());
        }
    }
}
