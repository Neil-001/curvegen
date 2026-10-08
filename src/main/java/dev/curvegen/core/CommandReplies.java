package dev.curvegen.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which of a server's chat lines answer the {@code /setblock} commands the mod sent itself, so the client can keep
 * them out of chat. A line is known by its translation key. The server answers commands in the order they were
 * sent, and this keeps every command the client sent in that order until it's answered or passed: the mod's, and
 * the ones the player or anything else sent. A success names a position, so it answers the oldest command that set
 * a block there. An error names nothing, so it answers the oldest command. Either is hidden only when that command
 * is the mod's.
 *
 * <p>A command of the player's that prints no error stays in the queue until a later reply passes it, so one error of
 * the mod's after it can show.
 */
public final class CommandReplies {
    public static final String SUCCESS = "commands.setblock.success", FAILED = "commands.setblock.failed", HERE = "command.context.here";
    /** What an error's key starts with when {@code /setblock} can give it: no such command without permission, a position the server won't take, a block it doesn't know. */
    private static final String[] ERRORS = {"command.unknown.", "argument.pos.", "argument.block.", "argument.id."};
    private static final Pattern SETBLOCK = Pattern.compile("setblock (-?\\d+) (-?\\d+) (-?\\d+)( .*)?");
    /** Ticks without a command sent or a reply seen before the rest are given up on, and how long the last error's second line may take. */
    static final int TIMEOUT = 200, GRACE = 40;

    private record Pos(int x, int y, int z) {}
    /** A command waiting for its reply: the position it sets a block at, if it's a plain {@code /setblock}, and whether the mod sent it. */
    private record Sent(Pos pos, boolean own) {}
    private final ArrayDeque<Sent> sent = new ArrayDeque<>();
    /** The errors already shown since the mod's commands started. */
    private final Set<String> shown = new HashSet<>();
    /** How many of the mod's commands are unanswered, and whether the last error seen answered one of them. */
    private int own;
    private boolean ownError;
    private int idle, grace;

    /** Call for each of the mod's commands as it's sent. */
    public void sent(int x, int y, int z) {
        if (own == 0 && grace == 0) shown.clear();
        sent.add(new Sent(new Pos(x, y, z), true));
        own++;
        idle = 0;
    }

    /** Call when the client sends any other command, the player's or another mod's, with its text without the slash. */
    public void other(String command) {
        Matcher m = SETBLOCK.matcher(command);
        Pos pos = null;
        try {
            if (m.matches()) pos = new Pos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (NumberFormatException _) { }
        sent.add(new Sent(pos, false));
        idle = 0;
    }

    /** Call once a client tick. */
    public void tick() {
        if (!sent.isEmpty() && ++idle > TIMEOUT) { sent.clear(); own = 0; }
        if (own == 0 && grace > 0 && --grace == 0) ownError = false;
    }

    /** How many of the mod's commands haven't been answered yet. */
    public int awaiting() { return own; }

    /**
     * Whether to hide a line that says a command worked, by its translation key. Only "Changed the block", and only
     * when the oldest unanswered command that set a block there is the mod's. The commands sent before that one have
     * been answered too.
     */
    public boolean hidesSuccess(String key, int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        if (!SUCCESS.equals(key) || sent.stream().noneMatch(c -> p.equals(c.pos))) return false;
        ownError = false;
        Sent answered;
        do answered = remove(); while (!p.equals(answered.pos));
        return answered.own;
    }

    /**
     * Whether to hide a line that says a command failed, by its translation key. Never when the oldest unanswered
     * command isn't the mod's. Otherwise "Could not set the block" is hidden, and any other error {@code /setblock}
     * can give, such as a position that isn't loaded, shows the first time and is hidden after that. {@link #HERE}
     * is the second line a syntax error comes with, and belongs to whichever command the error before it did.
     */
    public boolean hidesFailure(String key) {
        if (key == null) return false;
        if (key.equals(HERE)) return ownError && !shown.add(key);
        ownError = false;
        if (sent.isEmpty()) return false;
        if (!sent.peek().own) { remove(); return false; }
        if (!key.equals(FAILED) && !isError(key)) return false;
        remove();
        ownError = true;
        return key.equals(FAILED) || !shown.add(key);
    }

    private static boolean isError(String key) {
        for (String start : ERRORS) if (key.startsWith(start)) return true;
        return false;
    }

    private Sent remove() {
        Sent c = sent.remove();
        idle = 0;
        if (c.own && --own == 0) grace = GRACE;
        return c;
    }
}
