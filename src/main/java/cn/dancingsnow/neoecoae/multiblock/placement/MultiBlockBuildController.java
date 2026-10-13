package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared state machine for the three system multiblock builders.
 * Persisted and description-synced values remain on the owning block entity so
 * their existing NBT keys and network field layout do not change.
 */
public final class MultiBlockBuildController {
    private final BlockEntity owner;
    private final Host host;
    private MultiBlockBuildSession buildSession;
    private UUID buildPlayerId;

    public <T extends BlockEntity & Host> MultiBlockBuildController(T owner) {
        this.owner = owner;
        this.host = owner;
    }

    public void tick(Level level) {
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
        host.setSelectedBuildLength(Math.clamp(
            host.getSelectedBuildLength() + delta,
            host.getMinBuildLength(),
            host.getMaxBuildLength()
        ));
        host.persistAndSync();
    }

    public void autoBuild(Player player) {
        if (!host.canPlayerInteract(player)
            || !(owner.getLevel() instanceof ServerLevel serverLevel)
            || !(player instanceof ServerPlayer serverPlayer)
            || host.isFormed()
            || host.isBuildInProgress()) {
            return;
        }

        MultiBlockDefinition definition = host.getBuildDefinition();
        if (definition == null) {
            return;
        }
        int buildLength = clampBuildLength(definition);
        host.setSelectedBuildLength(buildLength);
        MultiBlockPlacementPlan plan = previewPlan(serverLevel, definition, buildLength);
        if (!plan.getConflictPositions().isEmpty()) {
            return;
        }
        if (!serverPlayer.isCreative()
            && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            return;
        }

        if (plan.getMissingBlocks().isEmpty()) {
            host.rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan, serverPlayer)) {
                return;
            }
            host.rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }

        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        host.setBuildInProgress(true);
        host.persistAndSync();
        serverPlayer.closeContainer();
    }

    public void setMirrorBuild(Player player, boolean mirrorBuild) {
        if (!host.canPlayerInteract(player) || host.isBuildInProgress()) {
            return;
        }
        host.setMirrorBuild(mirrorBuild);
        host.persistAndSync();
    }

    public @Nullable MultiBlockPlacementPlan createLocalPreviewPlan() {
        Level level = owner.getLevel();
        if (level == null || host.isFormed()) {
            return null;
        }
        MultiBlockDefinition definition = host.getBuildDefinition();
        if (definition == null) {
            return null;
        }
        return previewPlan(level, definition, clampBuildLength(definition));
    }

    private int clampBuildLength(MultiBlockDefinition definition) {
        return Math.clamp(
            host.getSelectedBuildLength(),
            definition.getExpandMin(),
            definition.getExpandMax()
        );
    }

    private MultiBlockPlacementPlan previewPlan(Level level, MultiBlockDefinition definition, int buildLength) {
        return MultiBlockPlacementService.preview(
            level,
            owner.getBlockPos(),
            owner.getBlockState(),
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
            host.rebuildMultiblock();
        }
        host.persistAndSync();
    }

    /**
     * Implemented by the block entity that owns this controller. {@link #rebuildMultiblock()} and
     * {@link #persistAndSync()} are inherited from {@code NEBlockEntity}; the position, state and level
     * come from the {@link BlockEntity} the controller was constructed with.
     */
    public interface Host {
        @Nullable MultiBlockDefinition getBuildDefinition();

        default int getMinBuildLength() {
            MultiBlockDefinition definition = getBuildDefinition();
            return definition == null ? 1 : definition.getExpandMin();
        }

        default int getMaxBuildLength() {
            MultiBlockDefinition definition = getBuildDefinition();
            return definition == null ? 1 : definition.getExpandMax();
        }

        int getSelectedBuildLength();

        void setSelectedBuildLength(int length);

        boolean isMirrorBuild();

        void setMirrorBuild(boolean mirrorBuild);

        boolean isBuildInProgress();

        void setBuildInProgress(boolean buildInProgress);

        boolean isFormed();

        boolean canPlayerInteract(Player player);

        void rebuildMultiblock();

        void persistAndSync();
    }
}
