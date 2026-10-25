package cn.dancingsnow.neoecoae.integration.xei;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.integration.emi.EmiBookmarkAccess;
import cn.dancingsnow.neoecoae.integration.jei.JeiBookmarkAccess;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipeViewerBookmarkAccessTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emiOwnsBookmarksWithOrWithoutJei(boolean jeiInstalled) {
        var mods = mock(ModList.class);
        when(mods.isLoaded("emi")).thenReturn(true);
        when(mods.isLoaded("jei")).thenReturn(jeiInstalled);
        List<AEKey> keys = List.of(mock(AEKey.class));
        try (var modList = mockStatic(ModList.class);
             var emi = mockStatic(EmiBookmarkAccess.class);
             var jei = mockStatic(JeiBookmarkAccess.class)) {
            modList.when(ModList::get).thenReturn(mods);
            emi.when(EmiBookmarkAccess::bookmarkedKeys).thenReturn(keys);
            assertTrue(RecipeViewerBookmarkAccess.isAvailable());
            assertEquals(keys, RecipeViewerBookmarkAccess.bookmarkedKeys());
            RecipeViewerBookmarkAccess.addMissingToBookmarks(keys);
            emi.verify(() -> EmiBookmarkAccess.addMissingToBookmarks(keys));
            jei.verifyNoInteractions();
        }
    }

    @Test void jeiOnlyKeepsTheExistingBookmarkImplementation() {
        var mods = mock(ModList.class);
        List<AEKey> keys = List.of(mock(AEKey.class));
        try (var modList = mockStatic(ModList.class);
             var jei = mockStatic(JeiBookmarkAccess.class);
             var emi = mockStatic(EmiBookmarkAccess.class)) {
            modList.when(ModList::get).thenReturn(mods);
            jei.when(JeiBookmarkAccess::isAvailable).thenReturn(true);
            jei.when(JeiBookmarkAccess::bookmarkedKeys).thenReturn(keys);
            assertTrue(RecipeViewerBookmarkAccess.isAvailable());
            assertEquals(keys, RecipeViewerBookmarkAccess.bookmarkedKeys());
            RecipeViewerBookmarkAccess.addMissingToBookmarks(keys);
            jei.verify(() -> JeiBookmarkAccess.addMissingToBookmarks(keys));
            emi.verifyNoInteractions();
        }
    }

    @Test void noViewerNeverLoadsEitherOptionalImplementation() throws Exception {
        var forbiddenLoads = new ArrayList<String>();
        String entryPoint = RecipeViewerBookmarkAccess.class.getName();
        String jeiEntry = JeiBookmarkAccess.class.getName();
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("dev.emi.") || name.startsWith("mezz.jei.")
                        || name.startsWith("cn.dancingsnow.neoecoae.integration.emi.")
                        || name.equals(jeiEntry + "Impl")) {
                    forbiddenLoads.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(entryPoint) && !name.equals(jeiEntry)) return super.loadClass(name, resolve);
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
        var mods = mock(ModList.class);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            var access = loader.loadClass(entryPoint);
            assertEquals(false, access.getMethod("isAvailable").invoke(null));
            assertEquals(List.of(), access.getMethod("bookmarkedKeys").invoke(null));
            access.getMethod("addMissingToBookmarks", List.class).invoke(null, List.of());
            assertTrue(forbiddenLoads.isEmpty(), () -> "Loaded absent viewer classes: " + forbiddenLoads);
        }
    }
}
