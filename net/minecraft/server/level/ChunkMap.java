package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public class ChunkMap extends ChunkStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap {
    private static final ChunkResult<List<ChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<ChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(
        UNLOADED_CHUNK_LIST_RESULT
    );
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    /**
     * Chunks in memory. This should only ever be manipulated by the main thread.
     */
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap = new Long2ObjectLinkedOpenHashMap<>();
    /**
     * Same as {@link #loadedChunks}, but immutable for access from other threads. <em>This should never be mutated.</em>
     */
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap = this.updatingChunkMap.clone();
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads = new Long2ObjectLinkedOpenHashMap<>();
    private final List<ChunkGenerationTask> pendingGenerationTasks = new ArrayList<>();
    final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final Supplier<DimensionDataStorage> overworldDataStorage;
    private final PoiManager poiManager;
    /**
     * Chunks that have been requested to be unloaded, but haven't been unloaded yet.
     */
    final LongSet toDrop = new LongOpenHashSet();
    /**
     * True if changes have been made to {@link #loadedChunks} and thus a new copy of the collection has to be made into {@link #immutableLoadedChunks}.
     */
    private boolean modified;
    private final ChunkTaskPriorityQueueSorter queueSorter;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> mainThreadMailbox;
    private final ChunkProgressListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    private final ChunkMap.DistanceManager distanceManager;
    private final AtomicInteger tickingGenerated = new AtomicInteger();
    private final String storageName;
    private final PlayerMap playerMap = new PlayerMap();
    private final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = new Int2ObjectOpenHashMap<>();
    private final Long2ByteMap chunkTypeCache = new Long2ByteOpenHashMap();
    private final Long2LongMap chunkSaveCooldowns = new Long2LongOpenHashMap();
    private final Queue<Runnable> unloadQueue = Queues.newConcurrentLinkedQueue();
    private int serverViewDistance;
    private final WorldGenContext worldGenContext;

    public ChunkMap(
        ServerLevel level,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        DataFixer fixerUpper,
        StructureTemplateManager structureManager,
        Executor dispatcher,
        BlockableEventLoop<Runnable> mainThreadExecutor,
        LightChunkGetter lightChunk,
        ChunkGenerator generator,
        ChunkProgressListener progressListener,
        ChunkStatusUpdateListener chunkStatusListener,
        Supplier<DimensionDataStorage> overworldDataStorage,
        int viewDistance,
        boolean sync
    ) {
        super(
            new RegionStorageInfo(levelStorageAccess.getLevelId(), level.dimension(), "chunk"),
            levelStorageAccess.getDimensionPath(level.dimension()).resolve("region"),
            fixerUpper,
            sync
        );
        Path path = levelStorageAccess.getDimensionPath(level.dimension());
        this.storageName = path.getFileName().toString();
        this.level = level;
        RegistryAccess registryaccess = level.registryAccess();
        long i = level.getSeed();
        if (generator instanceof NoiseBasedChunkGenerator noisebasedchunkgenerator) {
            this.randomState = RandomState.create(noisebasedchunkgenerator.generatorSettings().value(), registryaccess.lookupOrThrow(Registries.NOISE), i);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), registryaccess.lookupOrThrow(Registries.NOISE), i);
        }

        this.chunkGeneratorState = generator.createState(registryaccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, i);
        this.mainThreadExecutor = mainThreadExecutor;
        ProcessorMailbox<Runnable> processormailbox1 = ProcessorMailbox.create(dispatcher, "worldgen");
        ProcessorHandle<Runnable> processorhandle = ProcessorHandle.of("main", mainThreadExecutor::tell);
        this.progressListener = progressListener;
        this.chunkStatusListener = chunkStatusListener;
        ProcessorMailbox<Runnable> processormailbox = ProcessorMailbox.create(dispatcher, "light");
        this.queueSorter = new ChunkTaskPriorityQueueSorter(
            ImmutableList.of(processormailbox1, processorhandle, processormailbox), dispatcher, Integer.MAX_VALUE
        );
        this.worldgenMailbox = this.queueSorter.getProcessor(processormailbox1, false);
        this.mainThreadMailbox = this.queueSorter.getProcessor(processorhandle, false);
        this.lightEngine = new ThreadedLevelLightEngine(
            lightChunk, this, this.level.dimensionType().hasSkyLight(), processormailbox, this.queueSorter.getProcessor(processormailbox, false)
        );
        this.distanceManager = new ChunkMap.DistanceManager(dispatcher, mainThreadExecutor);
        this.overworldDataStorage = overworldDataStorage;
        this.poiManager = new PoiManager(
            new RegionStorageInfo(levelStorageAccess.getLevelId(), level.dimension(), "poi"),
            path.resolve("poi"),
            fixerUpper,
            sync,
            registryaccess,
            level.getServer(),
            level
        );
        this.setServerViewDistance(viewDistance);
        this.worldGenContext = new WorldGenContext(level, generator, structureManager, this.lightEngine, this.mainThreadMailbox);
    }

    protected ChunkGenerator generator() {
        return this.worldGenContext.generator();
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    /**
     * Returns the squared distance to the center of the chunk.
     */
    private static double euclideanDistanceSquared(ChunkPos chunkPos, Entity entity) {
        double d0 = (double)SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double d1 = (double)SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double d2 = d0 - entity.getX();
        double d3 = d1 - entity.getZ();
        return d2 * d2 + d3 * d3;
    }

    /**
     * Checks if a chunk is within a player's view distance.
     */
    boolean isChunkTracked(ServerPlayer player, int x, int z) {
        return player.getChunkTrackingView().contains(x, z)
            && !player.connection.chunkSender.isPending(ChunkPos.asLong(x, z));
    }

    /**
     * Checks if a chunk is on the edge of the player's view distance.
     */
    private boolean isChunkOnTrackedBorder(ServerPlayer player, int x, int z) {
        if (!this.isChunkTracked(player, x, z)) {
            return false;
        } else {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if ((i != 0 || j != 0) && !this.isChunkTracked(player, x + i, z + j)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    protected ChunkHolder getUpdatingChunkIfPresent(long chunkPos) {
        return this.updatingChunkMap.get(chunkPos);
    }

    @Nullable
    public ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        return this.visibleChunkMap.get(chunkPos);
    }

    protected IntSupplier getChunkQueueLevel(long chunkPos) {
        return () -> {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(chunkPos);
            return chunkholder == null
                ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1
                : Math.min(chunkholder.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    public String getChunkDebugData(ChunkPos pos) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pos.toLong());
        if (chunkholder == null) {
            return "null";
        } else {
            String s = chunkholder.getTicketLevel() + "\n";
            ChunkStatus chunkstatus = chunkholder.getLatestStatus();
            ChunkAccess chunkaccess = chunkholder.getLatestChunk();
            if (chunkstatus != null) {
                s = s + "St: \u00a7" + chunkstatus.getIndex() + chunkstatus + "\u00a7r\n";
            }

            if (chunkaccess != null) {
                s = s + "Ch: \u00a7" + chunkaccess.getPersistedStatus().getIndex() + chunkaccess.getPersistedStatus() + "\u00a7r\n";
            }

            FullChunkStatus fullchunkstatus = chunkholder.getFullStatus();
            s = s + '\u00a7' + fullchunkstatus.ordinal() + fullchunkstatus;
            return s + "\u00a7r";
        }
    }

    private CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(ChunkHolder chunkHolder, int range, IntFunction<ChunkStatus> statusGetter) {
        if (range == 0) {
            ChunkStatus chunkstatus1 = statusGetter.apply(0);
            return chunkHolder.scheduleChunkGenerationTask(chunkstatus1, this).thenApply(p_329931_ -> p_329931_.map(List::of));
        } else {
            List<CompletableFuture<ChunkResult<ChunkAccess>>> list = new ArrayList<>();
            ChunkPos chunkpos = chunkHolder.getPos();

            for (int i = -range; i <= range; i++) {
                for (int j = -range; j <= range; j++) {
                    int k = Math.max(Math.abs(j), Math.abs(i));
                    long l = ChunkPos.asLong(chunkpos.x + j, chunkpos.z + i);
                    ChunkHolder chunkholder = this.getUpdatingChunkIfPresent(l);
                    if (chunkholder == null) {
                        return UNLOADED_CHUNK_LIST_FUTURE;
                    }

                    ChunkStatus chunkstatus = statusGetter.apply(k);
                    list.add(chunkholder.scheduleChunkGenerationTask(chunkstatus, this));
                }
            }

            return Util.sequence(list).thenApply(p_347038_ -> {
                List<ChunkAccess> list1 = Lists.newArrayList();

                for (ChunkResult<ChunkAccess> chunkresult : p_347038_) {
                    if (chunkresult == null) {
                        throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                    }

                    ChunkAccess chunkaccess = chunkresult.orElse(null);
                    if (chunkaccess == null) {
                        return UNLOADED_CHUNK_LIST_RESULT;
                    }

                    list1.add(chunkaccess);
                }

                return ChunkResult.of(list1);
            });
        }
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details) {
        StringBuilder stringbuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = p_347055_ -> p_347055_.getAllFutures()
                .forEach(
                    p_347043_ -> {
                        ChunkStatus chunkstatus = p_347043_.getFirst();
                        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = p_347043_.getSecond();
                        if (completablefuture != null && completablefuture.isDone() && completablefuture.join() == null) {
                            stringbuilder.append(p_347055_.getPos())
                                .append(" - status: ")
                                .append(chunkstatus)
                                .append(" future: ")
                                .append(completablefuture)
                                .append(System.lineSeparator());
                        }
                    }
                );
        stringbuilder.append("Updating:").append(System.lineSeparator());
        this.updatingChunkMap.values().forEach(consumer);
        stringbuilder.append("Visible:").append(System.lineSeparator());
        this.visibleChunkMap.values().forEach(consumer);
        CrashReport crashreport = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk loading");
        crashreportcategory.setDetail("Details", details);
        crashreportcategory.setDetail("Futures", stringbuilder);
        return new ReportedException(crashreport);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareEntityTickingChunk(ChunkHolder chunk) {
        return this.getChunkRangeFuture(chunk, 2, p_329942_ -> ChunkStatus.FULL)
            .thenApplyAsync(p_329945_ -> p_329945_.map(p_214939_ -> (LevelChunk)p_214939_.get(p_214939_.size() / 2)), this.mainThreadExecutor);
    }

    /**
     * Sets level and loads/unloads chunk.
     *
     * @param holder The {@link net.minecraft.server.level.ChunkHolder} of the chunk
     *               if it is loaded, and null otherwise.
     */
    @Nullable
    ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel) {
        if (!ChunkLevel.isLoaded(oldLevel) && !ChunkLevel.isLoaded(newLevel)) {
            return holder;
        } else {
            if (holder != null) {
                holder.setTicketLevel(newLevel);
            }

            if (holder != null) {
                if (!ChunkLevel.isLoaded(newLevel)) {
                    this.toDrop.add(chunkPos);
                } else {
                    this.toDrop.remove(chunkPos);
                }
            }

            if (ChunkLevel.isLoaded(newLevel) && holder == null) {
                holder = this.pendingUnloads.remove(chunkPos);
                if (holder != null) {
                    holder.setTicketLevel(newLevel);
                } else {
                    holder = new ChunkHolder(new ChunkPos(chunkPos), newLevel, this.level, this.lightEngine, this.queueSorter, this);
                }

                this.updatingChunkMap.put(chunkPos, holder);
                this.modified = true;
            }

            net.neoforged.neoforge.event.EventHooks.fireChunkTicketLevelUpdated(this.level, chunkPos, oldLevel, newLevel, holder);
            return holder;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.queueSorter.close();
            this.poiManager.close();
        } finally {
            super.close();
        }
    }

    protected void saveAllChunks(boolean flush) {
        if (flush) {
            List<ChunkHolder> list = this.visibleChunkMap
                .values()
                .stream()
                .filter(ChunkHolder::wasAccessibleSinceLastSave)
                .peek(ChunkHolder::refreshAccessibility)
                .toList();
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream()
                    .map(p_347059_ -> {
                        this.mainThreadExecutor.managedBlock(p_347059_::isReadyForSaving);
                        return p_347059_.getLatestChunk();
                    })
                    .filter(p_203088_ -> p_203088_ instanceof ImposterProtoChunk || p_203088_ instanceof LevelChunk)
                    .filter(this::save)
                    .forEach(p_203051_ -> mutableboolean.setTrue());
            } while (mutableboolean.isTrue());

            this.processUnloads(() -> true);
            this.flushWorker();
        } else {
            this.visibleChunkMap.values().forEach(this::saveChunkIfNeeded);
        }
    }

    protected void tick(BooleanSupplier hasMoreTime) {
        ProfilerFiller profilerfiller = this.level.getProfiler();
        profilerfiller.push("poi");
        this.poiManager.tick(hasMoreTime);
        profilerfiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(hasMoreTime);
        }

        profilerfiller.pop();
    }

    public boolean hasWork() {
        return this.lightEngine.hasLightWork()
            || !this.pendingUnloads.isEmpty()
            || !this.updatingChunkMap.isEmpty()
            || this.poiManager.hasWork()
            || !this.toDrop.isEmpty()
            || !this.unloadQueue.isEmpty()
            || this.queueSorter.hasWork()
            || this.distanceManager.hasTickets();
    }

    private void processUnloads(BooleanSupplier hasMoreTime) {
        LongIterator longiterator = this.toDrop.iterator();
        int i = 0;

        while (longiterator.hasNext() && (hasMoreTime.getAsBoolean() || i < 200 || this.toDrop.size() > 2000)) {
            long j = longiterator.nextLong();
            ChunkHolder chunkholder = this.updatingChunkMap.get(j);
            if (chunkholder != null) {
                if (chunkholder.getGenerationRefCount() != 0) {
                    continue;
                }

                this.updatingChunkMap.remove(j);
                this.pendingUnloads.put(j, chunkholder);
                this.modified = true;
                i++;
                this.scheduleUnload(j, chunkholder);
            }

            longiterator.remove();
        }

        int k = Math.max(0, this.unloadQueue.size() - 2000);

        Runnable runnable;
        while ((hasMoreTime.getAsBoolean() || k > 0) && (runnable = this.unloadQueue.poll()) != null) {
            k--;
            runnable.run();
        }

        int l = 0;
        ObjectIterator<ChunkHolder> objectiterator = this.visibleChunkMap.values().iterator();

        while (l < 20 && hasMoreTime.getAsBoolean() && objectiterator.hasNext()) {
            if (this.saveChunkIfNeeded(objectiterator.next())) {
                l++;
            }
        }
    }

    private void scheduleUnload(long chunkPos, ChunkHolder chunkHolder) {
        chunkHolder.getSaveSyncFuture().thenRunAsync(() -> {
            if (!chunkHolder.isReadyForSaving()) {
                this.scheduleUnload(chunkPos, chunkHolder);
            } else {
                ChunkAccess chunkaccess = chunkHolder.getLatestChunk();
                if (this.pendingUnloads.remove(chunkPos, chunkHolder) && chunkaccess != null) {
                    if (chunkaccess instanceof LevelChunk levelchunk) {
                        levelchunk.setLoaded(false);
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunkaccess));
                    }

                    this.save(chunkaccess);
                    if (chunkaccess instanceof LevelChunk levelchunk1) {
                        this.level.unload(levelchunk1);
                    }

                    net.neoforged.neoforge.common.CommonHooks.onChunkUnload(this.poiManager, chunkaccess); // Neo: Must be called for all chunk unloading. Not just LevelChunks.
                    this.chunkTypeCache.remove(chunkaccess.getPos().toLong()); // Neo: Prevent chunk type cache from permanently retaining data for unloaded chunks

                    this.lightEngine.updateChunkStatus(chunkaccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.progressListener.onStatusChange(chunkaccess.getPos(), null);
                    this.chunkSaveCooldowns.remove(chunkaccess.getPos().toLong());
                }
            }
        }, this.unloadQueue::add).whenComplete((p_347052_, p_347053_) -> {
            if (p_347053_ != null) {
                LOGGER.error("Failed to save chunk {}", chunkHolder.getPos(), p_347053_);
            }
        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        } else {
            this.visibleChunkMap = this.updatingChunkMap.clone();
            this.modified = false;
            return true;
        }
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos chunkPos) {
        return this.readChunk(chunkPos).thenApply(p_214925_ -> p_214925_.filter(p_214928_ -> {
                boolean flag = isChunkDataValid(p_214928_);
                if (!flag) {
                    LOGGER.error("Chunk file at {} is missing level data, skipping", chunkPos);
                }

                return flag;
            })).thenApplyAsync(p_351774_ -> {
            this.level.getProfiler().incrementCounter("chunkLoad");
            if (p_351774_.isPresent()) {
                ChunkAccess chunkaccess = ChunkSerializer.read(this.level, this.poiManager, this.storageInfo(), chunkPos, p_351774_.get());
                this.markPosition(chunkPos, chunkaccess.getPersistedStatus().getChunkType());
                return chunkaccess;
            } else {
                return this.createEmptyChunk(chunkPos);
            }
        }, this.mainThreadExecutor).exceptionallyAsync(p_329919_ -> this.handleChunkLoadFailure(p_329919_, chunkPos), this.mainThreadExecutor);
    }

    private static boolean isChunkDataValid(CompoundTag tag) {
        return tag.contains("Status", 8);
    }

    private ChunkAccess handleChunkLoadFailure(Throwable exception, ChunkPos chunkPos) {
        Throwable throwable = exception instanceof CompletionException completionexception ? completionexception.getCause() : exception;
        Throwable throwable1 = throwable instanceof ReportedException reportedexception ? reportedexception.getCause() : throwable;
        boolean flag1 = throwable1 instanceof Error;
        boolean flag = throwable1 instanceof IOException || throwable1 instanceof NbtException;
        if (!flag1) {
            if (!flag) {
            }

            this.level.getServer().reportChunkLoadFailure(throwable1, this.storageInfo(), chunkPos);
            return this.createEmptyChunk(chunkPos);
        } else {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Exception loading chunk");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk being loaded");
            crashreportcategory.setDetail("pos", chunkPos);
            this.markPositionReplaceable(chunkPos);
            throw new ReportedException(crashreport);
        }
    }

    private ChunkAccess createEmptyChunk(ChunkPos chunkPos) {
        this.markPositionReplaceable(chunkPos);
        return new ProtoChunk(chunkPos, UpgradeData.EMPTY, this.level, this.level.registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    private void markPositionReplaceable(ChunkPos chunkPos) {
        this.chunkTypeCache.put(chunkPos.toLong(), (byte)-1);
    }

    private byte markPosition(ChunkPos chunkPos, ChunkType chunkType) {
        return this.chunkTypeCache.put(chunkPos.toLong(), (byte)(chunkType == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long chunkPos) {
        ChunkHolder chunkholder = this.updatingChunkMap.get(chunkPos);
        chunkholder.increaseGenerationRefCount();
        return chunkholder;
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder chunk) {
        chunk.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache) {
        ChunkPos chunkpos = chunk.getPos();
        if (step.targetStatus() == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkpos);
        } else {
            try {
                GenerationChunkHolder generationchunkholder = cache.get(chunkpos.x, chunkpos.z);
                ChunkAccess chunkaccess = generationchunkholder.getChunkIfPresentUnchecked(step.targetStatus().getParent());
                if (chunkaccess == null) {
                    throw new IllegalStateException("Parent chunk missing");
                } else {
                    CompletableFuture<ChunkAccess> completablefuture = step.apply(this.worldGenContext, cache, chunkaccess);
                    this.progressListener.onStatusChange(chunkpos, step.targetStatus());
                    return completablefuture;
                }
            } catch (Exception exception) {
                exception.getStackTrace();
                CrashReport crashreport = CrashReport.forThrowable(exception, "Exception generating new chunk");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk to be generated");
                crashreportcategory.setDetail("Status being generated", () -> step.targetStatus().getName());
                crashreportcategory.setDetail("Location", String.format(Locale.ROOT, "%d,%d", chunkpos.x, chunkpos.z));
                crashreportcategory.setDetail("Position hash", ChunkPos.asLong(chunkpos.x, chunkpos.z));
                crashreportcategory.setDetail("Generator", this.generator());
                this.mainThreadExecutor.execute(() -> {
                    throw new ReportedException(crashreport);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos) {
        ChunkGenerationTask chunkgenerationtask = ChunkGenerationTask.create(this, targetStatus, pos);
        this.pendingGenerationTasks.add(chunkgenerationtask);
        return chunkgenerationtask;
    }

    private void runGenerationTask(ChunkGenerationTask task) {
        this.worldgenMailbox.tell(ChunkTaskPriorityQueueSorter.message(task.getCenter(), () -> {
            CompletableFuture<?> completablefuture = task.runUntilWait();
            if (completablefuture != null) {
                completablefuture.thenRun(() -> this.runGenerationTask(task));
            }
        }));
    }

    @Override
    public void runGenerationTasks() {
        this.pendingGenerationTasks.forEach(this::runGenerationTask);
        this.pendingGenerationTasks.clear();
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(ChunkHolder holder) {
        CompletableFuture<ChunkResult<List<ChunkAccess>>> completablefuture = this.getChunkRangeFuture(holder, 1, p_329920_ -> ChunkStatus.FULL);
        CompletableFuture<ChunkResult<LevelChunk>> completablefuture1 = completablefuture.<ChunkResult<LevelChunk>>thenApplyAsync(
                p_329912_ -> p_329912_.map(p_293806_ -> (LevelChunk)p_293806_.get(p_293806_.size() / 2)),
                p_347057_ -> this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, p_347057_))
            )
            .thenApplyAsync(p_329924_ -> p_329924_.ifSuccess(p_347050_ -> {
                    p_347050_.postProcessGeneration();
                    this.level.startTickingChunk(p_347050_);
                    CompletableFuture<?> completablefuture2 = holder.getSendSyncFuture();
                    if (completablefuture2.isDone()) {
                        this.onChunkReadyToSend(p_347050_);
                    } else {
                        completablefuture2.thenAcceptAsync(p_300774_ -> this.onChunkReadyToSend(p_347050_), this.mainThreadExecutor);
                    }
                }), this.mainThreadExecutor);
        completablefuture1.handle((p_331041_, p_287365_) -> {
            this.tickingGenerated.getAndIncrement();
            return null;
        });
        return completablefuture1;
    }

    private void onChunkReadyToSend(LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();

        for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
            if (serverplayer.getChunkTrackingView().contains(chunkpos)) {
                markChunkPendingToSend(serverplayer, chunk);
            }
        }
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(ChunkHolder holder) {
        return this.getChunkRangeFuture(holder, 1, ChunkLevel::getStatusAroundFullChunk)
            .thenApplyAsync(
                p_329940_ -> p_329940_.map(p_203092_ -> (LevelChunk)p_203092_.get(p_203092_.size() / 2)),
                p_347047_ -> this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(holder, p_347047_))
            );
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(ChunkHolder holder) {
        if (holder.wasAccessibleSinceLastSave() && holder.isReadyForSaving()) {
            ChunkAccess chunkaccess = holder.getLatestChunk();
            if (!(chunkaccess instanceof ImposterProtoChunk) && !(chunkaccess instanceof LevelChunk)) {
                return false;
            } else {
                long i = chunkaccess.getPos().toLong();
                long j = this.chunkSaveCooldowns.getOrDefault(i, -1L);
                long k = System.currentTimeMillis();
                if (k < j) {
                    return false;
                } else {
                    boolean flag = this.save(chunkaccess);
                    holder.refreshAccessibility();
                    if (flag) {
                        this.chunkSaveCooldowns.put(i, k + 10000L);
                    }

                    return flag;
                }
            }
        } else {
            return false;
        }
    }

    private boolean save(ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.isUnsaved()) {
            return false;
        } else {
            chunk.setUnsaved(false);
            ChunkPos chunkpos = chunk.getPos();

            try {
                ChunkStatus chunkstatus = chunk.getPersistedStatus();
                if (chunkstatus.getChunkType() != ChunkType.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkpos)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && chunk.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                this.level.getProfiler().incrementCounter("chunkSave");
                CompoundTag compoundtag = ChunkSerializer.write(this.level, chunk);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkDataEvent.Save(chunk, chunk.getLevel() != null ? chunk.getLevel() : this.level, compoundtag));
                this.write(chunkpos, compoundtag).exceptionally(p_351776_ -> {
                    this.level.getServer().reportChunkSaveFailure(p_351776_, this.storageInfo(), chunkpos);
                    return null;
                });
                this.markPosition(chunkpos, chunkstatus.getChunkType());
                return true;
            } catch (Exception exception) {
                this.level.getServer().reportChunkSaveFailure(exception, this.storageInfo(), chunkpos);
                return false;
            }
        }
    }

    private boolean isExistingChunkFull(ChunkPos chunkPos) {
        byte b0 = this.chunkTypeCache.get(chunkPos.toLong());
        if (b0 != 0) {
            return b0 == 1;
        } else {
            CompoundTag compoundtag;
            try {
                compoundtag = this.readChunk(chunkPos).join().orElse(null);
                if (compoundtag == null) {
                    this.markPositionReplaceable(chunkPos);
                    return false;
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to read chunk {}", chunkPos, exception);
                this.markPositionReplaceable(chunkPos);
                return false;
            }

            ChunkType chunktype = ChunkSerializer.getChunkTypeFromTag(compoundtag);
            return this.markPosition(chunkPos, chunktype) == 1;
        }
    }

    protected void setServerViewDistance(int viewDistance) {
        int i = Mth.clamp(viewDistance, 2, 32);
        if (i != this.serverViewDistance) {
            this.serverViewDistance = i;
            this.distanceManager.updatePlayerTickets(this.serverViewDistance);

            for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
                this.updateChunkTracking(serverplayer);
            }
        }
    }

    int getPlayerViewDistance(ServerPlayer player) {
        return Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos chunkPos) {
        LevelChunk levelchunk = this.getChunkToSend(chunkPos.toLong());
        if (levelchunk != null) {
            markChunkPendingToSend(player, levelchunk);
        }
    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        player.connection.chunkSender.markChunkPendingToSend(chunk);
        net.neoforged.neoforge.event.EventHooks.fireChunkWatch(player, chunk, player.serverLevel());
    }

    private static void dropChunk(ServerPlayer player, ChunkPos chunkPos) {
        net.neoforged.neoforge.event.EventHooks.fireChunkUnWatch(player, chunkPos, player.serverLevel());
        player.connection.chunkSender.dropChunk(player, chunkPos);
    }

    @Nullable
    public LevelChunk getChunkToSend(long chunkPos) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(chunkPos);
        return chunkholder == null ? null : chunkholder.getChunkToSend();
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    public net.minecraft.server.level.DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<ChunkHolder> getChunks() {
        return Iterables.unmodifiableIterable(this.visibleChunkMap.values());
    }

    void dumpChunks(Writer writer) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("z")
            .addColumn("level")
            .addColumn("in_memory")
            .addColumn("status")
            .addColumn("full_status")
            .addColumn("accessible_ready")
            .addColumn("ticking_ready")
            .addColumn("entity_ticking_ready")
            .addColumn("ticket")
            .addColumn("spawning")
            .addColumn("block_entity_count")
            .addColumn("ticking_ticket")
            .addColumn("ticking_level")
            .addColumn("block_ticks")
            .addColumn("fluid_ticks")
            .build(writer);
        TickingTracker tickingtracker = this.distanceManager.tickingTracker();

        for (Entry<ChunkHolder> entry : this.visibleChunkMap.long2ObjectEntrySet()) {
            long i = entry.getLongKey();
            ChunkPos chunkpos = new ChunkPos(i);
            ChunkHolder chunkholder = entry.getValue();
            Optional<ChunkAccess> optional = Optional.ofNullable(chunkholder.getLatestChunk());
            Optional<LevelChunk> optional1 = optional.flatMap(
                p_214932_ -> p_214932_ instanceof LevelChunk ? Optional.of((LevelChunk)p_214932_) : Optional.empty()
            );
            csvoutput.writeRow(
                chunkpos.x,
                chunkpos.z,
                chunkholder.getTicketLevel(),
                optional.isPresent(),
                optional.map(ChunkAccess::getPersistedStatus).orElse(null),
                optional1.map(LevelChunk::getFullStatus).orElse(null),
                printFuture(chunkholder.getFullChunkFuture()),
                printFuture(chunkholder.getTickingChunkFuture()),
                printFuture(chunkholder.getEntityTickingChunkFuture()),
                this.distanceManager.getTicketDebugString(i),
                this.anyPlayerCloseEnoughForSpawning(chunkpos),
                optional1.<Integer>map(p_214953_ -> p_214953_.getBlockEntities().size()).orElse(0),
                tickingtracker.getTicketDebugString(i),
                tickingtracker.getLevel(i),
                optional1.<Integer>map(p_214946_ -> p_214946_.getBlockTicks().count()).orElse(0),
                optional1.<Integer>map(p_214937_ -> p_214937_.getFluidTicks().count()).orElse(0)
            );
        }
    }

    private static String printFuture(CompletableFuture<ChunkResult<LevelChunk>> future) {
        try {
            ChunkResult<LevelChunk> chunkresult = future.getNow(null);
            if (chunkresult != null) {
                return chunkresult.isSuccess() ? "done" : "unloaded";
            } else {
                return "not completed";
            }
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos pos) {
        return this.read(pos).thenApplyAsync(p_214907_ -> p_214907_.map(this::upgradeChunkTag), Util.backgroundExecutor());
    }

    private CompoundTag upgradeChunkTag(CompoundTag tag) {
        return this.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, tag, this.generator().getTypeNameForDataFixer());
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos) {
        if (!this.distanceManager.hasPlayersNearby(chunkPos.toLong())) {
            return false;
        } else {
            for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
                if (this.playerIsCloseEnoughForSpawning(serverplayer, chunkPos)) {
                    return true;
                }
            }

            return false;
        }
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos chunkPos) {
        long i = chunkPos.toLong();
        if (!this.distanceManager.hasPlayersNearby(i)) {
            return List.of();
        } else {
            Builder<ServerPlayer> builder = ImmutableList.builder();

            for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
                if (this.playerIsCloseEnoughForSpawning(serverplayer, chunkPos)) {
                    builder.add(serverplayer);
                }
            }

            return builder.build();
        }
    }

    private boolean playerIsCloseEnoughForSpawning(ServerPlayer player, ChunkPos chunkPos) {
        if (player.isSpectator()) {
            return false;
        } else {
            double d0 = euclideanDistanceSquared(chunkPos, player);
            return d0 < 16384.0;
        }
    }

    private boolean skipPlayer(ServerPlayer player) {
        return player.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(ServerPlayer player, boolean track) {
        boolean flag = this.skipPlayer(player);
        boolean flag1 = this.playerMap.ignoredOrUnknown(player);
        if (track) {
            this.playerMap.addPlayer(player, flag);
            this.updatePlayerPos(player);
            if (!flag) {
                this.distanceManager.addPlayer(SectionPos.of(player), player);
            }

            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            this.updateChunkTracking(player);
        } else {
            SectionPos sectionpos = player.getLastSectionPos();
            this.playerMap.removePlayer(player);
            if (!flag1) {
                this.distanceManager.removePlayer(sectionpos, player);
            }

            this.applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
        }
    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionpos = SectionPos.of(player);
        player.setLastSectionPos(sectionpos);
    }

    public void move(ServerPlayer player) {
        for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
            if (chunkmap$trackedentity.entity == player) {
                chunkmap$trackedentity.updatePlayers(this.level.players());
            } else {
                chunkmap$trackedentity.updatePlayer(player);
            }
        }

        SectionPos sectionpos = player.getLastSectionPos();
        SectionPos sectionpos1 = SectionPos.of(player);
        boolean flag = this.playerMap.ignored(player);
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = sectionpos.asLong() != sectionpos1.asLong();
        if (flag2 || flag != flag1) {
            this.updatePlayerPos(player);
            if (!flag) {
                this.distanceManager.removePlayer(sectionpos, player);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionpos1, player);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(player);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(player);
            }

            //PATCH 1.20.2: Figure out the firing of the watch and unwatch events when chunk tracking updates.
            this.updateChunkTracking(player);
        }
    }

    private void updateChunkTracking(ServerPlayer player) {
        ChunkPos chunkpos = player.chunkPosition();
        int i = this.getPlayerViewDistance(player);
        if (player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned chunktrackingview$positioned
            && chunktrackingview$positioned.center().equals(chunkpos)
            && chunktrackingview$positioned.viewDistance() == i) {
            return;
        }

        this.applyChunkTrackingView(player, ChunkTrackingView.of(chunkpos, i));
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkTrackingView) {
        if (player.level() == this.level) {
            ChunkTrackingView chunktrackingview = player.getChunkTrackingView();
            if (chunkTrackingView instanceof ChunkTrackingView.Positioned chunktrackingview$positioned
                && (
                    !(chunktrackingview instanceof ChunkTrackingView.Positioned chunktrackingview$positioned1)
                        || !chunktrackingview$positioned1.center().equals(chunktrackingview$positioned.center())
                )) {
                player.connection
                    .send(new ClientboundSetChunkCacheCenterPacket(chunktrackingview$positioned.center().x, chunktrackingview$positioned.center().z));
            }

            ChunkTrackingView.difference(
                chunktrackingview, chunkTrackingView, p_293802_ -> this.markChunkPendingToSend(player, p_293802_), p_293800_ -> dropChunk(player, p_293800_)
            );
            player.setChunkTrackingView(chunkTrackingView);
        }
    }

    /**
     * Returns the players tracking the given chunk.
     */
    @Override
    public List<ServerPlayer> getPlayers(ChunkPos pos, boolean boundaryOnly) {
        Set<ServerPlayer> set = this.playerMap.getAllPlayers();
        Builder<ServerPlayer> builder = ImmutableList.builder();

        for (ServerPlayer serverplayer : set) {
            if (boundaryOnly && this.isChunkOnTrackedBorder(serverplayer, pos.x, pos.z)
                || !boundaryOnly && this.isChunkTracked(serverplayer, pos.x, pos.z)) {
                builder.add(serverplayer);
            }
        }

        return builder.build();
    }

    protected void addEntity(Entity entity) {
        if (!(entity instanceof net.neoforged.neoforge.entity.PartEntity)) {
            EntityType<?> entitytype = entity.getType();
            int i = entitytype.clientTrackingRange() * 16;
            if (i != 0) {
                int j = entitytype.updateInterval();
                if (this.entityMap.containsKey(entity.getId())) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    ChunkMap.TrackedEntity chunkmap$trackedentity = new ChunkMap.TrackedEntity(entity, i, j, entitytype.trackDeltas());
                    this.entityMap.put(entity.getId(), chunkmap$trackedentity);
                    chunkmap$trackedentity.updatePlayers(this.level.players());
                    if (entity instanceof ServerPlayer serverplayer) {
                        this.updatePlayerStatus(serverplayer, true);

                        for (ChunkMap.TrackedEntity chunkmap$trackedentity1 : this.entityMap.values()) {
                            if (chunkmap$trackedentity1.entity != serverplayer) {
                                chunkmap$trackedentity1.updatePlayer(serverplayer);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        if (entity instanceof ServerPlayer serverplayer) {
            this.updatePlayerStatus(serverplayer, false);

            for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
                chunkmap$trackedentity.removePlayer(serverplayer);
            }
        }

        ChunkMap.TrackedEntity chunkmap$trackedentity1 = this.entityMap.remove(entity.getId());
        if (chunkmap$trackedentity1 != null) {
            chunkmap$trackedentity1.broadcastRemoved();
        }
    }

    protected void tick() {
        for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
            this.updateChunkTracking(serverplayer);
        }

        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list1 = this.level.players();

        for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
            SectionPos sectionpos = chunkmap$trackedentity.lastSectionPos;
            SectionPos sectionpos1 = SectionPos.of(chunkmap$trackedentity.entity);
            boolean flag = !Objects.equals(sectionpos, sectionpos1);
            if (flag) {
                chunkmap$trackedentity.updatePlayers(list1);
                Entity entity = chunkmap$trackedentity.entity;
                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer)entity);
                }

                chunkmap$trackedentity.lastSectionPos = sectionpos1;
            }

            if (flag || this.distanceManager.inEntityTickingRange(sectionpos1.chunk().toLong())) {
                chunkmap$trackedentity.serverEntity.sendChanges();
            }
        }

        if (!list.isEmpty()) {
            for (ChunkMap.TrackedEntity chunkmap$trackedentity1 : this.entityMap.values()) {
                chunkmap$trackedentity1.updatePlayers(list);
            }
        }
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity chunkmap$trackedentity = this.entityMap.get(entity.getId());
        if (chunkmap$trackedentity != null) {
            chunkmap$trackedentity.broadcast(packet);
        }
    }

    protected void broadcastAndSend(Entity entity, Packet<?> packet) {
        ChunkMap.TrackedEntity chunkmap$trackedentity = this.entityMap.get(entity.getId());
        if (chunkmap$trackedentity != null) {
            chunkmap$trackedentity.broadcastAndSend(packet);
        }
    }

    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap<>();

        for (ChunkAccess chunkaccess : chunks) {
            ChunkPos chunkpos = chunkaccess.getPos();
            LevelChunk levelchunk;
            if (chunkaccess instanceof LevelChunk levelchunk1) {
                levelchunk = levelchunk1;
            } else {
                levelchunk = this.level.getChunk(chunkpos.x, chunkpos.z);
            }

            for (ServerPlayer serverplayer : this.getPlayers(chunkpos, false)) {
                map.computeIfAbsent(serverplayer, p_274834_ -> new ArrayList<>()).add(levelchunk);
            }
        }

        map.forEach((p_293803_, p_293804_) -> p_293803_.connection.send(ClientboundChunksBiomesPacket.forChunks((List<LevelChunk>)p_293804_)));
    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus fullChunkStatus) {
        this.chunkStatusListener.onChunkStatusChange(chunkPos, fullChunkStatus);
    }

    public void waitForLightBeforeSending(ChunkPos chunkPos, int range) {
        int i = range + 1;
        ChunkPos.rangeClosed(chunkPos, i).forEach(p_300775_ -> {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(p_300775_.toLong());
            if (chunkholder != null) {
                chunkholder.addSendDependency(this.lightEngine.waitForPendingTasks(p_300775_.x, p_300775_.z));
            }
        });
    }

    // Neo: Getter for players watching an entity.
    public List<ServerPlayer> getPlayersWatching(Entity entity) {
        var trackedEntity = entityMap.get(entity.getId());
        if (trackedEntity != null) {
            var ret = new java.util.ArrayList<ServerPlayer>(trackedEntity.seenBy.size());
            for (var connection : trackedEntity.seenBy) {
                ret.add(connection.getPlayer());
            }
            return java.util.Collections.unmodifiableList(ret);
        } else {
            return List.of();
        }
    }

    class DistanceManager extends net.minecraft.server.level.DistanceManager {
        protected DistanceManager(Executor dispatcher, Executor mainThreadExecutor) {
            super(dispatcher, mainThreadExecutor);
        }

        @Override
        protected boolean isChunkToRemove(long chunkPos) {
            return ChunkMap.this.toDrop.contains(chunkPos);
        }

        @Nullable
        @Override
        protected ChunkHolder getChunk(long chunkPos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(chunkPos);
        }

        @Nullable
        @Override
        protected ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel) {
            return ChunkMap.this.updateChunkScheduling(chunkPos, newLevel, holder, oldLevel);
        }
    }

    class TrackedEntity {
        final ServerEntity serverEntity;
        final Entity entity;
        private final int range;
        SectionPos lastSectionPos;
        private final Set<ServerPlayerConnection> seenBy = Sets.newIdentityHashSet();

        public TrackedEntity(Entity entity, int range, int updateInterval, boolean trackDelta) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, updateInterval, trackDelta, this::broadcast);
            this.entity = entity;
            this.range = range;
            this.lastSectionPos = SectionPos.of(entity);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChunkMap.TrackedEntity ? ((ChunkMap.TrackedEntity)other).entity.getId() == this.entity.getId() : false;
        }

        @Override
        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> packet) {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                serverplayerconnection.send(packet);
            }
        }

        public void broadcastAndSend(Packet<?> packet) {
            this.broadcast(packet);
            if (this.entity instanceof ServerPlayer) {
                ((ServerPlayer)this.entity).connection.send(packet);
            }
        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                this.serverEntity.removePairing(serverplayerconnection.getPlayer());
            }
        }

        public void removePlayer(ServerPlayer player) {
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
            }
        }

        public void updatePlayer(ServerPlayer player) {
            if (player != this.entity) {
                Vec3 vec3 = player.position().subtract(this.entity.position());
                int i = ChunkMap.this.getPlayerViewDistance(player);
                double d0 = (double)Math.min(this.getEffectiveRange(), i * 16);
                double d1 = vec3.x * vec3.x + vec3.z * vec3.z;
                double d2 = d0 * d0;
                boolean flag = d1 <= d2
                    && this.entity.broadcastToPlayer(player)
                    && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition().x, this.entity.chunkPosition().z);
                if (flag) {
                    if (this.seenBy.add(player.connection)) {
                        this.serverEntity.addPairing(player);
                    }
                } else if (this.seenBy.remove(player.connection)) {
                    this.serverEntity.removePairing(player);
                }
            }
        }

        private int scaledRange(int trackingDistance) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(trackingDistance);
        }

        private int getEffectiveRange() {
            int i = this.range;

            for (Entity entity : this.entity.getIndirectPassengers()) {
                int j = entity.getType().clientTrackingRange() * 16;
                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> playersList) {
            for (ServerPlayer serverplayer : playersList) {
                this.updatePlayer(serverplayer);
            }
        }
    }

    /**
     * Neo: PR #937
     * This is for mainly pre-generation usage such as Neoforge's generate command.
     * Use this to schedule chunk load tasks into ChunkTaskPriorityQueueSorter so a chunk is fully finished all of their tasks before scheduling more chunks to load.
     * Reason for this is when scheduling a huge ton of Full Status chunk tasks to the server (to load chunks),
     * you could cause the server to only process those loading tasks and never reach the two chunk tasks that are
     * automatically scheduled to run after the chunk is loaded to Full. As a result of flooding the system with Full Status chunk tasks,
     * the queue for the two kind of successor chunk tasks will grow and become a memory leak of lambdas and chunk references.
     * Use this method to schedule tasks for loading chunks in your whenCompleteAsync method call so the tasks gets processed properly over time and not leak.
     * See {@link net.neoforged.neoforge.server.command.generation.GenerationTask#enqueueChunks} as an example usage of this method.
     */
    public void scheduleOnMainThreadMailbox(ChunkTaskPriorityQueueSorter.Message<Runnable> msg) {
        mainThreadMailbox.tell(msg);
    }
}
