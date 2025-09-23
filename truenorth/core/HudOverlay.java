// src/main/java/com/yourname/truenorth/client/HudOverlay.java
package com.yourname.truenorth.client;

import com.yourname.truenorth.scan.ScanManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class HudOverlay {

    public static void render(GuiGraphics gg, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        LocalPlayer p = mc.player;
        ScanManager.Target current = ScanManager.get().currentTarget();
        if (current == null) return;

        Vec3 eye = p.getEyePosition(partialTick);
        Vec3 to = Vec3.atCenterOf(current.pos());
        double dx = to.x - eye.x;
        double dz = to.z - eye.z;

        // yaw arrow angle in degrees (screen space)
        float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot());
        // Normalize [-180,180]
        while (yaw > 180) yaw -= 360;
        while (yaw < -180) yaw += 360;

        int w = gg.guiWidth();
        int h = gg.guiHeight();

        int cx = w / 2;
        int cy = h - 40; // above hotbar

        // Draw a simple triangle arrow and distance text
        gg.fill(cx - 2, cy - 22, cx + 2, cy - 8, 0xA0000000); // stem
        // arrow head (simple)
        gg.fill(cx - 5, cy - 12, cx + 5, cy - 8, 0xA0FFFFFF);

        // Rotate the pose to indicate direction (coarse: show left/right indicator)
        // Cheap hint: small bars left/right
        if (yaw > 10) gg.fill(cx + 20, cy - 16, cx + 26, cy - 10, 0xA0FFFFFF);
        else if (yaw < -10) gg.fill(cx - 26, cy - 16, cx - 20, cy - 10, 0xA0FFFFFF);

        String label = current.displayName() + " " + (int)eye.distanceTo(to) + "m";
        gg.drawString(mc.font, label, cx - mc.font.width(label)/2, cy - 28, 0xFFFFFF);
    }
}
