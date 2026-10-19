package dev.curvegen.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.core.CommandReplies;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

/** The chat lines a server sends for a command, built the way the game's {@code Commands} and {@code CommandSourceStack} build them. */
class PlacementFeedbackTest {
    private static Component success(int x, int y, int z) { return Component.translatable("commands.setblock.success", x, y, z); }

    private static Component failure(Component message) { return Component.empty().append(message).withStyle(ChatFormatting.RED); }

    private static Component failure(String key) { return failure(Component.translatable(key)); }

    /** The second line of a syntax error: the end of the command, then "<--[HERE]". */
    private static Component context(String command) {
        MutableComponent context = Component.empty().withStyle(ChatFormatting.GRAY)
                .withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/" + command)));
        context.append(CommonComponents.ELLIPSIS);
        context.append(command.substring(command.length() - 10));
        context.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        return failure(context);
    }

    private static CommandReplies sent(int n) {
        CommandReplies r = new CommandReplies();
        for (int k = 0; k < n; k++) r.sent(k, 64, 0);
        return r;
    }

    @Test
    void theModsSuccessesAndFailuresAreHidden() {
        CommandReplies r = sent(3);
        assertTrue(Placement.hides(r, success(0, 64, 0)));
        assertTrue(Placement.hides(r, failure("commands.setblock.failed")));
        // The numbers can arrive as text.
        assertTrue(Placement.hides(r, Component.translatable("commands.setblock.success", "2", Component.literal("64"), "0")));
        assertFalse(Placement.hides(r, success(2, 64, 0)), "every command has been answered");
    }

    @Test
    void thePlayersOwnCommandsStillPrint() {
        CommandReplies r = sent(3);
        assertFalse(Placement.hides(r, success(7, 64, 7)));
        assertFalse(Placement.hides(r, Component.translatable("commands.time.query", 6000)));
        assertFalse(Placement.hides(r, failure("commands.fill.failed")));
        assertFalse(Placement.hides(r, Component.literal("hello")));
        assertFalse(Placement.hides(r, Component.translatable("chat.type.text", "Tester", "commands.setblock.failed")));
        assertFalse(Placement.hides(r, Component.translatable("commands.setblock.failed")), "not in an error's wrapping");
        assertEquals(3, r.awaiting());
        assertFalse(Placement.hidesFeedback(success(0, 64, 0), true), "a line above the hotbar is never a command's reply");
    }

    @Test
    void losingPermissionMidRunShowsTheErrorOnce() {
        CommandReplies r = sent(3);
        assertFalse(Placement.hides(r, failure("command.unknown.command")));
        assertFalse(Placement.hides(r, context("setblock 0 64 0 minecraft:stone")));
        for (int k = 1; k < 3; k++) {
            assertTrue(Placement.hides(r, failure("command.unknown.command")));
            assertTrue(Placement.hides(r, context("setblock " + k + " 64 0 minecraft:stone")));
        }
        assertFalse(Placement.hides(r, failure("command.unknown.command")), "the player's own mistake afterwards");
    }

    @Test
    void anotherCommandsSyntaxErrorLineStillPrints() {
        CommandReplies r = sent(2);
        assertFalse(Placement.hides(r, failure("command.unknown.command")));
        assertFalse(Placement.hides(r, context("setblock 0 64 0 minecraft:stone")));
        assertFalse(Placement.hides(r, context("gamemode creative Nobody")));
    }
}
