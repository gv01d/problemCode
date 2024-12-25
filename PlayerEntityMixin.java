package me.gv0id.arbalests.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntityMixin {
    @Shadow
    public abstract boolean isSwimming();


    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void travelInject(Vec3d movementInput, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (quakeInstance.travel(player, movementInput)) {
            ci.cancel();
        }
    }
}
