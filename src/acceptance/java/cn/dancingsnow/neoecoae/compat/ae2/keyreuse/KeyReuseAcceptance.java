package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.*;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Executed in the real transformed launcher; excluded from every published mod JAR. */
@EventBusSubscriber(modid = "neoecoae")
public final class KeyReuseAcceptance {
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks.add(message);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("neoecoae.keyReuseAcceptance")) return;
        String outcome = "PASS";
        Throwable failure = null;
        try {
            run(event.getServer().registryAccess());
        } catch (Throwable problem) {
            outcome = "FAIL";
            failure = problem;
            problem.printStackTrace();
        }
        try {
            Path report = Path.of("key-reuse-acceptance-" + (event.getServer().isDedicatedServer() ? "server" : "client") + ".txt");
            Files.writeString(report, outcome + "\n" + String.join("\n", checks) + "\n" + (failure == null ? "" : failure.toString()));
            System.out.println("[KEY_REUSE_ACCEPTANCE] " + outcome + " " + checks.size() + " checks; " + report.toAbsolutePath());
        } catch (Exception problem) { throw new RuntimeException(problem); }
        if (event.getServer().isDedicatedServer()) event.getServer().halt(false);
    }

    private static void run(net.minecraft.core.RegistryAccess registries) throws Exception {
        check(KeyReuse.epoch() != null, "local config enabled reuse before acceptance overrides");
        ItemStack input = new ItemStack(Items.IRON_INGOT, 64);
        FluidStack fluid = new FluidStack(Fluids.WATER, 1000);
        KeyReuse.configure(false);
        AEItemKey oldItem = AEItemKey.of(input);
        AEFluidKey oldFluid = AEFluidKey.of(fluid);
        CompoundTag oldItemTag = oldItem.toTagGeneric(registries);
        CompoundTag oldFluidTag = oldFluid.toTagGeneric(registries);
        ItemStack cellStack = new ItemStack(NEItems.ECO_ITEM_CELL_16M.get());
        ECOStorageCell oldCell = new ECOStorageCell(cellStack, null);
        check(oldCell.insert(oldItem, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite cell inserted");
        CompoundTag serializedCell = (CompoundTag) cellStack.save(registries);
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        SavedDataInfiniteStorageEngine oldEngine = new SavedDataInfiniteStorageEngine(data);
        BigInteger huge = BigInteger.TEN.pow(40);
        oldEngine.insert(oldItem, huge, Actionable.MODULATE);
        oldEngine.insert(oldFluid, BigInteger.ONE.shiftLeft(100), Actionable.MODULATE);
        UUID receipt = UUID.fromString("c5348bb2-50fa-4d72-868c-1fc7cf663eee");
        oldEngine.insertOnce(receipt, oldItem, 17);
        CompoundTag oldSnapshot = data.save(new CompoundTag(), registries);
        Path snapshot = Path.of("key-reuse-old-inventory.nbt");
        NbtIo.writeCompressed(oldSnapshot, snapshot);

        KeyReuse.configure(true);
        AEItemKey item = AEItemKey.of(input);
        AEFluidKey water = AEFluidKey.of(fluid);
        check(item != oldItem && item.equals(oldItem) && item.hashCode() == oldItem.hashCode(), "old and reused items retain equals/hashCode");
        check(water != oldFluid && water.equals(oldFluid), "old and reused fluids retain equals");
        check(item == AEItemKey.of(input) && item == AEItemKey.of(input.copyWithCount(3)), "item factories reuse before copying");
        check(water == AEFluidKey.of(fluid) && water == AEFluidKey.of(Fluids.WATER), "fluid factories reuse");
        check(item.getReadOnlyStack().getCount() == 1 && input.getCount() == 64, "representative count is one and source is untouched");
        check(item.toTagGeneric(registries).equals(oldItemTag) && water.toTagGeneric(registries).equals(oldFluidTag), "encoded key fields are unchanged");
        check(AEKey.fromTagGeneric(registries, oldItemTag) == item && AEKey.fromTagGeneric(registries, oldFluidTag) == water, "old generic key codecs reuse");
        check(GenericStack.readTag(registries, GenericStack.writeTag(registries, new GenericStack(oldItem, 987))).what() == item, "direct GenericStack map codec reuses");
        RegistryFriendlyByteBuf packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            oldItem.writeToPacket(packet);
            check(AEItemKey.fromPacket(packet) == item, "item packet decoder reuses");
            packet.clear();
            oldFluid.writeToPacket(packet);
            check(AEFluidKey.fromPacket(packet) == water, "fluid packet decoder reuses");
        } finally { packet.release(); }
        ItemStack named = input.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("variant"));
        check(AEItemKey.of(named) != AEItemKey.of(named) && !item.equals(AEItemKey.of(named)), "component variants retain the upstream path");
        ItemStack removed = input.copy();
        removed.remove(DataComponents.MAX_STACK_SIZE);
        check(!removed.isComponentsPatchEmpty() && !item.equals(AEItemKey.of(removed)), "removed default components are not pooled");
        check(AEItemKey.of(ItemStack.EMPTY) == null && AEFluidKey.of(FluidStack.EMPTY) == null, "empty factories retain null semantics");
        KeyReuse.invalidate();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = new ArrayList<java.util.concurrent.Callable<AEItemKey>>();
            for (int i = 0; i < 64; i++) calls.add(() -> AEItemKey.of(input));
            var values = executor.invokeAll(calls);
            Object shared = values.getFirst().get();
            for (var value : values) check(value.get() == shared, "concurrent cold item publication");
        }
        AEItemKey refreshed = AEItemKey.of(input);
        check(item != refreshed && item.equals(refreshed), "invalidation preserves old key equality");
        ECOStorageCell reloadedCell = new ECOStorageCell(ItemStack.parse(registries, serializedCell).orElseThrow(), null);
        check(reloadedCell.extract(refreshed, 4096, Actionable.MODULATE, IActionSource.empty()) == 4096, "old finite inventory reload and extraction conserve resources");
        ECOInfiniteStorageData loaded = ECOInfiniteStorageData.load(NbtIo.readCompressed(snapshot, NbtAccounter.unlimitedHeap()), registries);
        SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(loaded);
        check(engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))), "old 10^40 item quantity survives real key codec and disk reload");
        check(engine.getAmount(AEFluidKey.of(fluid)).toBigInteger().equals(BigInteger.ONE.shiftLeft(100)), "old U126 fluid quantity survives reload");
        check(engine.insertOnce(receipt, refreshed, 17) == 17
            && engine.getAmount(refreshed).toBigInteger().equals(huge.add(BigInteger.valueOf(17))),
            "migration receipt prevents duplicate import after reload");
        check(engine.extract(refreshed, 31, Actionable.MODULATE) == 31 && engine.getAmount(oldItem).toBigInteger().equals(huge.subtract(BigInteger.valueOf(14))), "old and new key extraction shares one exact ledger");
        KeyReuse.configure(false);
        AEItemKey disabled = AEItemKey.of(input);
        check(disabled != AEItemKey.of(input) && disabled.equals(refreshed), "disabled mode restores factory behavior and old access");
        KeyReuse.configure(true);
        check(AEItemKey.of(input) != refreshed && AEItemKey.of(input).equals(disabled), "config reenable creates a fresh valid generation");
        checkOptionalChannels(registries);
    }

    private static void checkOptionalChannels(net.minecraft.core.RegistryAccess registries) {
        var otherChannel = new OtherChannelMatterType();
        KeyReuse.configure(false);
        String originalRejection;
        try {
            dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(otherChannel);
            throw new AssertionError("unregistered MatterType was accepted");
        } catch (IllegalArgumentException expected) { originalRejection = expected.getMessage(); }
        KeyReuse.configure(true);
        Object epoch = KeyReuse.epoch();
        synchronized (otherChannel.slot) { otherChannel.slot.set(epoch, AEItemKey.of(new ItemStack(Items.IRON_INGOT))); }
        try {
            dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(otherChannel);
            throw new AssertionError("unregistered MatterType was accepted with reuse enabled");
        } catch (IllegalArgumentException expected) {
            check(java.util.Objects.equals(originalRejection, expected.getMessage()), "unknown matter implementations retain upstream rejection");
        }
        check(KeyReuse.peek(otherChannel) instanceof AEItemKey, "different resource channels cannot overwrite one owner slot");
        var matterType = com.buuz135.replication.api.MatterType.EARTH;
        var matter = dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(matterType);
        check(matter == dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(matterType), "matter type factory reuses");
        check(matter == dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.of(new com.buuz135.replication.api.matter_fluid.MatterStack(matterType, 99)), "matter stack factory reuses");
        matter.getStack().setAmount(77);
        check(matter.getStack().getAmount() == 1, "shared matter stack is defensively copied");
        check(AEKey.fromTagGeneric(registries, matter.toTagGeneric(registries)) == matter, "matter map codec reuses");
        RegistryFriendlyByteBuf matterPacket = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            matter.writeToPacket(matterPacket);
            check(dev.lapis256.apprep.api.ae2.stack.MatterKey.Companion.fromPacket(matterPacket) == matter, "matter packet decoder reuses");
        } finally { matterPacket.release(); }
        var type = com.glodblock.github.appflux.common.me.key.type.EnergyType.values()[0];
        var flux = com.glodblock.github.appflux.common.me.key.FluxKey.of(type);
        check(flux == com.glodblock.github.appflux.common.me.key.FluxKey.of(type), "energy factory reuses");
        check(AEKey.fromTagGeneric(registries, flux.toTagGeneric(registries)) == flux, "energy codec reuses constructor-reference path");
        var chemical = new mekanism.api.chemical.ChemicalStack(mekanism.common.registries.MekanismChemicals.OXYGEN, 123);
        var key = me.ramidzkh.mekae2.ae2.MekanismKey.of(chemical);
        check(key == me.ramidzkh.mekae2.ae2.MekanismKey.of(chemical), "chemical factory reuses");
        key.getStack().setAmount(88);
        check(key.getStack().getAmount() == 1 && chemical.getAmount() == 123, "shared chemical stack is defensively copied");
        check(AEKey.fromTagGeneric(registries, key.toTagGeneric(registries)) == key, "chemical codec reuses");
        RegistryFriendlyByteBuf packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            key.writeToPacket(packet);
            check(me.ramidzkh.mekae2.ae2.MekanismKey.fromPacket(packet) == key, "chemical packet reuses");
        } finally { packet.release(); }
    }
    private static final class OtherChannelMatterType implements com.buuz135.replication.api.IMatterType, KeyReuseOwner {
        final KeyReuseSlot slot = new KeyReuseSlot();
        @Override public KeyReuseSlot neoecoae$keyReuseSlot() { return slot; }
        @Override public String getName() { return "key_reuse_acceptance"; }
        @Override public java.util.function.Supplier<float[]> getColor() { return () -> new float[] {1, 1, 1}; }
        @Override public int getMax() { return 1000; }
    }

}
