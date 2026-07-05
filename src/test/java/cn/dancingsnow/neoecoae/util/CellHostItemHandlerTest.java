package cn.dancingsnow.neoecoae.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires a Forge/Minecraft test harness; plain Gradle JUnit cannot bootstrap ItemStack safely.")
class CellHostItemHandlerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rejectsInvalidSlots() {
        TestCellHost host = new TestCellHost(true);
        host.cellStack = new ItemStack(Items.DIAMOND);
        CellHostItemHandler handler = new CellHostItemHandler(host);
        ItemStack inserted = new ItemStack(Items.EMERALD);

        assertTrue(handler.getStackInSlot(1).isEmpty());
        assertSame(inserted, handler.insertItem(1, inserted, false));
        assertTrue(handler.extractItem(1, 1, false).isEmpty());
        assertEquals(0, handler.getSlotLimit(1));
        assertFalse(handler.isItemValid(1, inserted));
        assertEquals(0, host.validityChecks);
    }

    @Test
    void insertItemConsultsHostValidity() {
        TestCellHost host = new TestCellHost(false);
        CellHostItemHandler handler = new CellHostItemHandler(host);
        ItemStack rejected = new ItemStack(Items.DIAMOND, 3);

        assertSame(rejected, handler.insertItem(0, rejected, false));
        assertNull(host.cellStack);
        assertEquals(1, host.validityChecks);

        host.acceptItems = true;
        ItemStack accepted = new ItemStack(Items.DIAMOND, 3);
        ItemStack remainder = handler.insertItem(0, accepted, false);

        assertEquals(1, host.cellStack.getCount());
        assertEquals(2, remainder.getCount());
        assertEquals(Items.DIAMOND, host.cellStack.getItem());
    }

    @Test
    void simulateInsertStillValidatesWithoutMutatingHost() {
        TestCellHost host = new TestCellHost(true);
        CellHostItemHandler handler = new CellHostItemHandler(host);

        ItemStack remainder = handler.insertItem(0, new ItemStack(Items.DIAMOND, 2), true);

        assertNull(host.cellStack);
        assertEquals(1, remainder.getCount());
        assertEquals(1, host.validityChecks);
    }

    private static final class TestCellHost implements ICellHost {
        private boolean acceptItems;
        private int validityChecks;
        private ItemStack cellStack;

        private TestCellHost(boolean acceptItems) {
            this.acceptItems = acceptItems;
        }

        @Override
        public void setCellStack(ItemStack itemStack) {
            this.cellStack = itemStack;
        }

        @Override
        public ItemStack getCellStack() {
            return cellStack;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            validityChecks++;
            return acceptItems;
        }
    }
}
