package dev.curvegen.client.fabric.mixin;

import dev.curvegen.client.edit.StepHold;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no event for key input either. Hold-to-type needs to see a key come up or go down afresh, with or
 * without a screen open, to tell a new press from the repeats of a held key. It only watches.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void curvegen$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        StepHold.keyEvent(event, action);
    }
}
