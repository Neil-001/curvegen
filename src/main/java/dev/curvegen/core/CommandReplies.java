package dev.curvegen.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which of a server's chat lines answer the {@code /setblock} commands the mod sent itself, so the client can keep
 * them out of chat. A line is known by its translation key. Only as many lines are hidden as commands were sent, and
 * a success only for a position a command went to, so what the player's own commands print still shows.
 */
public final class CommandReplies {
    public static final String SUCCESS = "commands.setblock.success", FAILED = "commands.setblock.failed", HERE = "command.context.here";
    /** Ticks without a command sent or a reply seen before the rest are given up on, and how long the last error's second line may take. */
    static final int TIMEOUT = 200, GRACE = 40;

    private record Pos(int x, int y, int z) {}
    private final Map<Pos, Integer> sent = new HashMap<>();
    /** The errors already shown since the commands started. */
    private final Set<String> shown = new HashSet<>();
    private int awaiting, idle, grace;

    /** Call for each command as it's sent. */
    public void sent(int x, int y, int z) {
        if (awaiting == 0 && grace == 0) shown.clear();
        sent.merge(new Pos(x, y, z), 1, Integer::sum);
        awaiting++;
        idle = 0;
    }

    /** Call once a client tick. */
    public void tick() {
        if (awaiting > 0) {
            if (++idle > TIMEOUT) { awaiting = 0; sent.clear(); }
        } else if (grace > 0) grace--;
    }

    /** How many commands haven't been answered yet. */
    public int awaiting() { return awaiting; }

    /**
     * Whether to hide a line with this translation key. {@code at} is the position a success names, or null.
     * Successes and "could not set the block" are hidden. Any other command error, such as having no permission,
     * shows the first time and is hidden after that. {@link #HERE} is the second line such an error comes with.
     */
    public boolean hides(String key, int[] at) {
        if (key == null) return false;
        if (key.equals(HERE)) return (awaiting > 0 || grace > 0) && !shown.add(key);
        if (awaiting == 0) return false;
        if (key.equals(SUCCESS)) {
            if (at == null) return false;
            Pos p = new Pos(at[0], at[1], at[2]);
            Integer n = sent.get(p);
            if (n == null) return false;
            if (n == 1) sent.remove(p); else sent.put(p, n - 1);
            answered();
            return true;
        }
        if (!key.startsWith("command.") && !key.startsWith("commands.") && !key.startsWith("argument.")
                && !key.startsWith("parsing.") && !key.startsWith("permissions.")) return false;
        answered();
        return key.equals(FAILED) || !shown.add(key);
    }

    private void answered() {
        idle = 0;
        if (--awaiting == 0) { sent.clear(); grace = GRACE; }
    }
}
