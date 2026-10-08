package dev.curvegen.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Which of a server's chat lines answer the {@code /setblock} commands the mod sent itself, so the client can keep
 * them out of chat. A line is known by its translation key. The server answers commands in the order they were
 * sent, and this keeps the positions of the ones still unanswered in that order. A success is the mod's only if it
 * names one of those positions. An error names nothing, so one that {@code /setblock} can give counts as the answer
 * to the oldest command. The player's own failed {@code /setblock} in the same few seconds can't be told apart
 * from the mod's and is hidden too.
 */
public final class CommandReplies {
    public static final String SUCCESS = "commands.setblock.success", FAILED = "commands.setblock.failed", HERE = "command.context.here";
    /** What an error's key starts with when {@code /setblock} can give it: no such command without permission, a position the server won't take, a block it doesn't know. */
    private static final String[] ERRORS = {"command.unknown.", "argument.pos.", "argument.block.", "argument.id."};
    /** Ticks without a command sent or a reply seen before the rest are given up on, and how long the last error's second line may take. */
    static final int TIMEOUT = 200, GRACE = 40;

    private record Pos(int x, int y, int z) {}
    private final ArrayDeque<Pos> sent = new ArrayDeque<>();
    /** The errors already shown since the commands started. */
    private final Set<String> shown = new HashSet<>();
    private int idle, grace;

    /** Call for each command as it's sent. */
    public void sent(int x, int y, int z) {
        if (sent.isEmpty() && grace == 0) shown.clear();
        sent.add(new Pos(x, y, z));
        idle = 0;
    }

    /** Call once a client tick. */
    public void tick() {
        if (!sent.isEmpty()) {
            if (++idle > TIMEOUT) sent.clear();
        } else if (grace > 0) grace--;
    }

    /** How many commands haven't been answered yet. */
    public int awaiting() { return sent.size(); }

    /**
     * Whether to hide a line that says a command worked, by its translation key. Only "Changed the block" at a
     * position one of the mod's unanswered commands went to. The commands sent before that one have been answered too.
     */
    public boolean hidesSuccess(String key, int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        if (!SUCCESS.equals(key) || !sent.contains(p)) return false;
        while (!sent.remove().equals(p)) { }
        answered();
        return true;
    }

    /**
     * Whether to hide a line that says a command failed, by its translation key. "Could not set the block" is
     * hidden. Any other error {@code /setblock} can give, such as a position that isn't loaded, shows the first time
     * and is hidden after that. {@link #HERE} is the second line a syntax error comes with, for a line that points
     * at a {@code /setblock}.
     */
    public boolean hidesFailure(String key) {
        if (key == null) return false;
        if (key.equals(HERE)) return (!sent.isEmpty() || grace > 0) && !shown.add(key);
        if (sent.isEmpty() || !key.equals(FAILED) && !isError(key)) return false;
        sent.remove();
        answered();
        return key.equals(FAILED) || !shown.add(key);
    }

    private static boolean isError(String key) {
        for (String start : ERRORS) if (key.startsWith(start)) return true;
        return false;
    }

    private void answered() {
        idle = 0;
        if (sent.isEmpty()) grace = GRACE;
    }
}
