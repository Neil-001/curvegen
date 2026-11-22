package dev.curvegen.client.fabric.mixin;

import dev.curvegen.client.edit.StepHold;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void curvegen$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        StepHold.keyEvent(event, action);
    }
}
