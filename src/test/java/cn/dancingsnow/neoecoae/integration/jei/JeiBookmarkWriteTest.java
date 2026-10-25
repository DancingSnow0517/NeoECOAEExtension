package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises JEI 15's real BookmarkList rather than a substitute for its internal bookmark API. */
class JeiBookmarkWriteTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(NeoECOAEJeiPlugin.class.getName());
        }
    }

    @Test void missingItemsAndFluidsReachTheRealListNotifyTheOverlayAndPersist() throws Exception {
        var fixture = new Fixture();
        var item = new ItemStack(Items.STONE);
        item.setHoverName(Component.literal("Exact missing item"));
        var fluid = new FluidStack(Fluids.WATER, 1);
        fluid.getOrCreateTag().putString("test_name", "Exact missing fluid");
        fixture.ingredient(VanillaTypes.ITEM_STACK, item, "item");
        fixture.ingredient(ForgeTypes.FLUID_STACK, fluid, "fluid");
        var keys = List.of(AEItemKey.of(item), AEFluidKey.of(fluid));

        try (var plugin = mockStatic(NeoECOAEJeiPlugin.class)) {
            plugin.when(NeoECOAEJeiPlugin::runtime).thenReturn(fixture.runtime);
            JeiBookmarkAccessImpl.addMissingToBookmarks(keys);
            assertEquals(keys, JeiBookmarkAccessImpl.bookmarkedKeys());
            assertEquals(2, fixture.notifications.get());
            assertEquals(2, fixture.saveCalls());

            // Re-adding an existing fluid must retain the bookmark without rewriting the list.
            JeiBookmarkAccessImpl.addMissingToBookmarks(List.of(keys.get(1)));
            assertEquals(keys, JeiBookmarkAccessImpl.bookmarkedKeys());
            assertEquals(2, fixture.notifications.get());
            assertEquals(2, fixture.saveCalls());
        }
    }

    @Test void unavailableRuntimeDoesNotTryToReadOrWriteBookmarks() {
        try (var plugin = mockStatic(NeoECOAEJeiPlugin.class)) {
            assertFalse(JeiBookmarkAccessImpl.isAvailable());
            assertEquals(List.of(), JeiBookmarkAccessImpl.bookmarkedKeys());
            assertDoesNotThrow(() -> JeiBookmarkAccessImpl.addMissingToBookmarks(List.of(AEItemKey.of(Items.STONE))));
        }
    }

    private static final class Fixture {
        final IJeiRuntime runtime = mock(IJeiRuntime.class);
        final IIngredientManager manager = mock(IIngredientManager.class);
        final AtomicInteger notifications = new AtomicInteger();
        final Object config;

        Fixture() throws Exception {
            var listType = Class.forName("mezz.jei.gui.bookmarks.BookmarkList");
            var constructor = listType.getConstructors()[0];
            var types = constructor.getParameterTypes();
            var arguments = new Object[types.length];
            Object bookmarkConfig = null;
            for (int i = 0; i < types.length; i++) {
                arguments[i] = types[i] == IIngredientManager.class ? manager
                    : types[i] == RegistryAccess.class ? RegistryAccess.EMPTY : mock(types[i]);
                if (types[i].getSimpleName().equals("IBookmarkConfig")) bookmarkConfig = arguments[i];
            }
            config = java.util.Objects.requireNonNull(bookmarkConfig);
            Object list = constructor.newInstance(arguments);
            var listenerType = Class.forName(
                "mezz.jei.gui.overlay.ingredients.IIngredientGridSource$SourceListChangedListener");
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                (proxy, method, args) -> { notifications.incrementAndGet(); return null; });
            listType.getMethod("addSourceListChangedListener", listenerType).invoke(list, listener);

            var overlayType = Class.forName("mezz.jei.gui.overlay.bookmarks.BookmarkOverlay");
            var overlay = (IBookmarkOverlay) mock(overlayType);
            var field = overlayType.getDeclaredField("bookmarkList");
            field.setAccessible(true);
            field.set(overlay, list);
            when(runtime.getBookmarkOverlay()).thenReturn(overlay);
            when(runtime.getIngredientManager()).thenReturn(manager);
            when(manager.normalizeTypedIngredient(any())).thenAnswer(call -> call.getArgument(0));
        }

        @SuppressWarnings("unchecked")
        <T> void ingredient(IIngredientType<T> type, T ingredient, String uid) {
            var typed = (ITypedIngredient<T>) mock(ITypedIngredient.class);
            when(typed.getType()).thenReturn(type);
            when(typed.getIngredient()).thenReturn(ingredient);
            when(typed.getIngredient(type)).thenReturn(Optional.of(ingredient));
            when(manager.createTypedIngredient(eq(type), any(), eq(false))).thenReturn(Optional.of(typed));
            // JEI 15.48 uses getUniqueId; 15.56 uses the newer getUid on the same helper boundary.
            var helper = (IIngredientHelper<T>) mock(IIngredientHelper.class, call ->
                call.getMethod().getName().equals("getUniqueId") || call.getMethod().getName().equals("getUid")
                    ? uid : RETURNS_DEFAULTS.answer(call));
            when(manager.getIngredientHelper(type)).thenReturn(helper);
        }

        long saveCalls() {
            return mockingDetails(config).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("saveBookmarks")).count();
        }
    }
}
