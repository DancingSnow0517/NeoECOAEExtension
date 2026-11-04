package cn.dancingsnow.neoecoae.integration.jei;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.integration.jei.multiblock.MultiBlockRecipeTransferHandler;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiBlockRecipeTransferTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void previewNeitherEncodesNorSendsACompletion() {
        var context = context();
        try (var encoding = mockStatic(EncodingHelper.class)) {
            assertNull(MultiBlockRecipeTransferHandler.create().transferRecipe(context, false));
            encoding.verifyNoInteractions();
            verify(context, never()).completeRecipeTransfer(any());
        }
    }

    @Test
    void successfulEncodingCompletesExactlyOnceAfterTheSlotsAreUpdated() {
        var context = context();
        var menu = mock(PatternEncodingTermMenu.class);
        var recipe = mock(MultiBlockInfoWrapper.class, RETURNS_DEEP_STUBS);
        when(recipe.getRequiredItems()).thenReturn(List.of());
        when(recipe.getDefinition().getOwner().value().asItem().getDefaultInstance()).thenReturn(new ItemStack(Items.STONE));
        when(context.getContainer()).thenReturn(menu);
        when(context.getRecipe()).thenReturn(recipe);
        var encoded = new AtomicBoolean();
        doAnswer(call -> {
            assertTrue(encoded.get(), "Completion must follow synchronous slot encoding");
            return null;
        }).when(context).completeRecipeTransfer(RecipeTransferResult.SUCCESS);
        try (var encoding = mockStatic(EncodingHelper.class)) {
            encoding.when(() -> EncodingHelper.encodeProcessingRecipe(eq(menu), anyList(), anyList()))
                .thenAnswer(call -> { encoded.set(true); return null; });
            assertNull(MultiBlockRecipeTransferHandler.create().transferRecipe(context, true));
            encoding.verify(() -> EncodingHelper.encodeProcessingRecipe(eq(menu), anyList(), anyList()), times(1));
            verify(context, times(1)).completeRecipeTransfer(RecipeTransferResult.SUCCESS);
            verify(context, never()).completeRecipeTransfer(RecipeTransferResult.REJECTED);
        }
    }

    @Test
    void failedEncodingReportsRejectionAndPreservesTheFailure() {
        var context = context();
        var recipe = mock(MultiBlockInfoWrapper.class);
        var failure = new IllegalStateException("preview is no longer available");
        when(context.getRecipe()).thenReturn(recipe);
        when(recipe.getRequiredItems()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> MultiBlockRecipeTransferHandler.create().transferRecipe(context, true)));
        verify(context, times(1)).completeRecipeTransfer(RecipeTransferResult.REJECTED);
        verify(context, never()).completeRecipeTransfer(RecipeTransferResult.SUCCESS);
    }

    @Test
    void olderJeiDoesNotLoadTheContextAdapter() throws Exception {
        String handlerName = MultiBlockRecipeTransferHandler.class.getName();
        List<String> forbiddenLoads = new ArrayList<>();
        var source = MultiBlockRecipeTransferHandler.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{source}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("mezz.jei.api.recipe.transfer.IRecipeTransferContext")) throw new ClassNotFoundException(name);
                if (name.equals(handlerName + "$ContextAware") || name.equals("mezz.jei.api.recipe.transfer.RecipeTransferResult")) {
                    forbiddenLoads.add(name);
                    throw new AssertionError("Legacy JEI must not link " + name);
                }
                if (!name.equals(handlerName)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        }) {
            Class<?> legacy = loader.loadClass(handlerName);
            Object handler = legacy.getMethod("create").invoke(null);
            assertSame(legacy, handler.getClass());
            assertEquals(PatternEncodingTermMenu.class, legacy.getMethod("getContainerClass").invoke(handler));
            assertTrue(forbiddenLoads.isEmpty());
        }
    }

    @SuppressWarnings("unchecked")
    private static IRecipeTransferContext<MultiBlockInfoWrapper, PatternEncodingTermMenu> context() {
        return mock(IRecipeTransferContext.class);
    }
}
