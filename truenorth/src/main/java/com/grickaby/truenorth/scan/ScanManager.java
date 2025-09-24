package com.grickaby.truenorth.scan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ScanManager {

    private static final int  MAX_TARGETS       = 5;     // how many markers to show
    private static final long SCAN_INTERVAL_MS  = 300;   // scan cadence

    // Include the block registry key so HUD can color by ore family if desired
    public record Target(BlockPos pos, ResourceLocation key, String displayName) {}

    private static final ScanManager INSTANCE = new ScanManager();
    public static ScanManager get() { return INSTANCE; }

    private final ScheduledExecutorService exec =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TrueNorth-Scanner");
            t.setDaemon(true);
            return t;
        });

    // Small list of nearest targets
    private final AtomicReference<List<Target>> currentList =
        new AtomicReference<>(List.of());

    public List<Target> currentTargets() { return currentList.get(); }

    private final AtomicReference<Target> current = new AtomicReference<>();
    private volatile Future<?> running;

    // Targets to scan for (includes deepslate variants)
    private volatile Set<ResourceLocation> targets = Set.of(
        // iron
        ResourceLocation.withDefaultNamespace("iron_ore"),
        ResourceLocation.withDefaultNamespace("deepslate_iron_ore"),
        // diamond
        ResourceLocation.withDefaultNamespace("diamond_ore"),
        ResourceLocation.withDefaultNamespace("deepslate_diamond_ore"),
        // coal
        ResourceLocation.withDefaultNamespace("coal_ore"),
        ResourceLocation.withDefaultNamespace("deepslate_coal_ore"),
        // gold
        ResourceLocation.withDefaultNamespace("gold_ore"),
        ResourceLocation.withDefaultNamespace("deepslate_gold_ore"),
        // redstone
        ResourceLocation.withDefaultNamespace("redstone_ore"),
        ResourceLocation.withDefaultNamespace("deepslate_redstone_ore"),
        // emerald
        ResourceLocation.withDefaultNamespace("emerald_ore"),
        ResourceLocation.withDefaultNamespace("deepslate_emerald_ore")
    );

    // Scan window around the player
    private volatile int radius     = 20;
    private volatile int yMinOffset = -5;
    private volatile int yMaxOffset =  5;

    private ScanManager() {
        exec.scheduleAtFixedRate(
            this::requestScan,
            0,
            SCAN_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    public Target currentTarget() { return current.get(); }

    private BlockPos lastPos = BlockPos.ZERO;
    private float lastYaw = 0f;

    private void requestScan() {
        var f = running;
        if (f != null && !f.isDone()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            var pos = mc.player.blockPosition();
            float yaw = mc.player.getYRot();
            // skip if you haven't moved ≥1 block and rotated <5°
            if (pos.distManhattan(lastPos) < 1 && Math.abs(yaw - lastYaw) < 5f) return;
            lastPos = pos;
            lastYaw = yaw;
        }
        running = exec.submit(this::scanNow);
    }

    private void scanNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        ClientLevel level = mc.level;
        BlockPos origin = mc.player.blockPosition();

        // Use dimension type for world Y bounds (1.21.8)
        var dim = level.dimensionType();
        int worldMinY = dim.minY();
        int worldMaxY = dim.minY() + dim.height() - 1;

        int yMin = Math.max(worldMinY, origin.getY() + yMinOffset);
        int yMax = Math.min(worldMaxY, origin.getY() + yMaxOffset);
        if (yMin > yMax) { 
            current.set(null); 
            currentList.set(List.of()); 
            return; 
        }

        // Snapshot radius and precompute r^2 for cheap circle check
        final int rad = this.radius;
        final int r2  = rad * rad;

        java.util.ArrayList<Target> found = new java.util.ArrayList<>();
        java.util.ArrayList<Double> dists = new java.util.ArrayList<>();

        // Early-exit threshold: if we already found enough very close ores (< 3 blocks)
        final double CLOSE_D2 = 3.0 * 3.0;

        // Expand by rings of chunks around the player
        scanLoop:
        for (int r = 0; r <= rad; r += 16) {
            for (BlockPos bp : ring(origin, r)) {
                int cx = bp.getX() >> 4, cz = bp.getZ() >> 4;
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                int baseX = cx << 4, baseZ = cz << 4;

                for (int dx = 0; dx < 16; dx++) {
                    int wx = baseX + dx;
                    for (int dz = 0; dz < 16; dz++) {
                        int wz = baseZ + dz;

                        int ddx = wx - origin.getX();
                        int ddz = wz - origin.getZ();
                        if ((ddx * ddx + ddz * ddz) > r2) continue;

                        for (int y = yMin; y <= yMax; y++) {
                            BlockPos pos = new BlockPos(wx, y, wz);
                            BlockState st = chunk.getBlockState(pos);
                            Block b = st.getBlock();
                            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(b);
                            if (key == null || !targets.contains(key)) continue;

                            double d2 = pos.distSqr(origin);
                            String name = key.getPath().replace('_', ' ');
                            found.add(new Target(pos.immutable(), key, name));
                            dists.add(d2);

                            // If we've already got enough very close hits, stop scanning
                            if (found.size() >= MAX_TARGETS && d2 < CLOSE_D2) {
                                break scanLoop;
                            }
                        }
                    }
                }
            }
        }

        if (found.isEmpty()) {
            current.set(null);
            currentList.set(List.of());
            return;
        }

        // Sort by distance (ascending)
        java.util.List<Integer> idx = new java.util.ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) idx.add(i);
        idx.sort(java.util.Comparator.comparingDouble(dists::get));

        // Take top N
        java.util.ArrayList<Target> top = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(MAX_TARGETS, idx.size()); i++) {
            top.add(found.get(idx.get(i)));
        }

        current.set(top.get(0)); // preserve old API
        currentList.set(java.util.Collections.unmodifiableList(top));
    }

    private static List<BlockPos> ring(BlockPos c, int r) {
        if (r == 0) return List.of(c);
        return List.of(
            c.offset( r, 0,  0),
            c.offset(-r, 0,  0),
            c.offset( 0, 0,  r),
            c.offset( 0, 0, -r),
            c.offset( r, 0,  r),
            c.offset( r, 0, -r),
            c.offset(-r, 0,  r),
            c.offset(-r, 0, -r)
        );
    }

    public void setTargets(Set<ResourceLocation> newTargets) {
        this.targets = Set.copyOf(newTargets);
        requestScan();
    }

    public void shutdown() { exec.shutdownNow(); }
}
