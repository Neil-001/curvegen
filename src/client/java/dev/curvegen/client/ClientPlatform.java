package dev.curvegen.client;

import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;

/**
 * What the client needs from the mod loader. The loader's client entrypoint passes one to {@link CurveGenClient#init}.
 *
 * <p>Each loader also hooks the mouse while no screen is open: it passes button presses and releases to
 * {@code Editor.mouseButton} and wheel movement to {@code Editor.mouseScroll}, and keeps the event from the game
 * when they return true.
 */
public interface ClientPlatform {
    /** Whether the server we're connected to accepts placement packets, which means it has the mod. */
    boolean canSendToServer();

    void sendToServer(PlaceBlocksPayload payload);

    Path configDir();

    Path gameDir();
}
