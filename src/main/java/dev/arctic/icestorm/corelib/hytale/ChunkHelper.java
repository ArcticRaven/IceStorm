package dev.arctic.icestorm.corelib.hytale;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.Objects;

/**
 * Utility helpers for working with chunk-style coordinate grids in Hytale.
 *
 * <p>Hytale world chunks are {@value #DEFAULT_CHUNK_SIZE} meters wide. However, many systems
 * benefit from treating the world as a grid at different scales:</p>
 *
 * <ul>
 *   <li>Sub-chunks (smaller partitions for fine-grained logic)</li>
 *   <li>Mega-chunks (larger partitions for caching or region claims)</li>
 *   <li>Custom cell sizes for gameplay systems</li>
 * </ul>
 *
 * <p>All methods default to the standard chunk size unless an explicit scale is provided.</p>
 */
public final class  ChunkHelper {

    private ChunkHelper() {}

    /**
     * Standard Hytale chunk size in world meters.
     */
    public static final int DEFAULT_CHUNK_SIZE = 32;

    /**
     * Computes the chunk X coordinate for a world X position
     * using the standard {@value #DEFAULT_CHUNK_SIZE} scale.
     *
     * @param worldX world X position in meters
     * @return chunk X coordinate
     */
    public static int toChunkX(double worldX) {
        return toChunkX(worldX, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Computes the chunk Z coordinate for a world Z position
     * using the standard {@value #DEFAULT_CHUNK_SIZE} scale.
     *
     * @param worldZ world Z position in meters
     * @return chunk Z coordinate
     */
    public static int toChunkZ(double worldZ) {
        return toChunkZ(worldZ, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Computes the chunk X coordinate for a world X position at an arbitrary grid scale.
     *
     * @param worldX world X position in meters
     * @param chunkSize size of one grid cell in meters (must be > 0)
     * @return chunk X coordinate
     */
    public static int toChunkX(double worldX, int chunkSize) {
        return floorDiv((int) Math.floor(worldX), requireChunkSize(chunkSize));
    }

    /**
     * Computes the chunk Z coordinate for a world Z position at an arbitrary grid scale.
     *
     * @param worldZ world Z position in meters
     * @param chunkSize size of one grid cell in meters (must be > 0)
     * @return chunk Z coordinate
     */
    public static int toChunkZ(double worldZ, int chunkSize) {
        return floorDiv((int) Math.floor(worldZ), requireChunkSize(chunkSize));
    }

    /**
     * Computes chunk coordinates from a world position using the standard chunk size.
     *
     * @param position world position
     * @return chunk coordinates as an {@link IntPair}
     */
    public static IntPair toChunkCoords(Vector3d position) {
        return toChunkCoords(position, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Computes chunk coordinates from a world position at an arbitrary grid scale.
     *
     * @param position world position
     * @param chunkSize size of one grid cell in meters (must be > 0)
     * @return chunk coordinates as an {@link IntPair}
     */
    public static IntPair toChunkCoords(Vector3d position, int chunkSize) {
        Objects.requireNonNull(position, "position");
        int size = requireChunkSize(chunkSize);

        int chunkX = floorDiv((int) Math.floor(position.x), size);
        int chunkZ = floorDiv((int) Math.floor(position.z), size);

        return new IntPair(chunkX, chunkZ);
    }

    /**
     * Computes chunk coordinates from a transform using the standard chunk size.
     *
     * @param transform transform containing world position
     * @return chunk coordinates as an {@link IntPair}
     */
    public static IntPair toChunkCoords(Transform transform) {
        return toChunkCoords(transform, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Computes chunk coordinates from a transform at an arbitrary grid scale.
     *
     * @param transform transform containing world position
     * @param chunkSize size of one grid cell in meters (must be > 0)
     * @return chunk coordinates as an {@link IntPair}
     */
    public static IntPair toChunkCoords(Transform transform, int chunkSize) {
        Objects.requireNonNull(transform, "transform");
        return toChunkCoords(transform.getPosition(), chunkSize);
    }

    /**
     * Computes the world-space origin (minimum corner) of a chunk cell
     * using the standard chunk size.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return world-space origin as an {@link IntPair}
     */
    public static IntPair chunkOrigin(int chunkX, int chunkZ) {
        return chunkOrigin(chunkX, chunkZ, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Computes the world-space origin (minimum corner) of a chunk cell
     * at an arbitrary grid scale.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @param chunkSize size of one grid cell in meters (must be > 0)
     * @return world-space origin as an {@link IntPair}
     */
    public static IntPair chunkOrigin(int chunkX, int chunkZ, int chunkSize) {
        int size = requireChunkSize(chunkSize);
        return new IntPair(chunkX * size, chunkZ * size);
    }

    /**
     * Computes world-space bounds for a chunk cell using the standard chunk size.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return bounds in world-space
     */
    public static ChunkBounds bounds(int chunkX, int chunkZ) {
        return bounds(chunkX, chunkZ, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Computes world-space bounds for a chunk cell at an arbitrary grid scale.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @param chunkSize size of one grid cell in meters (must be > 0)
     * @return bounds in world-space
     */
    public static ChunkBounds bounds(int chunkX, int chunkZ, int chunkSize) {
        int size = requireChunkSize(chunkSize);

        double minX = chunkX * (double) size;
        double minZ = chunkZ * (double) size;

        return new ChunkBounds(
                minX,
                minX + size,
                minZ,
                minZ + size
        );
    }

    /**
     * Integer floor division consistent with negative coordinates.
     *
     * @param value dividend
     * @param divisor divisor (must be non-zero)
     * @return floor(value / divisor)
     */
    public static int floorDiv(int value, int divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("divisor cannot be 0.");
        }

        int quotient = value / divisor;
        int remainder = value % divisor;
        if (remainder != 0 && ((value ^ divisor) < 0)) {
            quotient--;
        }
        return quotient;
    }

    private static int requireChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0.");
        }
        return chunkSize;
    }

    /**
         * Simple integer pair for returning chunk coordinates.
         */
        public record IntPair(int x, int z) {

        @Override
            public String toString() {
                return "IntPair{x=" + x + ", z=" + z + "}";
            }
        }

    /**
         * World-space bounds of a single chunk cell in the XZ plane.
         */
        public record ChunkBounds(double minX, double maxX, double minZ, double maxZ) {

        @Override
            public String toString() {
                return "ChunkBounds{minX=" + minX + ", maxX=" + maxX +
                        ", minZ=" + minZ + ", maxZ=" + maxZ + "}";
            }
        }
}
