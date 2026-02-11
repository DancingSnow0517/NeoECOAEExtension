package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.items.cell.ECOStorageCell;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaJustify;

import java.util.Comparator;
import java.util.List;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity> implements ISyncPersistRPCBlockEntity,  IGridTickable {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @DescSynced
    private long[] usedTypes;
    @DescSynced
    private long[] totalTypes;
    @DescSynced
    private long[] usedBytes;
    @DescSynced
    private long[] totalBytes;

    @DescSynced
    private long storedEnergy;
    @DescSynced
    private long maxEnergy;

    public ECOStorageSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;

        getMainNode().addService(IGridTickable.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(256 + (1 << (1 + 4 * tier.getTier())));
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            updateInfos();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false);
    }


    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        updateInfos();
        return TickRateModulation.URGENT;
    }

    @SuppressWarnings("UnstableApiUsage")
    private void updateInfos() {
        if (cluster != null) {
            storedEnergy = 0;
            maxEnergy = 0;
            for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
                storedEnergy += (long) energyCell.getAECurrentPower();
                maxEnergy += (long) energyCell.getAEMaxPower();
            }

            usedTypes = new long[AEKeyTypesInternal.getAllTypes().size()];
            totalTypes = new long[AEKeyTypesInternal.getAllTypes().size()];
            usedBytes = new long[AEKeyTypesInternal.getAllTypes().size()];
            totalBytes = new long[AEKeyTypesInternal.getAllTypes().size()];
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                ECOStorageCell inv = drive.getCellInventory();
                if (inv != null) {
                    AEKeyType keyType = inv.getKeyType();
                    usedTypes[keyType.getRawId()] += inv.getStoredItemTypes();
                    totalTypes[keyType.getRawId()] += inv.getTotalItemTypes();
                    usedBytes[keyType.getRawId()] += inv.getUsedBytes();
                    totalBytes[keyType.getRawId()] += inv.getTotalBytes();
                }
            }
            setChanged();
        }
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
            .textStyle(ECOStorageSystemBlockEntity::textStyle)
            .layout(layout -> layout.marginBottom(5)));
        //noinspection UnstableApiUsage
        AEKeyTypesInternal.getAllTypes().stream()
            .sorted(Comparator.comparingInt(AEKeyType::getRawId))
            .forEachOrdered(keyType -> {
                textPanel.addScrollViewChild(new Label()
                    .setText(keyType.getDescription())
                    .textStyle(ECOStorageSystemBlockEntity::textStyle));
                textPanel.addScrollViewChild(new Label()
                    .bindDataSource(SupplierDataSource.of(() -> Tooltips.typesUsed(usedTypes[keyType.getRawId()], totalTypes[keyType.getRawId()])))
                    .textStyle(ECOStorageSystemBlockEntity::textStyle));
                textPanel.addScrollViewChild(new Label()
                    .bindDataSource(SupplierDataSource.of(() -> Tooltips.bytesUsed(usedBytes[keyType.getRawId()], totalBytes[keyType.getRawId()])))
                    .textStyle(ECOStorageSystemBlockEntity::textStyle));
            });

        textPanel.addScrollViewChild(new Label().setText("").textStyle(ECOStorageSystemBlockEntity::textStyle));

        textPanel.addScrollViewChild(new Label()
            .setText(Component.translatable("gui.neoecoae.storage.energy"))
            .textStyle(ECOStorageSystemBlockEntity::textStyle));
        textPanel.addScrollViewChild(new Label()
            .bindDataSource(SupplierDataSource.of(() -> Component.translatable("gui.neoecoae.storage.energy_status", Tooltips.ofNumber(storedEnergy), Tooltips.ofNumber(maxEnergy), (int) ((double) storedEnergy / maxEnergy * 100))))
            .textStyle(ECOStorageSystemBlockEntity::textStyle));

        textPanel.layout(layout -> layout.setHeight(160).setWidth(220));

        root.addChild(textPanel);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static void textStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0xadb0c4).textShadow(false);
    }
}
