package cn.dancingsnow.neoecoae.api.me.menu;

/** Server-authoritative actions for ECO bulk markers in the AE2 cell workbench. */
public interface ECOBulkMarkerMenu {
    boolean neoecoae$canRemoveMarker(int slot);
    void neoecoae$editBulkMarker(MarkerEdit edit);

    // The exact key is encoded as SNBT so AE2's plain Gson action transport never serializes registry objects.
    record MarkerEdit(int slot, String expectedMarker, int action) {}
}
