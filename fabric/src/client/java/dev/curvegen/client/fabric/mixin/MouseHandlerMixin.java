package dev.curvegen.client.fabric.mixin;

import dev.curvegen.client.edit.Editor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no event for mouse input outside screens, so this gives the editor the first look at clicks and
 * scrolling while no screen is open. It cancels only what the editor says it used.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    private boolean curvegen$inWorld(long handle) {
        return handle != 0 && handle == minecraft.getWindow().handle() && minecraft.gui.screen() == null && minecraft.gui.overlay() == null;
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void curvegen$onButton(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (curvegen$inWorld(handle) && Editor.mouseButton(button.button(), action == 1)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void curvegen$onScroll(long handle, double x, double y, CallbackInfo ci) {
        if (curvegen$inWorld(handle) && Editor.mouseScroll(y)) ci.cancel();
    }
}
