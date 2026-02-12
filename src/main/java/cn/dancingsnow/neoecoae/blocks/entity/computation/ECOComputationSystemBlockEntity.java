package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaJustify;

import java.util.List;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity> implements ISyncPersistRPCBlockEntity {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    private int usedThread;
    @DescSynced
    private int totalThread;
    @DescSynced
    private int parallelCount;
    @DescSynced
    private long availableBytes;
    @DescSynced
    private long totalBytes;

    public ECOComputationSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            updateInfos();
        }
    }

    public void updateInfos() {
        if (cluster != null) {
            availableBytes = cluster.getAvailableStorage();
            totalBytes = 0;
            for (ECOComputationDriveBlockEntity drive : cluster.getUpperDrives()) {
                ItemStack cellStack = drive.getCellStack();
                if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    totalBytes += cellItem.getTier().getCPUTotalBytes();
                }
            }
            for (ECOComputationDriveBlockEntity drive : cluster.getLowerDrives()) {
                ItemStack cellStack = drive.getCellStack();
                if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    totalBytes += cellItem.getTier().getCPUTotalBytes();
                }
            }

            usedThread = cluster.getActiveCPUs().size();
            totalThread = cluster.getMaxThreads();
            parallelCount = cluster.getParallelCores().stream().mapToInt(e -> e.getTier().getCPUAccelerators()).sum();
        } else {
            totalThread = 0;
            parallelCount = 0;
            availableBytes = 0;
            totalBytes = 0;
        }
        setChanged();
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .setPadding(YogaEdge.ALL, 4)
            .setGap(YogaGutter.ALL, 2)
            .setJustifyContent(YogaJustify.CENTER)
        ).addClass("panel_bg");

        ScrollerView textPanel = new ScrollerView().viewContainer(view -> view.getLayout().gapAll(2));
        textPanel.addScrollViewChild(new TextElement()
            .setText(getItemFromBlockEntity().getDescription())
            .textStyle(ECOComputationSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(5)));

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.computation.thread_info", usedThread, totalThread)))
            .textStyle(ECOComputationSystemBlockEntity::textStyle));
        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.computation.parallel_info", parallelCount)))
            .textStyle(ECOComputationSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(10)));

        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.computation.storage_info", Tooltips.ofBytes(availableBytes), Tooltips.ofBytes(totalBytes))))
            .textStyle(ECOComputationSystemBlockEntity::textStyle));

        textPanel.layout(layout -> layout.setHeight(160).setWidth(220));

        root.addChild(textPanel);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static void textStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0xadb0c4).textShadow(false);
    }
}
