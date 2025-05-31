package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextBoxWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ECOCraftingSystemBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingSystemBlockEntity>
    implements IGridTickable, IUIHolder.Block, IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IManaged {

    public static final int MAX_COOLANT = 100000;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ECOCraftingSystemBlockEntity.class);
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    @DescSynced
    private boolean overclocked = false;

    @Persisted
    @DescSynced
    private boolean cooled = false;

    @Persisted
    @DescSynced
    private int coolant = 0;

    @DescSynced
    private int threadCount, patternBusCount, parallelCount, workerCount = 0;

    public ECOCraftingSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public IManagedStorage getSyncStorage() {
        return syncStorage;
    }

    @Override
    public IManagedStorage getRootStorage() {
        return getSyncStorage();
    }

    @Override
    public void onChanged() {
        setChanged();
        markForUpdate();
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            updateInfo();
        }
    }

    private void updateInfo() {
        updateThreadCount();
        updateCount();
    }

    private void updateThreadCount() {
        if (cluster != null && !cluster.getParallelCores().isEmpty()) {
            int perCore = tier.getCrafterParallel();
            if (overclocked) {
                perCore += tier.getOverclockedCrafterParallel();
            }
            threadCount = cluster.getParallelCores().size() * perCore;
        } else {
            threadCount = 0;
        }
    }

    private void updateCount() {
        if (cluster != null) {
            parallelCount = cluster.getParallelCores().size();
            patternBusCount = cluster.getParallelCores().size();
            workerCount = cluster.getWorkers().size();
        } else {
            parallelCount = 0;
            patternBusCount = 0;
            workerCount = 0;
        }
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return false;
    }

    public boolean isBusy() {
        return false;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return null;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return null;
    }

    private WidgetGroup createUI() {
        WidgetGroup root = new WidgetGroup();
        root.setSize(120, 120);

        WidgetGroup switchGroup = new WidgetGroup();
        switchGroup.setSize(80, 36);
        switchGroup.setSelfPosition(8, 8);

        SwitchWidget overclockSwitch = new SwitchWidget(0, 0, 36, 36, ((d, b) -> overclocked = b));
        overclockSwitch.initTemplate();
        overclockSwitch.setBaseTexture(GuiTextures.OVERCLOCK_OFF);
        overclockSwitch.setPressedTexture(GuiTextures.OVERCLOCK_ON);
        overclockSwitch.setPressed(overclocked);
        switchGroup.addWidget(overclockSwitch);

        SwitchWidget cooledSwitch = new SwitchWidget(40, 0, 36, 36, ((d, b) -> cooled = b));
        cooledSwitch.initTemplate();
        cooledSwitch.setBaseTexture(GuiTextures.COOLING_OFF);
        cooledSwitch.setPressedTexture(GuiTextures.COOLING_ON);
        cooledSwitch.setPressed(cooled);
        switchGroup.addWidget(cooledSwitch);

        root.addWidget(switchGroup);

        WidgetGroup infoGroup = new WidgetGroup();

        infoGroup.setSelfPosition(8, 50);
        TextBoxWidget totalInfoWidget = new TextBoxWidget(0, 0, 40, List.of(
            Component.translatable("gui.neoecoae.crafting.pattern_bus_count", patternBusCount).getString(),
            Component.translatable("gui.neoecoae.crafting.parallel_core_count", parallelCount).getString(),
            Component.translatable("gui.neoecoae.crafting.worker_count", workerCount).getString()
        ));
        totalInfoWidget.setSize(200, 6);
        totalInfoWidget.setFontSize(7);
        totalInfoWidget.setFontColor(0x69F0AE);

        infoGroup.addWidget(totalInfoWidget);

        root.addWidget(infoGroup);

        return root;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(createUI(), this, entityPlayer);
    }
}

