package cn.dancingsnow.neoecoae.crafting.execution.worker;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCraftingThreadPersistenceTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(strings = {"outputItems", "inputItems", "remainingItems"})
    void aLargePhysicalStackSurvivesSaveAndReloadWithoutTruncation(String listName) throws Exception {
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        var original = new ECOCraftingThread(worker);
        int amount = 99 * 256 + 1;
        stacks(original, listName).add(new ItemStack(Items.DIAMOND, amount));
        if (!listName.equals("outputItems")) stacks(original, "outputItems").add(new ItemStack(Items.EMERALD));
        if (!listName.equals("inputItems")) stacks(original, "inputItems").add(new ItemStack(Items.IRON_INGOT));
        field("isBusy").set(original, true);
        field("outputsReady").set(original, !listName.equals("inputItems"));
        var active = field("recoveryState").getType().getDeclaredField("ACTIVE");
        active.setAccessible(true);
        field("recoveryState").set(original, active.get(null));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = original.serializeNBT(registries);
        assertEquals(257, saved.getList(listName, Tag.TAG_COMPOUND).size());

        var restored = new ECOCraftingThread(worker);
        restored.deserializeNBT(registries, saved);

        assertEquals(amount, stacks(restored, listName).stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(restored.isBusy());
        assertEquals("ACTIVE", restored.serializeNBT(registries).getString("recoveryState"),
            "A valid large list must resume normally rather than enter corruption recovery");
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> stacks(ECOCraftingThread thread, String name) throws Exception {
        return (List<ItemStack>) field(name).get(thread);
    }

    private static Field field(String name) throws Exception {
        Field field = ECOCraftingThread.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
