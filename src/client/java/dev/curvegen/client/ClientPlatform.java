package dev.curvegen.client;

import dev.curvegen.net.PlaceBlocksPayload;
import java.nio.file.Path;

/** What the client needs from the mod loader. The loader's client entrypoint passes one to {@link CurveGenClient#init}. */
public interface ClientPlatform {
    /** Whether the server we're connected to accepts placement packets, which means it has the mod. */
    boolean canSendToServer();

    void sendToServer(PlaceBlocksPayload payload);

    Path configDir();

    Path gameDir();
}
