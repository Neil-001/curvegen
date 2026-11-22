package dev.curvegen.core;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

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
