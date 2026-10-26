package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.api.config.CpuSelectionMode;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPage;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftingStatusMenuMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CpuListPageSyncTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static List<CraftingStatusMenu.CraftingCpuListEntry> cpus(int size) {
        return IntStream.rangeClosed(1, size).mapToObj(i -> new CraftingStatusMenu.CraftingCpuListEntry(
            i, 1024, 1, null, CpuSelectionMode.ANY, null, 0, 0)).toList();
    }

    @Test void pageCodecRoundTripsOffscreenSelectionAndConsumesOnlyItsOwnBytes() {
        var page = ECOCpuListPage.sample(cpus(1000), 900, 7, 1);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            page.writeToPacket(buf);
            int bytes = buf.writerIndex();
            buf.writeInt(0x12345678);
            assertEquals(page, new ECOCpuListPage(buf));
            assertEquals(bytes, buf.readerIndex());
            assertEquals(0x12345678, buf.readInt());
            assertTrue(bytes < 512, "CPU-list packets must stay bounded in a thousand-CPU network");
        } finally { buf.release(); }
    }

    @Test void equalRowsWithDifferentScrollSequencesAreStillGuiSyncChanges() {
        var cpus = cpus(10);
        assertNotEquals(ECOCpuListPage.sample(cpus, 0, 0, 1), ECOCpuListPage.sample(cpus, 0, 2, 1));
    }

    @Test void nativeGuiSyncCannotSerializeTheCompleteServerList() throws Exception {
        var menu = mock(CraftingStatusMenuMixin.class, CALLS_REAL_METHODS);
        var full = new CraftingStatusMenu.CraftingCpuList(cpus(1000));
        menu.cpuList = full;
        field(menu, "selectedCpuSerial", 500);
        field(menu, "neoecoae$cpuOffset", 994);
        Operation<Void> sync = arguments -> {
            assertTrue(menu.cpuList.cpus().isEmpty());
            assertEquals(1000, menu.neoecoae$getCpuPage().total());
            assertEquals(6, menu.neoecoae$getCpuPage().cpus().size());
            assertEquals(500, menu.neoecoae$getCpuPage().selected().serial());
            return null;
        };
        invokeSync(menu, sync);
        assertSame(full, menu.cpuList, "Native selection must still see the complete server list");
    }

    @Test void aFailedSyncRestoresTheCompleteServerList() throws Exception {
        var menu = mock(CraftingStatusMenuMixin.class, CALLS_REAL_METHODS);
        var full = new CraftingStatusMenu.CraftingCpuList(cpus(10));
        menu.cpuList = full;
        assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> invokeSync(menu, arguments -> { throw new IllegalStateException("send failed"); }));
        assertSame(full, menu.cpuList);
    }

    @Test void fullContainerResyncAlsoUsesTheBoundedPublisher() throws Exception {
        var mixin = new ClassNode();
        try (var stream = CraftingStatusMenuMixin.class.getResourceAsStream("CraftingStatusMenuMixin.class")) {
            new ClassReader(stream).accept(mixin, 0);
        }
        var fullSync = mixin.methods.stream().filter(method -> method.name.equals("sendAllDataToRemote"))
            .findFirst().orElseThrow();
        assertTrue(java.util.stream.StreamSupport.stream(fullSync.instructions.spliterator(), false)
            .anyMatch(node -> node instanceof MethodInsnNode call && call.name.equals("neoecoae$publishCpuPage")));
    }

    @Test void invalidPageSizesAreRejectedBeforeAllocatingOrDecodingEntries() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buf.writeVarInt(1000);
            buf.writeVarInt(0);
            buf.writeVarLong(0);
            buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> new ECOCpuListPage(buf));
        } finally { buf.release(); }
    }

    private static void invokeSync(CraftingStatusMenuMixin menu, Operation<Void> sync) throws Exception {
        var method = CraftingStatusMenuMixin.class.getDeclaredMethod("neoecoae$syncOnlyCpuPage",
            CraftingStatusMenu.class, Operation.class);
        method.setAccessible(true);
        method.invoke(menu, mock(CraftingStatusMenu.class), sync);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        var field = CraftingStatusMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
