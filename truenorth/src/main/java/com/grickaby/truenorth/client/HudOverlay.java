package com.grickaby.truenorth.client;

import com.grickaby.truenorth.scan.ScanManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class HudOverlay {

    private static float smoothAngleDeg = 0f;

    private static final int   SWEEP_DEG        = 70;
    private static final int   BASE_FROM_BOTTOM = 44;
    private static final int   END_MARGIN_X     = 160;
    private static final int   ARC_HEIGHT_PX    = 14;

    private static final float FOLLOW_ALPHA = 0.12f;
    private static final float MAX_STEP_DEG = 2.0f;
    private static final float SNAP_DEG     = 0.6f;

    public static void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        LocalPlayer p = mc.player;
        if (p == null) return;

        final int w = gg.guiWidth(), h = gg.guiHeight();
        final int cx = w / 2, cy = h - BASE_FROM_BOTTOM;

        // Arc geometry
        float sweepRad = (float) Math.toRadians(SWEEP_DEG);
        float sinSweep = (float) Math.sin(sweepRad);
        float R = ((w / 2f) - END_MARGIN_X) / Math.max(0.2f, sinSweep);
        float FLAT = Math.max(0.2f, ARC_HEIGHT_PX / R);

        // Arc + ticks
        drawArcSmoothApprox(gg, cx, cy, R, FLAT, 0xB0FFFFFF, 1);
        drawVerticalTicksSimple(gg, cx, cy, R, FLAT, 35, 5, 0xFFFFFFFF, 1);

        // Targets
        var list = ScanManager.get().currentTargets();
        if (list.isEmpty()) {
            gg.drawString(mc.font, "no target",
                cx - mc.font.width("no target") / 2,
                cy - (int)(R * FLAT) - 12, 0xAAAAAA);
            return;
        }

        // We'll need the eye pos for both secondary + primary
        Vec3 eye = p.getEyePosition();

        // SECONDARIES (no smoothing)
        for (int i = 1; i < list.size(); i++) {
            var t = list.get(i);
            Vec3 to2 = Vec3.atCenterOf(t.pos());
            double dx2 = to2.x - eye.x, dz2 = to2.z - eye.z;
            float bearing2 = (float) Math.toDegrees(Math.atan2(-dx2, dz2));
            float rel2     = Mth.wrapDegrees(bearing2 - p.getYRot());
            float disp2    = Mth.clamp(rel2, -SWEEP_DEG, SWEEP_DEG);

            int mx2 = (int) (cx + Math.sin(Math.toRadians(disp2)) * R);
            int my2 = (int) (cy - Math.cos(Math.toRadians(disp2)) * R * FLAT);

            int col = oreColorFromName(t.displayName());
            col = (col & 0x00FFFFFF) | 0x80_000000; // 50% alpha

            gg.fill(mx2 - 2, my2 - 2, mx2 + 2, my2 + 2, 0xFF000000);
            gg.fill(mx2 - 1, my2 - 1, mx2 + 1, my2 + 1, col);
        }

        // PRIMARY (index 0) — smoothed + label
        var tgt = list.get(0);
        Vec3 to  = Vec3.atCenterOf(tgt.pos());
        double dx = to.x - eye.x, dz = to.z - eye.z;
        float bearingDeg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relDeg     = Mth.wrapDegrees(bearingDeg - p.getYRot());

        float target = Mth.wrapDegrees(relDeg);
        float delta  = Mth.wrapDegrees(target - smoothAngleDeg);
        float step   = Mth.clamp(delta * FOLLOW_ALPHA, -MAX_STEP_DEG, MAX_STEP_DEG);
        smoothAngleDeg = Mth.wrapDegrees(smoothAngleDeg + step);
        if (Math.abs(Mth.wrapDegrees(target - smoothAngleDeg)) < SNAP_DEG) {
            smoothAngleDeg = target;
        }

        float disp = Mth.clamp(smoothAngleDeg, -SWEEP_DEG, SWEEP_DEG);
        int mx = (int) (cx + Math.sin(Math.toRadians(disp)) * R);
        int my = (int) (cy - Math.cos(Math.toRadians(disp)) * R * FLAT);

        int colARGB = oreColorFromName(tgt.displayName());
        int textRGB = colARGB & 0xFFFFFF;
        gg.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
        gg.fill(mx - 2, my - 2, mx + 2, my + 2, colARGB);

        int distM = (int) eye.distanceTo(to);
        int dy = tgt.pos().getY() - p.blockPosition().getY();
        String label = tgt.displayName() + " " + distM + "m  " + (dy >= 0 ? "+" : "") + dy + "y";
        gg.drawString(mc.font, label,
            cx - mc.font.width(label) / 2,
            cy - (int)(R * FLAT) - 12, textRGB);
    }

    // --- helpers ---

    private static void drawArcSmoothApprox(GuiGraphics gg, int cx, int cy, float R, float flat, int argb, int thicknessPx) {
        int half = Math.max(1, thicknessPx / 2);
        for (int deg = -SWEEP_DEG; deg <= SWEEP_DEG; deg++) {
            float rad = (float) Math.toRadians(deg);
            int x = (int) (cx + Math.sin(rad) * R);
            int y = (int) (cy - Math.cos(rad) * R * flat);
            gg.fill(x - half, y - half, x + half, y + half, argb);
        }
    }

    private static void drawVerticalTicksSimple(GuiGraphics gg, int cx, int cy, float R, float flat,
                                                int everyDeg, int lenPx, int argb, int thicknessPx) {
        int halfW = Math.max(1, thicknessPx / 2);
        for (int deg = -SWEEP_DEG; deg <= SWEEP_DEG; deg += everyDeg) {
            float rad = (float) Math.toRadians(deg);
            int x = (int) (cx + Math.sin(rad) * R);
            int y = (int) (cy - Math.cos(rad) * R * flat);
            gg.fill(x - halfW, y - lenPx, x + halfW, y, argb);
        }
    }

    private static int oreColorFromName(String name) {
        String s = name.toLowerCase();
        boolean deep = s.contains("deepslate");
        if (s.contains("diamond"))   return deep ? 0xFF2CBFD0 : 0xFF3CF0FF;
        if (s.contains("iron"))      return deep ? 0xFF6E7C86 : 0xFFA0B6C0;
        if (s.contains("coal"))      return deep ? 0xFF4D4D4D : 0xFF666666;
        if (s.contains("gold"))      return deep ? 0xFFE0B800 : 0xFFFFD200;
        if (s.contains("redstone"))  return deep ? 0xFF9E2323 : 0xFFB52A2A;
        if (s.contains("emerald"))   return deep ? 0xFF00B85D : 0xFF0CC453;
        return 0xFFFFFFFF;
    }
}
