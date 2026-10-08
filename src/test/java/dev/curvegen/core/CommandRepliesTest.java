package dev.curvegen.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandRepliesTest {
    private static final String NO_COMMAND = "command.unknown.command";

    @Test
    void nothingIsHiddenUntilTheModSendsCommands() {
        CommandReplies r = new CommandReplies();
        assertFalse(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}));
        assertFalse(r.hides(CommandReplies.FAILED, null));
        assertFalse(r.hides(NO_COMMAND, null));
        assertFalse(r.hides(CommandReplies.HERE, null));
    }

    @Test
    void itsOwnRepliesAreHiddenAndNoMore() {
        CommandReplies r = new CommandReplies();
        r.sent(1, 2, 3);
        r.sent(1, 2, 4);
        r.sent(1, 2, 5);
        assertFalse(r.hides(CommandReplies.SUCCESS, new int[]{9, 9, 9}), "the player's own /setblock somewhere else");
        assertFalse(r.hides("chat.type.text", null), "not a command's reply");
        assertFalse(r.hides(null, null));
        assertTrue(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}));
        assertFalse(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}), "only one command went there");
        assertTrue(r.hides(CommandReplies.FAILED, null), "the block was already there");
        assertEquals(1, r.awaiting());
        assertTrue(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 5}));
        assertEquals(0, r.awaiting());
        assertFalse(r.hides(CommandReplies.FAILED, null), "every command has been answered");
        assertFalse(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 4}));
    }

    @Test
    void theSamePositionTwiceHidesTwoReplies() {
        CommandReplies r = new CommandReplies();
        r.sent(1, 2, 3);
        r.sent(1, 2, 3);
        assertTrue(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}));
        assertTrue(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}));
        assertFalse(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}));
    }

    @Test
    void anErrorThePlayerNeedsShowsOnce() {
        CommandReplies r = new CommandReplies();
        for (int k = 0; k < 3; k++) r.sent(k, 0, 0);
        // Without permission every command gets the error and a second line pointing at the command.
        assertFalse(r.hides(NO_COMMAND, null));
        assertFalse(r.hides(CommandReplies.HERE, null));
        for (int k = 1; k < 3; k++) {
            assertTrue(r.hides(NO_COMMAND, null));
            assertTrue(r.hides(CommandReplies.HERE, null), "also after the last command's error");
        }
        assertEquals(0, r.awaiting());
        assertFalse(r.hides(NO_COMMAND, null), "the player's own mistake afterwards");
        // A different error is news.
        r.sent(0, 0, 0);
        r.sent(0, 0, 1);
        assertTrue(r.hides(NO_COMMAND, null), "still the same run of commands");
        assertFalse(r.hides("argument.pos.unloaded", null));
    }

    @Test
    void theNextRunShowsItsErrorAgain() {
        CommandReplies r = new CommandReplies();
        r.sent(0, 0, 0);
        assertFalse(r.hides(NO_COMMAND, null));
        for (int t = 0; t <= CommandReplies.GRACE; t++) r.tick();
        assertFalse(r.hides(CommandReplies.HERE, null), "too late to belong to the mod's command");
        r.sent(0, 0, 0);
        assertFalse(r.hides(NO_COMMAND, null));
    }

    @Test
    void repliesThatNeverComeAreGivenUpOn() {
        CommandReplies r = new CommandReplies();
        r.sent(1, 2, 3);
        r.sent(1, 2, 4);
        for (int t = 0; t < CommandReplies.TIMEOUT; t++) r.tick();
        assertTrue(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 3}), "a reply restarts the wait");
        for (int t = 0; t <= CommandReplies.TIMEOUT; t++) r.tick();
        assertEquals(0, r.awaiting());
        assertFalse(r.hides(CommandReplies.SUCCESS, new int[]{1, 2, 4}));
    }
}
