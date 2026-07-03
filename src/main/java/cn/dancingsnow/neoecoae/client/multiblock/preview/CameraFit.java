package cn.dancingsnow.neoecoae.client.multiblock.preview;

final class CameraFit {
    private CameraFit() {}

    /**
     * Calculate a stable scale based solely on the scene's bounding sphere radius,
     * independent of yaw/pitch. This prevents scale breathing during rotation.
     */
    static float calculateStableScale(SceneBounds bounds, int width, int height, float padding) {
        float sizeX = bounds.sizeX();
        float sizeY = bounds.sizeY();
        float sizeZ = bounds.sizeZ();
        float radius = (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ) * 0.5F;
        float scale = Math.min(width, height) * padding / Math.max(1.0F, radius * 2.0F);
        return scale;
    }

}
