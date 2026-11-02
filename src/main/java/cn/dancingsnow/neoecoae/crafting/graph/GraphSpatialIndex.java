package cn.dancingsnow.neoecoae.crafting.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable bounds tree. Query cost does not grow with empty world-space grid cells at small zooms. */
final class GraphSpatialIndex<T> {
    record Entry<T>(GraphLayoutSnapshot.Bounds bounds, T value) {}

    private record Branch(GraphLayoutSnapshot.Bounds bounds, int from, int to, Branch left, Branch right) {}

    private final List<Entry<T>> entries;
    private final Branch root;

    GraphSpatialIndex(List<Entry<T>> entries) {
        this.entries = new ArrayList<>(entries);
        root = entries.isEmpty() ? null : build(0, entries.size());
    }

    List<T> query(GraphLayoutSnapshot.Bounds area, int limit) {
        if (limit <= 0 || root == null) return List.of();
        List<T> result = new ArrayList<>();
        query(root, area, limit, result);
        return List.copyOf(result);
    }

    int count(GraphLayoutSnapshot.Bounds area) {
        return root == null ? 0 : count(root, area);
    }

    private int count(Branch branch, GraphLayoutSnapshot.Bounds area) {
        var bounds = branch.bounds();
        if (!bounds.intersects(area)) return 0;
        if (area.left() <= bounds.left() && area.top() <= bounds.top()
                && area.right() >= bounds.right() && area.bottom() >= bounds.bottom()) return branch.to() - branch.from();
        if (branch.left() != null) return count(branch.left(), area) + count(branch.right(), area);
        int count = 0;
        for (int i = branch.from(); i < branch.to(); i++) if (entries.get(i).bounds().intersects(area)) count++;
        return count;
    }

    private Branch build(int from, int to) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int i = from; i < to; i++) {
            var bounds = entries.get(i).bounds();
            left = Math.min(left, bounds.left());
            top = Math.min(top, bounds.top());
            right = Math.max(right, bounds.right());
            bottom = Math.max(bottom, bounds.bottom());
        }
        var bounds = new GraphLayoutSnapshot.Bounds(left, top, right, bottom);
        if (to - from <= 8) return new Branch(bounds, from, to, null, null);
        boolean horizontal = bounds.width() >= bounds.height();
        entries.subList(from, to).sort(Comparator.comparingDouble(entry -> horizontal
            ? entry.bounds().left() + entry.bounds().right() : entry.bounds().top() + entry.bounds().bottom()));
        int middle = (from + to) >>> 1;
        return new Branch(bounds, from, to, build(from, middle), build(middle, to));
    }

    private void query(Branch branch, GraphLayoutSnapshot.Bounds area, int limit, List<T> result) {
        if (result.size() >= limit || !branch.bounds().intersects(area)) return;
        if (branch.left() == null) {
            for (int i = branch.from(); i < branch.to() && result.size() < limit; i++) {
                var entry = entries.get(i);
                if (entry.bounds().intersects(area)) result.add(entry.value());
            }
        } else {
            query(branch.left(), area, limit, result);
            query(branch.right(), area, limit, result);
        }
    }
}
