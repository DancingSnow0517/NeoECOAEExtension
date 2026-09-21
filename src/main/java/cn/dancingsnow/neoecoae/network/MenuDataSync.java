package cn.dancingsnow.neoecoae.network;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** One bounded fragment per menu per server tick, with a client nonce for each subscription. */
public final class MenuDataSync {
    public static final int EXACT = 1, REPORT = 2, GRAPH = 3;
    private UUID session = UUID.randomUUID();
    private final Map<Integer, Supplier<byte[]>> sources = new HashMap<>();
    private final Set<Integer> asynchronous = new HashSet<>();
    private static final ThreadPoolExecutor ENCODER =
            new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "neoecoae-sync-encoder");
                thread.setDaemon(true);
                return thread;
            });
    private CompletableFuture<byte[]> encoding;
    private int encodingKind;
    private final Map<Integer, UUID> requests = new HashMap<>();
    private final Map<Integer, BoundedData.Receiver> receivers = new HashMap<>();
    private boolean announced;
    private Pending pending;
    private long lastTick = Long.MIN_VALUE;
    private final ArrayDeque<Integer> requestedKinds = new ArrayDeque<>();
    private String error = "";
    private boolean closed;

    public void source(int kind, Supplier<byte[]> source) {
        sources.put(kind, source);
    }

    /** Only immutable snapshots may be captured by this supplier. No world/menu reads on the worker. */
    public void asynchronousSource(int kind, Supplier<byte[]> source) {
        source(kind, source);
        asynchronous.add(kind);
    }

    public void invalidate() {
        session = UUID.randomUUID();
        announced = false;
        pending = null;
        requests.clear();
        requestedKinds.clear();
        error = "";
        close();
        closed = false;
    }

    public void close() {
        closed = true;
        pending = null;
        requestedKinds.clear();
        if (encoding != null) encoding.cancel(false);
        encoding = null;
        receivers.clear();
    }

    public boolean subscribed(int kind) {
        return requests.containsKey(kind);
    }

    public boolean busy() {
        return pending != null || !requestedKinds.isEmpty() || encoding != null;
    }

    public String error() {
        return error;
    }

    public void requestFromClient(AbstractContainerMenu menu, int kind) {
        MenuDataRequest request = request(menu.containerId, kind);
        if (request != null) ECOPlannerNetwork.sendToServer(request);
    }

    MenuDataRequest request(int containerId, int kind) {
        if (closed || requests.containsKey(kind)) return null;
        UUID request = UUID.randomUUID();
        requests.put(kind, request);
        error = "";
        return new MenuDataRequest(containerId, session, request, kind);
    }

    public void acceptRequest(MenuDataRequest request) {
        if (closed
                || !session.equals(request.session())
                || !sources.containsKey(request.kind())
                || requests.containsKey(request.kind())) return;
        requests.put(request.kind(), request.request());
        requestedKinds.add(request.kind());
    }

    public void queue(int kind, byte[] data, Runnable completed) {
        if (closed || pending != null || encoding != null || !subscribed(kind))
            throw new IllegalStateException("Sync is not ready");
        if (data.length < 1 || data.length > BoundedData.MAX_BYTES)
            throw new IllegalArgumentException("Sync exceeds budget");
        pending = new Pending(kind, data, completed);
    }

    public void tick(ServerPlayer player, AbstractContainerMenu menu, int autoKind) {
        if (player.containerMenu != menu) {
            close();
            return;
        }
        var part = poll(menu.containerId, player.serverLevel().getGameTime(), autoKind);
        if (part != null) ECOPlannerNetwork.sendToPlayer(player, part);
    }

    MenuDataFragment poll(int containerId, long tick, int autoKind) {
        if (closed || lastTick == tick) return null;
        lastTick = tick;
        if (!announced) {
            announced = true;
            return new MenuDataFragment(containerId, session, session, -autoKind, 0, 0, new byte[0]);
        }
        if (!requestedKinds.isEmpty() && pending == null && encoding == null) {
            int kind = requestedKinds.remove();
            try {
                if (asynchronous.contains(kind)) {
                    encodingKind = kind;
                    encoding = CompletableFuture.supplyAsync(sources.get(kind), ENCODER);
                } else queue(kind, sources.get(kind).get(), () -> {});
            } catch (RuntimeException failure) {
                return failure(containerId, kind);
            }
        }
        if (encoding != null && encoding.isDone()) {
            var completed = encoding;
            encoding = null;
            try {
                queue(encodingKind, completed.join(), () -> {});
            } catch (RuntimeException failure) {
                return failure(containerId, encodingKind);
            }
        }
        if (pending == null) return null;
        Pending next = pending;
        int end = Math.min(next.offset + BoundedData.PART_BYTES, next.data.length);
        var part = new MenuDataFragment(
                containerId,
                session,
                requests.get(next.kind),
                next.kind,
                next.data.length,
                next.offset,
                Arrays.copyOfRange(next.data, next.offset, end));
        next.offset = end;
        if (end == next.data.length) {
            pending = null;
            next.completed.run();
        }
        return part;
    }

    public void fail(ServerPlayer player, AbstractContainerMenu menu, int kind) {
        ECOPlannerNetwork.sendToPlayer(player, failure(menu.containerId, kind));
    }

    private MenuDataFragment failure(int containerId, int kind) {
        pending = null;
        error = "gui.neoecoae.sync.too_large";
        return new MenuDataFragment(containerId, session, requests.get(kind), kind, -1, 0, new byte[0]);
    }

    public void receive(AbstractContainerMenu menu, NetworkMenu target, MenuDataFragment part) {
        receive(menu.containerId, target, part, ECOPlannerNetwork::sendToServer, ignored -> notifyClientError());
    }

    void receive(
            int containerId,
            NetworkMenu target,
            MenuDataFragment part,
            Consumer<MenuDataRequest> sendRequest,
            Consumer<String> onError) {
        if (closed || containerId != part.containerId()) return;
        if (part.kind() < 0) {
            if (announced && session.equals(part.session())) return;
            session = part.session();
            announced = true;
            requests.clear();
            receivers.clear();
            error = "";
            target.neoecoae$resetData();
            sendRequest.accept(request(containerId, -part.kind()));
            return;
        }
        if (!session.equals(part.session()) || !part.request().equals(requests.get(part.kind()))) return;
        if (part.total() == -1) {
            receivers.remove(part.kind());
            error = "gui.neoecoae.sync.too_large";
            if (part.kind() == EXACT) target.neoecoae$resetData();
            onError.accept(error);
            return;
        }
        byte[] data = receivers
                .computeIfAbsent(part.kind(), ignored -> new BoundedData.Receiver())
                .accept(part.total(), part.offset(), part.data());
        if (data != null) target.neoecoae$receiveData(part.kind(), data);
    }

    private static final class Client {
        static void notifyError() {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null)
                player.displayClientMessage(Component.translatable("gui.neoecoae.sync.too_large"), false);
        }
    }

    public static void notifyClientError() {
        Client.notifyError();
    }

    private static final class Pending {
        final int kind;
        final byte[] data;
        final Runnable completed;
        int offset;

        Pending(int kind, byte[] data, Runnable completed) {
            this.kind = kind;
            this.data = data;
            this.completed = completed;
        }
    }
}
