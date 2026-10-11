package cn.dancingsnow.neoecoae.gui.json;

/** Raised when a NeoECOAE UI definition is malformed or uses an unsupported type. */
public final class NeoUiJsonException extends RuntimeException {
    public NeoUiJsonException(String message) {
        super(message);
    }

    public NeoUiJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
