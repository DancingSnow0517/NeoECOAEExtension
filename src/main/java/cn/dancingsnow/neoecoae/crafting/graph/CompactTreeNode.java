package cn.dancingsnow.neoecoae.crafting.graph;

/** Client-only metadata for one visible tree occurrence or collapsed subtree. */
public record CompactTreeNode(
        int id,
        int sourceId,
        int parentId,
        String path,
        int depth,
        boolean folder,
        boolean reference,
        int hiddenNodeCount,
        int hiddenDepth,
        boolean containsMissing,
        boolean containsCycle,
        boolean containsUnsupported,
        boolean sharedBatch
) {
    public boolean hasHiddenContent() {
        return folder && hiddenNodeCount > 0;
    }

    CompactTreeNode withSharedBatch() {
        return new CompactTreeNode(id, sourceId, parentId, path, depth, folder, reference, hiddenNodeCount,
            hiddenDepth, containsMissing, containsCycle, containsUnsupported, true);
    }
}
