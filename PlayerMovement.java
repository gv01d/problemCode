package me.gv0id.arbalests.entity.movement;

import me.gv0id.arbalests.Arbalests;
import net.minecraft.block.Blocks;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.List;
import java.util.Objects;

import static java.lang.Math.*;

public class PlayerMovement {

    public double maxVelocity = 40;
    public double acceleration = 5;

    public PlayerMovement(){
    }
    private Vec3d vel;



    // Called to add speed
    public boolean updateVelocity(Entity player, float speed, Vec3d movementInput){
        if (!(player instanceof PlayerEntity) ||
            !player.getWorld().isClient ||
            player.isCrawling() ||
            player.isTouchingWater() ||
            player.isInLava() ||
            ((PlayerEntity) player).isClimbing() ||
            (((PlayerEntity) player).getAbilities().flying && !player.hasVehicle())
        ) return false;

        //double wishVel = speed;
        Vec2f wishDir = getMovementDirection((PlayerEntity) player, new Vec2f((float)movementInput.x,(float)movementInput.z));
        Vec3d wishVel = new Vec3d(wishDir.x * speed, 0, wishDir.y * speed);

        player.addVelocity(wishVel);

        return true;
    }

    public void afterJump(LivingEntity player){
        if (player instanceof PlayerEntity){
            //
        }
    }

    private Vec2f getMovementDirection(PlayerEntity player, Vec2f movementInput){
        float lenght = movementInput.length();
        lenght = lenght < (float)1.0 ? (float)1.0 : (float)1.0 / lenght;

        Vec2f mov = new Vec2f(movementInput.y * lenght,movementInput.x * lenght);
        float s = (float) sin(player.getYaw() * PI / (double) 180 );
        float c = (float) cos(player.getYaw() * PI / (double) 180 );

        return new Vec2f(
                mov.y * c - mov.x * s,
                mov.x * c + mov.y * s
        );
    }

    // Travel
    public boolean travel(PlayerEntity player, Vec3d movementInput){

        if (!player.getWorld().isClient ||
            player.isGliding()||
            player.getAbilities().flying ||
            player.hasVehicle() ||
            player.isInSwimmingPose() ||
            player.isClimbing() ||
            player.isTouchingWater() // No custom movement when touching water
        ) return false;

        double preX = player.getX();
        double preY = player.getY();
        double preZ = player.getZ();

        return travelQuake(player,new Vec2f((float)movementInput.x,(float)movementInput.z));
    }

    float airControlVel = 0.001F;
    public boolean travelQuake(PlayerEntity player, Vec2f movementInput){
        if (player.isOnGround()){
            return false;
        }


        Vec2f wishVelocity = getMovementDirection(player,movementInput);
        double wishSpeed = (movementInput.x != 0.0 || movementInput.y != 0.0)? player.getMovementSpeed():0.0;
        //
        Vec2f tempVel = new Vec2f((float) player.getVelocity().x,(float) player.getVelocity().z);
        float tempVelLen = tempVel.length();
        float impulse = airControlVel * (1.0F/tempVelLen);
        tempVel = new Vec2f( (float) player.getVelocity().x + (wishVelocity.x * impulse), (float) player.getVelocity().z +(wishVelocity.y * impulse));
        tempVel = tempVel.normalize();
        tempVel = tempVel.multiply(tempVelLen);
        //

        Vec2f vel = airAccelerate(wishVelocity,wishSpeed,new Vec2f(tempVel.x,tempVel.y),wishSpeed,acceleration);


        player.setVelocity(tempVel.x + vel.x,player.getVelocity().y, tempVel.y + vel.y);

        player.move(MovementType.SELF, player.getVelocity());

        applyGravity(player);

        return true;
    }

