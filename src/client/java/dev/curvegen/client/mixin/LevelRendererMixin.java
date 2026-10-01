//? if >=1.21.9 <1.21.11 {
/*package dev.curvegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curvegen.client.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Draws the placement hologram at the end of the main pass, after translucent terrain. Fabric API has no world
 * render events on 1.21.9, and 1.21.10 runs the same jar. Other versions use Fabric's event and don't have this class.
 ^/
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    // The lambda that addMainPass hands to the frame pass. Its last endBatch() comes after translucent terrain.
    @Inject(method = "method_62214", at = @At(value = "INVOKE:LAST", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void curvegen$renderHologram(CallbackInfo ci) {
        Placement.render(new PoseStack(), Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
}
*///?}
