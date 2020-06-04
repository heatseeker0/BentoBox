package world.bentobox.bentobox.managers.island;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.util.Util;

/**
 * The default strategy for generating locations for island
 * 
 * @author tastybento, leonardochaia
 * @since 1.8.0
 *
 */
public class DefaultNewIslandLocationStrategy implements NewIslandLocationStrategy {

    /**
     * The amount times to tolerate island check returning blocks without kwnon
     * island.
     */
    protected static final Integer MAX_UNOWNED_ISLANDS = 20;

    protected enum Result {
        ISLAND_FOUND,
        BLOCKS_IN_AREA,
        FREE
    }

    protected BentoBox plugin = BentoBox.getInstance();

    @Override
    public Location getNextLocation(World world) {
        Location last = plugin.getIslands().getLast(world);
        if (last == null) {
            last = new Location(world, (double) plugin.getIWM().getIslandXOffset(world) + plugin.getIWM().getIslandStartX(world), plugin.getIWM().getIslandHeight(
                world), (double) plugin.getIWM().getIslandZOffset(world) + plugin.getIWM().getIslandStartZ(world));
        }

        // Find a free spot
        Map<Result, Integer> result = new EnumMap<>(Result.class);
        // Check center

        // TODO: This does a lot of boxing and unboxing, potential for optimization here
        Result r = isIsland(last);
        while (!r.equals(Result.FREE) && result.getOrDefault(Result.BLOCKS_IN_AREA, 0) < MAX_UNOWNED_ISLANDS) {
            nextGridLocation(last);
            result.put(r, result.getOrDefault(r, 0) + 1);
            r = isIsland(last);
        }

        if (!r.equals(Result.FREE)) {
            // We could not find a free spot within the limit required. It's likely this
            // world is not empty
            plugin.logError("Could not find a free spot for islands! Is this world empty?");
            plugin.logError("Blocks around center locations: " + result.getOrDefault(Result.BLOCKS_IN_AREA, 0) + " max " + MAX_UNOWNED_ISLANDS);
            plugin.logError("Known islands: " + result.getOrDefault(Result.ISLAND_FOUND, 0) + " max unlimited.");
            return null;
        }
        plugin.getIslands().setLast(last);
        return last;
    }

    /**
     * Checks if there is an island or blocks at this location
     *
     * @param location - the location
     * @return Result enum if island found, null if blocks found, false if nothing found
     */
    protected Result isIsland(Location location) {
        if (location == null) {
            return Result.BLOCKS_IN_AREA;
        }

        // Quick check
        if (plugin.getIslands().getIslandAt(location).isPresent()) {
            return Result.ISLAND_FOUND;
        }

        World world = location.getWorld();
        if (world == null) {
            return Result.BLOCKS_IN_AREA;
        }

        // Check 4 corners
        int dist = plugin.getIWM().getIslandDistance(world);
        Set<Location> locs = new HashSet<>();
        locs.add(location);

        locs.add(new Location(world, location.getX() - dist, 0, location.getZ() - dist));
        locs.add(new Location(world, location.getX() - dist, 0, location.getZ() + dist - 1));
        locs.add(new Location(world, location.getX() + dist - 1, 0, location.getZ() - dist));
        locs.add(new Location(world, location.getX() + dist - 1, 0, location.getZ() + dist - 1));

        for (Location l : locs) {
            if (plugin.getIslands().getIslandAt(l).isPresent() || plugin.getIslandDeletionManager().inDeletion(l)) {
                return Result.ISLAND_FOUND;
            }

            // If chunk has not been generated yet, then it's not occupied
            if (!Util.isChunkGenerated(l)) {
                return Result.FREE;
            }
        }

        if (plugin.getIWM().isUseOwnGenerator(world)) {
            return Result.FREE;
        }

        List<CompletableFuture<Result>> futures = new ArrayList<>();

        int locX = location.getBlockX();
        int locZ = location.getBlockZ();

        for (BlockFace face : BlockFace.values()) {
            // We need to load each async because different block faces can be part of different chunks. This ensures *all* chunk loads are async.
            futures.add(Util.getChunkAtAsync(world, locX + face.getModX(), locZ + face.getModZ()).thenApply(r -> {
                Block block = location.getBlock().getRelative(face);
                if (!block.isEmpty() || (block.getType() != Material.WATER)) {
                    // Block found. Mark this area as belonging to an island.
                    plugin.getIslands().createIsland(location);
                    return Result.BLOCKS_IN_AREA;
                }

                return Result.FREE;
            }));
        }

        try {
            // And finally calculate the outcome sync using short-circuit evaluation for even more speed gains
            for (CompletableFuture<Result> future : futures) {
                if (future.get() == Result.BLOCKS_IN_AREA) {
                    return Result.BLOCKS_IN_AREA;
                }
            }

            return Result.FREE;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();

            return Result.BLOCKS_IN_AREA;
        }
    }

    /**
     * Finds the next free island spot based off the last known island Uses
     * island_distance setting from the config file Builds up in a grid fashion
     *
     * @param lastIsland - last island location
     * @return Location of next free island
     */
    private Location nextGridLocation(final Location lastIsland) {
        int x = lastIsland.getBlockX();
        int z = lastIsland.getBlockZ();
        int d = plugin.getIWM().getIslandDistance(lastIsland.getWorld()) * 2;
        if (x < z) {
            if (-1 * x < z) {
                lastIsland.setX(lastIsland.getX() + d);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() + d);
            return lastIsland;
        }
        if (x > z) {
            if (-1 * x >= z) {
                lastIsland.setX(lastIsland.getX() - d);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() - d);
            return lastIsland;
        }
        if (x <= 0) {
            lastIsland.setZ(lastIsland.getZ() + d);
            return lastIsland;
        }
        lastIsland.setZ(lastIsland.getZ() - d);
        return lastIsland;
    }
}
