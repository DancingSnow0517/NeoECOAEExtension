package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaFlexDirection;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaJustify;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingPatternBusBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;

    @Persisted
    @DescSynced
    private final AppEngInternalInventory inventory;
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    public final IItemHandlerModifiable itemHandler;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternDetails;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern supportedPattern)) {
            return false;
        }
        if (cluster != null) {
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                if (worker.pushPattern(supportedPattern, inputHolder)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        if (cluster != null && cluster.getController() != null) {
            ECOCraftingSystemBlockEntity controller = cluster.getController();
            if (getRunningThread() >= controller.getThreadCount()) {
                return true;
            }
            for (ECOCraftingWorkerBlockEntity worker : cluster.getWorkers()) {
                if (!worker.isBusy()) {
                    return false;
                }
            }
        }
        return true;
    }

    private long getRunningThread() {
        if (cluster != null) {
            return cluster.getWorkers().stream().mapToLong(ECOCraftingWorkerBlockEntity::getRunningThreads).sum();
        }
        return 0;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getGridNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return inventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (cluster != null && cluster.getController() != null) {
            var block = cluster.getController().getBlockState().getBlock();
            if (block != Blocks.AIR) {
                return new PatternContainerGroup(
                    AEItemKey.of(block.asItem()),
                    block.getName(),
                    List.of()
                );
            }
        }
        return new PatternContainerGroup(
            AEItemKey.of(NEBlocks.CRAFTING_PATTERN_BUS.asStack()),
            NEBlocks.CRAFTING_PATTERN_BUS.get().getName(),
            List.of()
        );
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        ItemStack result = inventory.addItems(itemStack.copy());
        return result.isEmpty();
    }

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern;
        }
    }

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.inventory = new AppEngInternalInventory(this, ROW_SIZE * COL_SIZE);
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = (IItemHandlerModifiable) inventory.toItemHandler();
        this.getMainNode().addService(ICraftingProvider.class, this)
            .addService(IECOPatternStorage.class, this);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.saveChanges();
        updatePatternDetails();
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePatternDetails();
    }

    private void updatePatternDetails() {
        patternDetails.clear();
        for (ItemStack itemStack : this.inventory) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(itemStack, this.level);
            if (details != null) {
                patternDetails.add(details);
            }
        }
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        IntStream.range(0, ROW_SIZE * COL_SIZE)
            .mapToObj(inventory::getStackInSlot)
            .filter(s -> !s.isEmpty())
            .forEach(drops::add);
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .setPadding(YogaEdge.ALL, 4)
            .setGap(YogaGutter.ALL, 2)
            .setJustifyContent(YogaJustify.CENTER)
        ).addClass("panel_bg");

        root.addChild(new TextElement()
            .setText(Component.translatable("block.neoecoae.crafting_pattern_bus"))
            .textStyle(textStyle -> textStyle
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true)));

        UIElement patternInv = new UIElement().addClass("panel_border");
        for (int row = 0; row < COL_SIZE; row++) {
            UIElement rowInv = new UIElement().layout(layout -> layout.setFlexDirection(YogaFlexDirection.ROW));
            for (int col = 0; col < ROW_SIZE; col++) {
                int slotIndex = row * ROW_SIZE + col;
                UIElement slot = new PatternItemSlot(new ItemHandlerSlot(itemHandler, slotIndex))
                    .slotStyle(slotStyle -> slotStyle.slotOverlay(NETextures.PATTERN_OVERLAY));
                rowInv.addChild(slot);
            }
            patternInv.addChild(rowInv);
        }
        root.addChild(patternInv);
        root.addChild(new InventorySlots().layout(layout -> layout.setMargin(YogaEdge.TOP, 5)));
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
}
