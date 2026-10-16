package cn.dancingsnow.neoecoae.integration.ae2lt.dispatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.AEBaseMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.Ae2LtIOPortBlockEntityMixin;
import cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.Ae2LtIOPortMenuMixin;
import cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.ECOThunderboltMixinPlugin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

class ECOAe2LtIOPortTest {
    private static final String PREFIX = "cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.";
    private static final String BLOCK = "com/moakiee/ae2lt/blockentity/OverloadedIOPortBlockEntity";
    private static final String MENU = "com/moakiee/ae2lt/menu/OverloadedIOPortMenu";

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void automaticInputAcceptsBothRegistriesAndPreservesExtractionPolicy() {
        var inventory = new AppEngInternalInventory(null, 6, 1);
        var nativeCell = new ItemStack(Items.STONE);
        var ecoCell = new ItemStack(Items.DIRT);
        var invalid = new ItemStack(Items.COBBLESTONE);
        var original = mock(IAEItemFilter.class);
        when(original.allowInsert(inventory, 0, nativeCell)).thenReturn(true);
        when(original.allowExtract(inventory, 0, 1)).thenReturn(false);
        when(original.allowExtract(inventory, 1, 1)).thenReturn(true);
        var filter = new ECOIOPortInputFilter(original);
        try (var eco = mockStatic(ECOStorageCells.class)) {
            eco.when(() -> ECOStorageCells.isCellHandled(ecoCell)).thenReturn(true);
            assertTrue(filter.allowInsert(inventory, 0, nativeCell));
            assertTrue(filter.allowInsert(inventory, 0, ecoCell));
            assertFalse(filter.allowInsert(inventory, 0, invalid));
            assertFalse(filter.allowInsert(inventory, 0, ItemStack.EMPTY));
            assertFalse(filter.allowExtract(inventory, 0, 1));
            assertTrue(filter.allowExtract(inventory, 1, 1));
        }
    }

    @Test void menuCreatesUsableEcoInputSlots() throws Exception {
        var inventory = new AppEngInternalInventory(null, 12, 1);
        var menu = mock(AEBaseMenu.class);
        when(menu.isValidForSlot(any(), any())).thenReturn(true);
        var stack = new ItemStack(Items.DIRT);
        var hook = Ae2LtIOPortMenuMixin.class.getDeclaredMethod("neoecoae$cellSlot",
            RestrictedInputSlot.PlacableItemType.class, InternalInventory.class, int.class, Operation.class);
        hook.setAccessible(true);
        try (var eco = mockStatic(ECOStorageCells.class)) {
            eco.when(() -> ECOStorageCells.isCellHandled(stack)).thenReturn(true);
            for (int index = 0; index < 6; index++) {
                var slot = spy((RestrictedInputSlot) hook.invoke(new Ae2LtIOPortMenuMixin(),
                    RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, inventory, index, null));
                slot.setMenu(menu);
                // AE2's getItem needs Minecraft's access transformer, which isolated unit tests do not run.
                doReturn(ItemStack.EMPTY).when(slot).getItem();
                assertTrue(slot.mayPlace(stack));
                assertFalse(slot.mayPlace(new ItemStack(Items.COBBLESTONE)));
            }
        }
    }

    @Test void transferReadsEcoInventoryWithSaveProviderAndKeepsNativeCells() throws Exception {
        var hook = Ae2LtIOPortBlockEntityMixin.class.getDeclaredMethod("neoecoae$getCellInventory",
            ItemStack.class, ISaveProvider.class, Operation.class);
        hook.setAccessible(true);
        var mixin = new Ae2LtIOPortBlockEntityMixin();
        var stack = new ItemStack(Items.DIRT);
        var host = mock(ISaveProvider.class);
        var nativeCell = mock(StorageCell.class);
        var ecoCell = mock(IECOStorageCell.class);
        Operation<StorageCell> nativeLookup = args -> {
            assertSame(stack, args[0]);
            assertSame(host, args[1]);
            return nativeCell;
        };
        Operation<StorageCell> missing = args -> null;
        try (var eco = mockStatic(ECOStorageCells.class)) {
            assertSame(nativeCell, hook.invoke(mixin, stack, host, nativeLookup));
            eco.verifyNoInteractions();
            eco.when(() -> ECOStorageCells.getCellInventory(stack, host)).thenReturn(ecoCell);
            assertSame(ecoCell, hook.invoke(mixin, stack, host, missing));
            eco.verify(() -> ECOStorageCells.getCellInventory(stack, host));
            assertNull(hook.invoke(mixin, ItemStack.EMPTY, host, missing));
        }
    }

    @Test void optionalMixinsFollowPortAvailabilityWithoutLoadingTheMod() throws Exception {
        String pluginName = ECOThunderboltMixinPlugin.class.getName();
        var parent = getClass().getClassLoader();
        for (boolean available : List.of(false, true)) {
            var loader = new ClassLoader(parent) {
                @Override public URL getResource(String name) {
                    if (name.startsWith("com/moakiee/ae2lt/")) {
                        return available ? parent.getResource(pluginName.replace('.', '/') + ".class") : null;
                    }
                    return super.getResource(name);
                }
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("com.moakiee.ae2lt.")) throw new AssertionError("Loaded optional mod class");
                    if (!name.equals(pluginName)) return super.loadClass(name, resolve);
                    Class<?> result = findLoadedClass(name);
                    if (result == null) {
                        try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
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
            var plugin = (IMixinConfigPlugin) loader.loadClass(pluginName).getConstructor().newInstance();
            assertEquals(available, plugin.shouldApplyMixin(BLOCK.replace('/', '.'), PREFIX + "Ae2LtIOPortBlockEntityMixin"));
            assertEquals(available, plugin.shouldApplyMixin(MENU.replace('/', '.'), PREFIX + "Ae2LtIOPortMenuMixin"));
        }
    }

    /** Run with -Pae2lt_local_jar=... to check the actual newer port's injection points. */
    @Test void newerRuntimeExposesTheExpectedInputFilterAndTransferHooks() throws Exception {
        var loader = getClass().getClassLoader();
        Assumptions.assumeTrue(loader.getResource(BLOCK + ".class") != null, "Old AE2LT has no overloaded IO port");
        var block = readClass(BLOCK);
        var constructor = block.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        MethodInsnNode firstFilter = calls(constructor, "appeng/util/inv/AppEngInternalInventory", "setFilter")
            .getFirst();
        var cursor = firstFilter.getPrevious();
        while (cursor != null && !(cursor instanceof FieldInsnNode field && field.name.equals("input"))) {
            cursor = cursor.getPrevious();
        }
        assertNotNull(cursor, "The first setFilter must still configure the cell input queue");
        var ticking = block.methods.stream().filter(m -> m.name.equals("tickingRequest")).findFirst().orElseThrow();
        var reads = calls(ticking, "appeng/api/storage/StorageCells", "getCellInventory");
        assertEquals(1, reads.size());
        assertEquals("(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;", reads.getFirst().desc);
        var menu = readClass(MENU).methods.stream().filter(m -> m.name.equals("setupConfig")).findFirst().orElseThrow();
        assertEquals(1, calls(menu, "appeng/menu/slot/RestrictedInputSlot", "<init>").size());
    }

    private static ClassNode readClass(String name) throws IOException {
        try (var input = ECOAe2LtIOPortTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        return java.util.Arrays.stream(method.instructions.toArray())
            .filter(i -> i instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name))
            .map(i -> (MethodInsnNode) i).toList();
    }
}
