package com.mohamed.presight.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * World -> screen projection.
 *
 * Uses GLU's own gluProject against the live matrices and viewport, which is the
 * exact transform the world pass used a moment earlier. Re-deriving the perspective
 * by hand is where "my overlay is 40px off" bugs come from.
 */
public final class Projection {

    private static final FloatBuffer MODEL = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJ = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEW = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer OUT = BufferUtils.createFloatBuffer(3);

    private Projection() {
    }

    /**
     * Screen position of a point given in camera-relative coordinates.
     *
     * @return {x, y, depth} in scaled-screen pixels, or null when the point is behind
     *         the camera (depth outside 0..1), because gluing a panel to something
     *         behind you draws it in the wrong place entirely.
     */
    public static double[] worldToScreen(double x, double y, double z, int scaledW, int scaledH) {
        MODEL.clear();
        PROJ.clear();
        VIEW.clear();
        OUT.clear();

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEW);

        if (GLU.gluProject((float) x, (float) y, (float) z, MODEL, PROJ, VIEW, OUT) == false) {
            return null;
        }

        float sx = OUT.get(0);
        float sy = OUT.get(1);
        float sz = OUT.get(2);
        if (sz < 0.0F || sz > 1.0F) {
            return null;   // behind the camera or past the far plane
        }

        // gluProject returns window pixels; the HUD draws in scaled pixels. Convert
        // through the viewport height so it is correct at every GUI scale.
        int viewportH = VIEW.get(3);
        double scale = (double) scaledH / (double) viewportH;
        return new double[]{sx * scale, scaledH - sy * scale, sz};
    }
}
