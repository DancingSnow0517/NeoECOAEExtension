package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared state machine for the three system multiblock builders.
 * Persisted and description-synced values remain on the owning block entity so
 * their existing NBT keys and network field layout do not change.
 */
public final class MultiBlockBuildController {
    private final Host host;
    private MultiBlockBuildSession buildSession;
    private UUID buildPlayerId;
    private final cn.dancingsnow.neoecoae.multiblock.BuildPreviewState terminalPreview=new cn.dancingsnow.neoecoae.multiblock.BuildPreviewState();

    public MultiBlockBuildController(Host host) {
        this.host = host;
    }

    public void writeTerminalPreview(CompoundTag data) {
        terminalPreview.selectedBuildLength = host.getSelectedBuildLength();
        CompoundTag preview = new CompoundTag();
        terminalPreview.writeToTag(preview);
        data.put("structureTerminalPreview", preview);
    }

    public void readTerminalPreview(CompoundTag data) {
        // Placement sessions are transient. Loading their status must not resume an unowned build.
        terminalPreview.buildSession = null;
        terminalPreview.buildPlayerId = null;
        terminalPreview.readFromTag(data.getCompound("structureTerminalPreview"));
        terminalPreview.selectedBuildLength = host.getSelectedBuildLength();
    }

    public void tick(Level level) {
        if(terminalPreview.buildInProgress){host.tickBuild(level);host.setBuildInProgress(terminalPreview.buildInProgress);return;}
        if (!(level instanceof ServerLevel serverLevel) || !host.isBuildInProgress() || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null
            ? null
            : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            finish(false);
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING, ADVANCED -> {
            }
            case COMPLETED -> finish(true);
            case BLOCKED -> finish(false);
        }
    }

    public void increaseBuildLength(Player player) {
        changeBuildLength(player, 1);
    }

    public void decreaseBuildLength(Player player) {
        changeBuildLength(player, -1);
    }

    private void changeBuildLength(Player player, int delta) {
        if (!host.canPlayerInteract(player) || host.isBuildInProgress()) {
            return;
        }
        host.setSelectedBuildLength(JdkCompat.clamp(
            host.getSelectedBuildLength() + delta,
            host.getMinBuildLength(),
            host.getMaxBuildLength()
        ));
        host.buildStateChanged();
    }

    public void autoBuild(Player player) {
        if (!host.canPlayerInteract(player)
            || !(host.getBuildLevel() instanceof ServerLevel serverLevel)
            || !(player instanceof ServerPlayer serverPlayer)
            || host.isFormed()
            || host.isBuildInProgress()) {
            return;
        }

        MultiBlockDefinition definition = host.getBuildDefinition();
        if (definition == null) {
            return;
        }
        int buildLength = JdkCompat.clamp(
            host.getSelectedBuildLength(),
            definition.getExpandMin(),
            definition.getExpandMax()
        );
        host.setSelectedBuildLength(buildLength);
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            host.getBuildPosition(),
            host.getBuildState(),
            definition,
            buildLength,
            host.isMirrorBuild()
        );
        if (!plan.getConflictPositions().isEmpty()) {
            return;
        }
        if (!serverPlayer.isCreative()
            && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            return;
        }

        if (plan.getMissingBlocks().isEmpty()) {
            host.rebuildAfterBuild();
            serverPlayer.closeContainer();
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan, serverPlayer)) {
                return;
            }
            host.rebuildAfterBuild();
            serverPlayer.closeContainer();
            return;
        }

        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        host.setBuildInProgress(true);
        host.buildStateChanged();
        serverPlayer.closeContainer();
    }

    public void setMirrorBuild(Player player, boolean mirrorBuild) {
        if (!host.canPlayerInteract(player) || host.isBuildInProgress()) {
            return;
        }
        host.setMirrorBuild(mirrorBuild);
        host.buildStateChanged();
    }

    public @Nullable MultiBlockPlacementPlan createLocalPreviewPlan() {
        Level level = host.getBuildLevel();
        if (level == null || host.isFormed()) {
            return null;
        }
        MultiBlockDefinition definition = host.getBuildDefinition();
        if (definition == null) {
            return null;
        }
        int buildLength = JdkCompat.clamp(
            host.getSelectedBuildLength(),
            definition.getExpandMin(),
            definition.getExpandMax()
        );
        return MultiBlockPlacementService.preview(
            level,
            host.getBuildPosition(),
            host.getBuildState(),
            definition,
            buildLength,
            host.isMirrorBuild()
        );
    }

    private void finish(boolean rebuild) {
        buildSession = null;
        buildPlayerId = null;
        host.setBuildInProgress(false);
        if (rebuild) {
            host.rebuildAfterBuild();
        }
        host.buildStateChanged();
    }

    public interface Host extends cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost {
        MultiBlockBuildController getBuildController();
        @Override default BlockPos getHostPos(){return getBuildPosition();}
        @Override default BlockState getHostBlockState(){return getBuildState();}
        @Override default Level getHostLevel(){return getBuildLevel();}
        @Override default cn.dancingsnow.neoecoae.multiblock.BuildPreviewState getBuildPreview(){
            var preview=getBuildController().terminalPreview;preview.selectedBuildLength=getSelectedBuildLength();return preview;
        }
        @Override default void markPreviewDirty(){setBuildInProgress(getBuildPreview().buildInProgress);buildStateChanged();}
        @Override default void rebuildMultiblock(){rebuildAfterBuild();}
        @Nullable Level getBuildLevel();

        BlockPos getBuildPosition();

        BlockState getBuildState();

        @Nullable MultiBlockDefinition getBuildDefinition();

        int getMinBuildLength();

        int getMaxBuildLength();

        int getSelectedBuildLength();

        void setSelectedBuildLength(int length);

        boolean isMirrorBuild();

        void setMirrorBuild(boolean mirrorBuild);

        boolean isBuildInProgress();

        void setBuildInProgress(boolean buildInProgress);

        boolean isFormed();

        boolean canPlayerInteract(Player player);

        void rebuildAfterBuild();

        void buildStateChanged();
    }
}
