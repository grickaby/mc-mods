// src/main/java/com/yourname/truenorth/scan/ScanManager.java
package com.yourname.truenorth.scan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ScanManager {

    public record Target(BlockPos pos, String displayName) {}

    private static final ScanManager INSTANCE = new ScanManager();
    public static ScanManager get() { return INSTANCE; }

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TrueNorth-Scanner");
                t.setDaemon(true);
                return t;
            });

    private final AtomicReference<Target> current = new AtomicReference<>();
    private volatile Future<?> running;

    // Defaults: iron & diamond; user config later
    private volatile Set<ResourceLocation> targets = Set.of(
            ResourceLocation.withDefaultNamespace("iron_ore"),
            ResourceLocation.withDefaultNamespace("deepslate_iron_ore"),
            ResourceLocation.withDefaultNamespace("diamond_ore"),
            ResourceLocation.withDefaultNamespace("deepslate_diamond_ore")
    );

    private ScanManager() {
        // rescan every 2 seconds
        exec.scheduleAtFixedRate(this::tickScan, 0, 40, TimeUnit.MILLISECONDS); // quick internal tick
        exec.scheduleAtFixedRate(this::requestScan, 0, 2, TimeUnit.SECONDS);
    }

    public Target currentTarget() { return current.get(); }

    private void requestScan() {
        if (running != null && !running.isDone()) return;
        running = exec.submit(this::scanNow);
    }

    private void tickScan() {
        // reserved for progressive spiral updates if needed
    }

    private void scanNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        ClientLevel level = mc.level;
        BlockPos origin = mc.player.blockPosition();

        int radius = 64; // configurable
        int yMin = Math.max(level.getMinBuildHeight(), origin.getY() - 32);
        int yMax = Math.min(level.getMaxBuildHeight(), origin.getY() + 16);

        Target best = null;
        double bestDist = Double.MAX_VALUE;

        // Spiral over chunks and within them only if loaded
        for (int r = 0; r <= radius; r += 16) {
            for (BlockPos bp : ring(origin, r)) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4);
                if (chunk == null) continue;
                // Iterate a coarse grid, then refine
                for (int x = 0; x < 16; x++) {
                    int wx = (chunk.getPos().x << 4) + x;
                    for (int z = 0; z < 16; z++) {
                        int wz = (chunk.getPos().z << 4) + z;
                        for (int y = yMin; y <= yMax; y++) {
                            BlockPos pos = new BlockPos(wx, y, wz);
                            BlockState st = chunk.getBlockState(pos);
                            Block b = st.getBlock();
                            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(b);
                            if (key != null && targets.contains(key)) {
                                double d = pos.distSqr(origin);
                                if (d < bestDist) {
                                    String name = key.toString().substring(key.getNamespace().length() + 1).replace('_', ' ');
                                    bestDist = d;
                                    best = new Target(pos.immutable(), name);
                                }
                            }
                        }
                    }
                }
                if (best != null) {
                    current.set(best);
                    return;
                }
            }
        }
        current.set(null);
    }

    private static List<BlockPos> ring(BlockPos center, int r) {
        // Quick ring of 8 points per “radius step” (cheap and good enough)
        if (r == 0) return List.of(center);
        return List.of(
                center.offset( r, 0,  0),
                center.offset(-r, 0,  0),
                center.offset( 0, 0,  r),
                center.offset( 0, 0, -r),
                center.offset( r, 0,  r),
                center.offset( r, 0, -r),
                center.offset(-r, 0,  r),
                center.offset(-r, 0, -r)
        );
    }

    public void setTargets(Set<ResourceLocation> newTargets) {
        this.targets = Set.copyOf(newTargets);
        requestScan();
    }

    public void shutdown() {
        exec.shutdownNow();
    }
}
