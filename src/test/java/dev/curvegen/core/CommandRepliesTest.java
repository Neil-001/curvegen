package dev.curvegen.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandRepliesTest {
    private static final String NO_COMMAND = "command.unknown.command", S = CommandReplies.SUCCESS;

    @Test
    void nothingIsHiddenUntilTheModSendsCommands() {
        CommandReplies r = new CommandReplies();
        assertFalse(r.hidesSuccess(S, 1, 2, 3));
        assertFalse(r.hidesFailure(CommandReplies.FAILED));
        assertFalse(r.hidesFailure(NO_COMMAND));
        assertFalse(r.hidesFailure(CommandReplies.HERE));
    }

    @Test
    void itsOwnRepliesAreHiddenAndNoMore() {
        CommandReplies r = new CommandReplies();
        r.sent(1, 2, 3);
        r.sent(1, 2, 4);
        r.sent(1, 2, 5);
        assertFalse(r.hidesSuccess(S, 9, 9, 9), "the player's own /setblock somewhere else");
        assertFalse(r.hidesSuccess("commands.time.query", 1, 2, 3), "another command's result");
        assertFalse(r.hidesFailure("commands.fill.failed"), "an error /setblock can't give");
        assertFalse(r.hidesFailure("commands.time.query"));
        assertFalse(r.hidesFailure(null));
        assertEquals(3, r.awaiting(), "none of those used up a reply");
        assertTrue(r.hidesSuccess(S, 1, 2, 3));
        assertFalse(r.hidesSuccess(S, 1, 2, 3), "only one command went there");
        assertTrue(r.hidesFailure(CommandReplies.FAILED), "the block was already there");
        assertEquals(1, r.awaiting());
        assertTrue(r.hidesSuccess(S, 1, 2, 5));
        assertEquals(0, r.awaiting());
        assertFalse(r.hidesFailure(CommandReplies.FAILED), "every command has been answered");
        assertFalse(r.hidesSuccess(S, 1, 2, 4));
    }

    @Test
    void theSamePositionTwiceHidesTwoReplies() {
        CommandReplies r = new CommandReplies();
        r.sent(1, 2, 3);
        r.sent(1, 2, 3);
        assertTrue(r.hidesSuccess(S, 1, 2, 3));
        assertTrue(r.hidesSuccess(S, 1, 2, 3));
        assertFalse(r.hidesSuccess(S, 1, 2, 3));
    }

    @Test
    void thePlayersOwnFailuresShowInTheirPlaceInTheOrder() {
        CommandReplies r = new CommandReplies();
        r.other();
        assertEquals(0, r.awaiting(), "nothing of the mod's is out, so there's nothing to keep track of");
        r.sent(0, 0, 0);
        r.other();            // the player's /setblock on a block that's already there
        r.sent(1, 0, 0);
        r.sent(2, 0, 0);
        r.other();            // the player's /fill somewhere that isn't loaded, twice
        r.other();
        r.sent(3, 0, 0);
        assertTrue(r.hidesSuccess(S, 0, 0, 0));
        assertFalse(r.hidesFailure(CommandReplies.FAILED), "the player's");
        assertTrue(r.hidesSuccess(S, 1, 0, 0), "the mod's next reply isn't let through by it");
        assertTrue(r.hidesFailure(CommandReplies.FAILED), "the mod's own");
        assertFalse(r.hidesFailure("argument.pos.unloaded"));
        assertFalse(r.hidesFailure("argument.pos.unloaded"), "the same error again is still the player's");
        assertTrue(r.hidesSuccess(S, 3, 0, 0));
        assertEquals(0, r.awaiting());
    }

    @Test
    void aPlayersCommandThatPrintsNoErrorIsPassedByTheNextSuccess() {
        CommandReplies r = new CommandReplies();
        r.sent(0, 0, 0);
        r.other();            // /time query, whose result isn't an error
        r.sent(1, 0, 0);
        r.sent(2, 0, 0);
        assertTrue(r.hidesSuccess(S, 0, 0, 0));
        assertTrue(r.hidesSuccess(S, 1, 0, 0));
        assertTrue(r.hidesFailure(CommandReplies.FAILED));
        assertEquals(0, r.awaiting());
    }

    @Test
    void aSuccessAnswersTheCommandsBeforeItToo() {
        CommandReplies r = new CommandReplies();
        for (int k = 0; k < 3; k++) r.sent(k, 0, 0);
        assertTrue(r.hidesSuccess(S, 1, 0, 0), "the first one's reply never came");
        assertEquals(1, r.awaiting());
        assertFalse(r.hidesSuccess(S, 0, 0, 0));
    }

    @Test
    void anErrorThePlayerNeedsShowsOnce() {
        CommandReplies r = new CommandReplies();
        for (int k = 0; k < 3; k++) r.sent(k, 0, 0);
        // Without permission every command gets the error and a second line pointing at the command.
        assertFalse(r.hidesFailure(NO_COMMAND));
        assertFalse(r.hidesFailure(CommandReplies.HERE));
        for (int k = 1; k < 3; k++) {
            assertTrue(r.hidesFailure(NO_COMMAND));
            assertTrue(r.hidesFailure(CommandReplies.HERE), "also after the last command's error");
        }
        assertEquals(0, r.awaiting());
        assertFalse(r.hidesFailure(NO_COMMAND), "the player's own mistake afterwards");
        // A different error is news.
        r.sent(0, 0, 0);
        r.sent(0, 0, 1);
        assertTrue(r.hidesFailure(NO_COMMAND), "still the same run of commands");
        assertFalse(r.hidesFailure("argument.pos.unloaded"));
    }

    @Test
    void theNextRunShowsItsErrorAgain() {
        CommandReplies r = new CommandReplies();
        r.sent(0, 0, 0);
        assertFalse(r.hidesFailure(NO_COMMAND));
        for (int t = 0; t <= CommandReplies.GRACE; t++) r.tick();
        assertFalse(r.hidesFailure(CommandReplies.HERE), "too late to belong to the mod's command");
        r.sent(0, 0, 0);
        assertFalse(r.hidesFailure(NO_COMMAND));
    }

    @Test
    void repliesThatNeverComeAreGivenUpOn() {
        CommandReplies r = new CommandReplies();
        r.sent(1, 2, 3);
        r.sent(1, 2, 4);
        for (int t = 0; t < CommandReplies.TIMEOUT; t++) r.tick();
        assertTrue(r.hidesSuccess(S, 1, 2, 3), "a reply restarts the wait");
        for (int t = 0; t <= CommandReplies.TIMEOUT; t++) r.tick();
        assertEquals(0, r.awaiting());
        assertFalse(r.hidesSuccess(S, 1, 2, 4));
    }
}
