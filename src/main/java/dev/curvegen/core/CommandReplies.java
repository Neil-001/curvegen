package dev.curvegen.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandReplies {
    public static final String SUCCESS = "commands.setblock.success", FAILED = "commands.setblock.failed", HERE = "command.context.here";
    private static final String[] ERRORS = {"command.unknown.", "argument.pos.", "argument.block.", "argument.id."};
    private static final Pattern SETBLOCK = Pattern.compile("setblock (-?\\d+) (-?\\d+) (-?\\d+)( .*)?");
    private static final Pattern NESTED_COMMAND = Pattern.compile("^(?:execute|return)\\s+(?:run|.*?\\s+run)\\s+");
    static final int TIMEOUT = 200, GRACE = 40;

    private record Pos(int x, int y, int z) {}
    private record Sent(Pos pos, boolean own, boolean anywhere) {
        boolean sets(Pos p) { return anywhere || p.equals(pos); }
    }
    private static final Sent MULTIPLE_REPLIES = new Sent(null, false, true);
    private final ArrayDeque<Sent> sent = new ArrayDeque<>();
    private final Set<String> shown = new HashSet<>();
    private int own;
    private boolean ownError;
    private boolean unsure;
    private int idle, grace;

    public void sent(int x, int y, int z) {
        idle = 0;
        if (sent.peekLast() == MULTIPLE_REPLIES) return;
        if (own == 0 && grace == 0) shown.clear();
        sent.add(new Sent(new Pos(x, y, z), true, false));
        own++;
    }

    public void other(String command) {
        idle = 0;
        if (sent.peekLast() == MULTIPLE_REPLIES) return;
        String executable = command;
        Matcher nested = NESTED_COMMAND.matcher(executable);
        while (nested.lookingAt()) {
            executable = executable.substring(nested.end());
            nested.reset(executable);
        }
        boolean setblock = executable.equals("setblock") || executable.startsWith("setblock ");
        if (setblock && !executable.equals(command)) {
            sent.add(MULTIPLE_REPLIES);
            return;
        }
        Matcher m = SETBLOCK.matcher(command);
        Pos pos = null;
        try {
            if (m.matches()) pos = new Pos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (NumberFormatException _) { }
        sent.add(new Sent(pos, false, pos == null && setblock));
    }

    public void tick() {
        if (!sent.isEmpty() && ++idle > TIMEOUT) { sent.clear(); own = 0; unsure = false; }
        if (own == 0 && grace > 0 && --grace == 0) ownError = false;
    }

    public int awaiting() { return own; }

    public boolean hidesSuccess(String key, int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        if (!SUCCESS.equals(key) || sent.stream().noneMatch(c -> c.sets(p))) return false;
        ownError = false;
        while (!sent.peek().sets(p)) remove();
        if (sent.peek() == MULTIPLE_REPLIES) {
            idle = 0;
            return false;
        }
        Sent answered = remove();
        if (answered.own) unsure = false;
        return answered.own;
    }

    public boolean hidesFailure(String key) {
        if (key == null) return false;
        if (key.equals(HERE)) return ownError && !shown.add(key);
        ownError = false;
        if (sent.isEmpty()) return false;
        if (sent.peek() == MULTIPLE_REPLIES) {
            idle = 0;
            return false;
        }
        if (!sent.peek().own) { remove(); unsure = !sent.isEmpty(); return false; }
        if (!key.equals(FAILED) && !isError(key)) return false;
        remove();
        if (unsure) { unsure = !sent.isEmpty(); return false; }
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
