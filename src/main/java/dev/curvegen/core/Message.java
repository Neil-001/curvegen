package dev.curvegen.core;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** A translation key and its arguments, independent of the game and the current language. */
public record Message(String key, List<Object> args) implements Serializable {
    private static final long serialVersionUID = 1L;
    public Message {
        Objects.requireNonNull(key);
        args = List.copyOf(args);
    }

    public static Message of(String key, Object... args) {
        return new Message(key, List.of(args));
    }
}
