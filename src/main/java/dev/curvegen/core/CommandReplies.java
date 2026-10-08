package dev.curvegen.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Which of a server's chat lines answer the {@code /setblock} commands the mod sent itself, so the client can keep
 * them out of chat. A line is known by its translation key. The server answers commands in the order they were
 * sent, and this keeps the ones still unanswered in that order: the mod's by position, and a mark for each command
 * anything else sent in between. A success is the mod's only if it names one of those positions. An error names
 * nothing, so it answers the oldest command, and shows if that one isn't the mod's.
 *
 * <p>A command of the player's that prints no error leaves its mark, so one error of the mod's after it can show.
 */
public final class CommandReplies {
    public static final String SUCCESS = "commands.setblock.success", FAILED = "commands.setblock.failed", HERE = "command.context.here";
    /** What an error's key starts with when {@code /setblock} can give it: no such command without permission, a position the server won't take, a block it doesn't know. */
    private static final String[] ERRORS = {"command.unknown.", "argument.pos.", "argument.block.", "argument.id."};
    /** Ticks without a command sent or a reply seen before the rest are given up on, and how long the last error's second line may take. */
    static final int TIMEOUT = 200, GRACE = 40;

    private record Pos(int x, int y, int z) {}
    /** Stands in the queue for a command that isn't the mod's. */
    private static final Object OTHER = new Object();
    private final ArrayDeque<Object> sent = new ArrayDeque<>();
    /** The errors already shown since the commands started. */
    private final Set<String> shown = new HashSet<>();
    private int idle, grace;

    /** Call for each command as it's sent. */
    public void sent(int x, int y, int z) {
        if (sent.isEmpty() && grace == 0) shown.clear();
        sent.add(new Pos(x, y, z));
        idle = 0;
    }

    /** Call when the client sends any other command, the player's or another mod's. */
    public void other() {
        if (!sent.isEmpty()) sent.add(OTHER);
    }

    /** Call once a client tick. */
    public void tick() {
        if (!sent.isEmpty()) {
            if (++idle > TIMEOUT) sent.clear();
        } else if (grace > 0) grace--;
    }

    /** How many commands haven't been answered yet. */
    public int awaiting() { return (int) sent.stream().filter(c -> c != OTHER).count(); }

    /**
     * Whether to hide a line that says a command worked, by its translation key. Only "Changed the block" at a
     * position one of the mod's unanswered commands went to. The commands sent before that one have been answered too.
     * A success of the player's own for the very block the mod is still setting is hidden with them.
     */
    public boolean hidesSuccess(String key, int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        if (!SUCCESS.equals(key) || !sent.contains(p)) return false;
        while (!sent.remove().equals(p)) { }
        answered();
        return true;
    }

    /**
     * Whether to hide a line that says a command failed, by its translation key. Never when the oldest unanswered
     * command isn't the mod's. Otherwise "Could not set the block" is hidden, and any other error {@code /setblock}
     * can give, such as a position that isn't loaded, shows the first time and is hidden after that. {@link #HERE}
     * is the second line a syntax error comes with, for a line that points at a {@code /setblock}.
     */
    public boolean hidesFailure(String key) {
        if (key == null) return false;
        if (key.equals(HERE)) return (!sent.isEmpty() || grace > 0) && !shown.add(key);
        if (sent.isEmpty()) return false;
        if (sent.peek() == OTHER) { sent.remove(); answered(); return false; }
        if (!key.equals(FAILED) && !isError(key)) return false;
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
