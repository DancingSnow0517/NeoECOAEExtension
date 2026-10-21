package cn.dancingsnow.neoecoae.mixins.client.ae2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.client.gui.widgets.InfoBar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftingCpuListEntry;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftingCpuListEntryMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class CpuFormattingOwnershipTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void listFormattingOnlyChangesEcoEntries(boolean eco) throws Exception {
        var cpu = entry();
        var mixin = new CpuSelectionListMixin();
        var infoBar = mock(InfoBar.class);
        List<Object[]> calls = new ArrayList<>();
        Operation<Void> original = args -> { calls.add(args); return null; };
        try (var ownership = mockStatic(ECOCraftingCpuListEntry.class, CALLS_REAL_METHODS)) {
            if (eco) ownership.when(() -> ECOCraftingCpuListEntry.isEcoCpu(cpu)).thenReturn(true);
            for (String handler : List.of("wrapStorageAdd", "wrapAdd")) {
                Method method = CpuSelectionListMixin.class.getDeclaredMethod(handler, InfoBar.class,
                    String.class, int.class, float.class, int.class, int.class, Operation.class,
                    CraftingStatusMenu.CraftingCpuListEntry.class);
                method.setAccessible(true);
                method.invoke(mixin, infoBar, "other-mod-format", 0x123456, 0.666f, 12, 34, original, cpu);
            }
            assertEquals(2, calls.size());
            assertArrayEquals(new Object[] {infoBar, eco ? "8 EB" : "other-mod-format",
                0x123456, 0.666f, 12, 34}, calls.get(0));
            assertArrayEquals(new Object[] {infoBar, eco ? "1M" : "other-mod-format",
                0x123456, 0.666f, 12, 34}, calls.get(1));

            MutableComponent existing = Component.literal("other-mod-tooltip");
            List<Object[]> tooltipCalls = new ArrayList<>();
            Operation<MutableComponent> tooltipOriginal = args -> { tooltipCalls.add(args); return existing; };
            Method tooltip = CpuSelectionListMixin.class.getDeclaredMethod("wrapStorageTooltip",
                long.class, Operation.class, CraftingStatusMenu.CraftingCpuListEntry.class);
            tooltip.setAccessible(true);
            var result = (MutableComponent) tooltip.invoke(mixin, cpu.storage(), tooltipOriginal, cpu);
            if (eco) {
                assertEquals("8 EB", result.getString());
                assertTrue(tooltipCalls.isEmpty());
            } else {
                assertSame(existing, result);
                assertEquals(1, tooltipCalls.size());
                assertArrayEquals(new Object[] {cpu.storage()}, cn.dancingsnow.neoecoae.platform.JdkCompat.first(tooltipCalls));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void ownershipSurvivesPacketRoundTripIndependentlyOfOverlay(boolean eco, boolean overlay) throws Exception {
        var sender = new CraftingCpuListEntryMixin();
        sender.neoecoae$setEcoCpu(eco);
        var texture = overlay ? new ResourceLocation("addon:textures/cpu.png") : null;
        sender.neoecoae$setOverlay(texture);
        var receiver = new CraftingCpuListEntryMixin();
        // Ensure reading a foreign CPU also clears stale ECO state.
        receiver.neoecoae$setEcoCpu(!eco);
        var cpu = entry();
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try (var overlays = mockStatic(IOverlayTextureHolder.class);
             var ownership = mockStatic(ECOCraftingCpuListEntry.class)) {
            overlays.when(() -> IOverlayTextureHolder.of(cpu)).thenReturn(receiver);
            ownership.when(() -> ECOCraftingCpuListEntry.of(cpu)).thenReturn(receiver);
            Method write = CraftingCpuListEntryMixin.class.getDeclaredMethod("onWriteToPacket",
                FriendlyByteBuf.class, CallbackInfo.class);
            write.setAccessible(true);
            write.invoke(sender, buf, new CallbackInfo("writeToPacket", false));
            Method read = CraftingCpuListEntryMixin.class.getDeclaredMethod("onReadFromPacket",
                FriendlyByteBuf.class, CallbackInfoReturnable.class);
            read.setAccessible(true);
            read.invoke(null, buf, new CallbackInfoReturnable<>("readFromPacket", false, cpu));
            assertEquals(eco, receiver.neoecoae$isEcoCpu());
            assertEquals(texture, receiver.neoecoae$getOverlay());
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void selectionChangesClearEcoOwnershipForForeignAndAutomaticCpus() throws Exception {
        var mixin = new CraftConfirmMenuMixin();
        var selected = CraftConfirmMenuMixin.class.getDeclaredField("selectedCpu");
        selected.setAccessible(true);
        var sync = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$syncSelectedCpuOwnership", CallbackInfo.class);
        sync.setAccessible(true);
        for (ICraftingCPU cpu : new ICraftingCPU[] {mock(ECOCraftingCPU.class), mock(ICraftingCPU.class), null}) {
            selected.set(mixin, cpu);
            sync.invoke(mixin, new CallbackInfo("onCPUSelectionChanged", false));
            assertEquals(cpu instanceof ECOCraftingCPU, mixin.neoecoae$isSelectedCpuEco());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void confirmationFormattingUsesSelectedCpuOwnership(boolean eco) throws Exception {
        var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        when(((ECOCraftConfirmMenuMode) menu).neoecoae$isSelectedCpuEco()).thenReturn(eco);
        var screen = mock(CraftConfirmScreenMixin.class, CALLS_REAL_METHODS);
        doReturn(menu).when(screen).getMenu();
        Object[] arguments = {Long.MAX_VALUE, 1_500_000};
        List<Object[]> calls = new ArrayList<>();
        MutableComponent existing = Component.literal("original-status");
        Operation<MutableComponent> original = args -> { calls.add(args); return existing; };
        Method format = CraftConfirmScreenMixin.class.getDeclaredMethod("neoecoae$formatCpuStatus",
            GuiText.class, Object[].class, Operation.class);
        format.setAccessible(true);
        assertSame(existing, format.invoke(screen, GuiText.ConfirmCraftCpuStatus, arguments, original));
        assertEquals(1, calls.size());
        assertSame(GuiText.ConfirmCraftCpuStatus, cn.dancingsnow.neoecoae.platform.JdkCompat.first(calls)[0]);
        Object[] actual = (Object[]) cn.dancingsnow.neoecoae.platform.JdkCompat.first(calls)[1];
        if (eco) {
            assertEquals("8 EB", ((Component) actual[0]).getString());
            assertEquals("1.5M", ((Component) actual[1]).getString());
        } else {
            assertSame(arguments, actual);
        }
    }

    private static CraftingStatusMenu.CraftingCpuListEntry entry() {
        return new CraftingStatusMenu.CraftingCpuListEntry(1, Long.MAX_VALUE, 1_500_000,
            null, CpuSelectionMode.ANY, null, 0, 0);
    }
}
