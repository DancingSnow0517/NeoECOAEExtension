package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.api.NEFakePlayer;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.List;

public class ECOCraftingThread implements INBTSerializable<CompoundTag> {
    public static final int MAX_PROGRESS = 100;

    private final ECOCraftingWorkerBlockEntity worker;
    private final IActionSource actionSource;

    @Getter
    private boolean isBusy = false;

    private boolean reboot = true;

    private ItemStack outputItem = ItemStack.EMPTY;

    private final List<ItemStack> remainingItems = new ArrayList<>();

    private int progress = 0;

    private final TransientCraftingContainer craftingInv;

    public ECOCraftingThread(ECOCraftingWorkerBlockEntity worker) {
        this.worker = worker;
        this.actionSource = IActionSource.ofMachine(worker);
        this.craftingInv = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    }

    /**
     * 宸ヤ綔 tick 鏂规硶
     *
     * @param overlockTimes 瓒呴娆℃暟锛堟瘡娆¤秴棰戝噺灏?tick鏃堕棿锛?
     * @param powerMultiply 鑳介噺鍊嶇巼锛堢敤浜庤秴棰戞棤鍐峰嵈鐨勬儏鍐碉級
     * @param ticksSinceLastCall 璺濈涓婁紶璋冪敤澶氬皯 tick
     */
    public TickRateModulation tick(int overlockTimes, int powerMultiply, int ticksSinceLastCall) {
        if (!isBusy) {
            progress = 0;
            setChanged();
            return TickRateModulation.SLEEP;
        }
        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        this.reboot = false;
        int bonusValue = Math.min(10 + overlockTimes * 10, 100);
        progress += userPower(ticksSinceLastCall, bonusValue, powerMultiply);

        if (this.progress >= MAX_PROGRESS) {
            if (ejectOutputs()) {
                worker.onThreadStop();
                isBusy = false;
                setChanged();
            }
            return TickRateModulation.URGENT;
        }
        setChanged();
        return TickRateModulation.URGENT;
    }

    public boolean isFree() {
        return !isBusy;
    }

    /**
     * 鎻愪氦鏍锋澘
     *
     * @param pattern    瑕佹彁浜ょ殑鏍锋澘
     * @param controller
     * @return 鏄惁鎴愬姛
     */
    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, ECOCraftingSystemBlockEntity controller) {
        if (isBusy) {
            return false;
        }

        return calcPattern(pattern, table, controller);
    }

    private boolean calcPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, ECOCraftingSystemBlockEntity controller) {
        if (controller.isActiveCooling()) {
            if (!controller.tryConsumeCoolant(5, controller.getEffectiveOverclockTimes())) {
                return false;
            }
        }
        craftingInv.clearContent();
        pattern.fillCraftingGrid(table, craftingInv::setItem);
        ItemStack outputItem = pattern.assemble(craftingInv, worker.getLevel());
        if (outputItem.isEmpty()) {
            return false;
        }
        this.outputItem = outputItem;
        remainingItems.clear();
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : pattern.getRemainingItems(craftingInv)) {
            if (!item.isEmpty()) {
                list.add(item);
            }
        }
        remainingItems.addAll(list);
        worker.onThreadWork();
        isBusy = true;
        reboot = true;
        setChanged();
        return true;
    }

    private int userPower(int ticksPassed, int bonusValue, double acceleratorTax) {
        var grid = this.worker.getMainNode().getGrid();
        if (grid != null) {
            var safePower = Math.min(ticksPassed * bonusValue * acceleratorTax, 500000);
            return (int) (grid.getEnergyService().extractAEPower(safePower, Actionable.MODULATE, PowerMultiplier.CONFIG) / acceleratorTax);
        } else {
            return 0;
        }
    }

    private boolean ejectOutputs() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid != null) {
            MEStorage storage = grid.getStorageService().getInventory();
            if (!tryEject(storage, outputItem)) {
                return false;
            }
            for (ItemStack item : remainingItems) {
                if (!tryEject(storage, item)) {
                    return false;
                }
            }
            eject(storage, outputItem);
            for (ItemStack item : remainingItems) {
                eject(storage, item);
            }
            if (NEConfig.postCraftingEvent) {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                    NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()),
                    outputItem,
                    craftingInv
                ));
            }
            outputItem = ItemStack.EMPTY;
            remainingItems.clear();
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean tryEject(MEStorage storage, ItemStack stack) {
        long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.SIMULATE, actionSource);
        return inserted == stack.getCount();
    }

    private void eject(MEStorage storage, ItemStack stack) {
        storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, actionSource);
    }

    private void setChanged() {
        worker.setChanged();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isBusy", isBusy);
        tag.putBoolean("reboot", reboot);
        tag.putInt("progress", progress);
        tag.put("outputItem", outputItem.save(new CompoundTag()));
        ListTag remaining = new ListTag();
        for (ItemStack remainingItem : remainingItems) {
            remaining.add(remainingItem.save(new CompoundTag()));
        }
        tag.put("remainingItems", remaining);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        this.progress = nbt.getInt("progress");
        this.outputItem = ItemStack.of(nbt.getCompound("outputItem"));
        ListTag remaining = nbt.getList("remainingItems", Tag.TAG_COMPOUND);
        remainingItems.clear();
        for (int i = 0; i < remaining.size(); i++) {
            remainingItems.add(ItemStack.of(remaining.getCompound(i)));
        }
    }
}
