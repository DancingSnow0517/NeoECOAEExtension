package cn.dancingsnow.neoecoae.client.multiblock.preview;

record SceneBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static SceneBounds full(MultiblockPreviewScene scene) {
        return new SceneBounds(scene.minX(), scene.minY(), scene.minZ(), scene.maxX(), scene.maxY(), scene.maxZ());
    }

    int sizeX() {
        return maxX - minX + 1;
    }

    int sizeY() {
        return maxY - minY + 1;
    }

    int sizeZ() {
        return maxZ - minZ + 1;
    }

    int maxDimension() {
        return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
    }

    float centerX() {
        return (minX + maxX + 1.0F) * 0.5F;
    }

    float centerY() {
        return (minY + maxY + 1.0F) * 0.5F;
    }

    float centerZ() {
        return (minZ + maxZ + 1.0F) * 0.5F;
    }
}
