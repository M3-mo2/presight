package com.mohamed.presight.render;

/**
 * What a world-space renderer needs, computed once per frame.
 *
 * Passing this around instead of letting every feature recompute the camera keeps the
 * interpolation correct and stops each new feature from deriving it slightly differently
 * — the source of "my box is one frame behind" bugs.
 *
 * It also carries the frame's shared {@link BoxBatch}, so every feature's boxes go into
 * one draw call instead of one each. That is the difference between smooth and unusable
 * when forty players are on screen.
 */
public class RenderContext {

    /** Camera position, interpolated for this frame. */
    public final double camX;
    public final double camY;
    public final double camZ;

    /** Partial tick, 0..1, for interpolating entity positions. */
    public final float partialTicks;

    /** Camera yaw/pitch in degrees, for billboarding text. */
    public final float viewY;
    public final float viewX;

    /** The frame's line batch. Begun before the features, flushed after them all. */
    public final BoxBatch boxes;

    /** Text queued during the pass; drawn after the batch closes, never inside it. */
    public final TextBatch texts;

    public RenderContext(double camX, double camY, double camZ,
                         float partialTicks, float viewY, float viewX,
                         BoxBatch boxes, TextBatch texts) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.partialTicks = partialTicks;
        this.viewY = viewY;
        this.viewX = viewX;
        this.boxes = boxes;
        this.texts = texts;
    }

    /** Interpolate an entity's coordinate for this frame. */
    public double lerp(double last, double current) {
        return last + (current - last) * partialTicks;
    }

    /** Entity position relative to the camera — the coordinates drawing uses. */
    public double relX(net.minecraft.entity.Entity e) {
        return lerp(e.lastTickPosX, e.posX) - camX;
    }

    public double relY(net.minecraft.entity.Entity e) {
        return lerp(e.lastTickPosY, e.posY) - camY;
    }

    public double relZ(net.minecraft.entity.Entity e) {
        return lerp(e.lastTickPosZ, e.posZ) - camZ;
    }
}