    public void applyGravity(PlayerEntity player){
        boolean isLevitating = player.hasStatusEffect(StatusEffects.LEVITATION);
        double ySpeed = player.getVelocity().y;
        double gravity = -(player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? Math.min(player.getFinalGravity(), 0.01) : player.getFinalGravity());

        if ((player.horizontalCollision)
            && (player.isClimbing() || player.getBlockStateAtPos().isOf(Blocks.POWDER_SNOW)
            && PowderSnowBlock.canWalkOnPowderSnow(player))){
            ySpeed += 0.2;
        }
        if (player.hasStatusEffect(StatusEffects.SLOW_FALLING)){
            player.onLanding();
        }
        if (isLevitating){
            ySpeed += ((double)(0.05 * (Objects.requireNonNull(player.getStatusEffect(StatusEffects.LEVITATION)).getAmplifier() + 1)) - ySpeed) * 0.2;
            player.onLanding();
        }

        if (!player.getWorld().isClient ||
            player.getWorld().getChunkManager().isChunkLoaded(
                    ChunkSectionPos.getSectionCoord(
                            player.getBlockPos().getX()),
                    ChunkSectionPos.getSectionCoord(
                            player.getBlockPos().getZ())
            )
        ){
            if (!player.hasNoGravity() && !isLevitating)
                ySpeed += gravity;

            double airResistance = 0.9800000190734863;
            ySpeed *= airResistance;
        }
        else{
            ySpeed = player.getY() > player.getWorld().getBottomY() ? -0.1 : 0.0;
        }
        player.setVelocity(new Vec3d(
                player.getVelocity().x,
                ySpeed,
                player.getVelocity().z
        ));

    }

    /**
     * <h1>
     *     THE SECRET SAUCE TO AIRSTRAFING
     * </h1>
     * <h2>
     *         Quake air acceleration function
     * </h2>
     * <h3>
     *  Input
     * </h3>
     * <ul>
     * <li>
     *      <h4>< Wish Velocity > (input "wishveloc" in Quake) :</h4>
     *      Expected movement direction based on camera rotation
     *      Length equal to "Max Speed"
     * </li>
     * <li>
     *      <h4>< Unchanged Wish Speed > (global "wishspeed" in Quake) :</h4>
     *      Wish speed value, in theory, unchanged
     * </li>
     * <li>
     *      <h4>< Velocity > (global "velocity" in Quake) :</h4>
     *      Actual entity velocity
     * </li>
     * <li>
     *      <h4>< Acceleration > (global "sv_accelerate.value" in Quake) :</h4>
     *      acceleration constant
     * </li>
     * </ul>
     * <h3>
     *  Local:
     * </h3>
     * <ul>
     * <li>
     *      <h4>< Wish Speed > (local "wishspd" in Quake) :</h4>
     *      Expected Wish Length, that means, probably, "Max Speed"
     * </li>
     * <li>
     *      <h4>< Projected Current Speed > (local "currentspeed" in Quake) :</h4>
     *      DotProduct of velocity and wishSpeed.
     *      Probably the projection of how much speed is needed to achieve WishSpeed ( "Max Speed" ).
     * </li>
     * <li>
     *      <h4>< Add Speed > (local "addspeed" in Quake) :</h4>
     *      Difference between WishSpeed (Capped to 30) and ProjectedCurrentSpeed.
     *      Represents the amount of speed that needs to be added to reach WishSpeed ( "Max Speed" ).
     * </li>
     * <li>
     *      <h4>< Acceleration Speed > (local "accelspeed" in Quake) :</h4>
     *      The product of Acceleration * UnchangedWishSpeed (And * "host_frametime" - DeltaTime/time between last frame - in Quake).
     *      Represents the max acceleration per frame - in this context PER TICK, since minecraft.
     *      <ul>
     *          <li>
     *          <h4>OBS:</h4>
     *          Since minecraft uses TICK BASED processing it is not necessary to use "DeltaTime" equivalent, making it so movement
     *          cant have absurd bursts of speed with higher or absurdly low fps.
     *          Everything is based on ticks, so if everything moves slower,you do to
     *          </li>
     *      </ul>
     * </li>
     * </ul>
     */
    public Vec2f airAccelerate(Vec2f wishVelocity, double wishSpeedInit, Vec2f velocity, double unchangedWishSpeed, double acceleration){
        double wishSpeed = wishSpeedInit;
        wishSpeed = min(wishSpeed, maxVelocity);
        double projectedCurrentSpeed;
        double addSpeed;
        double accelSpeed;
        // - - -
        wishVelocity.normalize();
        // - - -
        projectedCurrentSpeed = (velocity.x * wishVelocity.x + velocity.y * wishVelocity.y);
        addSpeed = wishSpeed - projectedCurrentSpeed;
        if (addSpeed <= 0)
            return Vec2f.ZERO;
        // - - -
        accelSpeed = acceleration * unchangedWishSpeed * 0.1;
        Arbalests.LOGGER.info("Accel : {}", accelSpeed);
        if (accelSpeed > addSpeed)
            accelSpeed = addSpeed;
        // - -
        return new Vec2f((float )(wishVelocity.x * accelSpeed),(float)(wishVelocity.y * accelSpeed));

    }
}
