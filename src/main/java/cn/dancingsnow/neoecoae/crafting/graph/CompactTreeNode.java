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
        boolean containsUnsupported
) implements cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection.TreeNode {
    public boolean hasHiddenContent() {
        return folder && hiddenNodeCount > 0;
    }
}
