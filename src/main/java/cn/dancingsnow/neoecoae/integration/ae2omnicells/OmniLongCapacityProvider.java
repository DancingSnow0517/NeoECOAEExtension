package cn.dancingsnow.neoecoae.integration.ae2omnicells;

/**
 * Supplies the real byte capacity of an Omni cell when it cannot be represented by OmniCells' int API.
 */
public interface OmniLongCapacityProvider {
    long getOmniTotalBytes();
}
