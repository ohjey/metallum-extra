package com.metallumextra.shader.pack;

/** A shader pack that cannot be used; the message says why, in words for the player. */
public final class PackException extends Exception {
    public PackException(final String message) {
        super(message);
    }

    public PackException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
