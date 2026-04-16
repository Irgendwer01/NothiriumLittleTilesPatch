package ru.V5Minecraft.NothiriumLittleTilesPatch.NothiriumPatches;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import meldexun.nothirium.util.Direction;
import meldexun.nothirium.util.VisibilityGraph;

public class LittleTilesIntegration {

    private static final int BLOCK_VERTEX_STRIDE = 28;

    private static final int MAX_RETRY_TICKS = 120;

    private static boolean initialized = false;
    private static boolean available = false;

    private static Class<?> blockTileClass = null;
    private static Class<?> teLittleTilesClass;

    private static Field renderField;
    private static Field requestedIndexField;
    private static Field buildingField;
    private static Method getBufferCacheMethod;
    private static Method chunkUpdateMethod;

    private static Field queueField;

    private static Method byteBufferMethod;
    private static Method lengthMethod;

    private static Method growMethod;

    private static Field byteBufferField;
    private static Field vertexCountField;

    private static Method updateQuadCacheMethod;

    private static final AtomicInteger blockTileSetOpaqueCount = new AtomicInteger(0);
    private static final AtomicInteger blockTileDoesCount = new AtomicInteger(0);
    private static volatile boolean logged = false;

    private static final ThreadLocal<IBlockState> currentBlockState = new ThreadLocal<>();

    private static final ConcurrentHashMap<Long, Integer> pendingSections = new ConcurrentHashMap<>();

    private static long packSection(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 34) | ((long) (y & 0xFFF) << 22) | (z & 0x3FFFFF);
    }

    private static void scheduleDirty(int sectionX, int sectionY, int sectionZ) {
        long key = packSection(sectionX, sectionY, sectionZ);
        pendingSections.merge(key, MAX_RETRY_TICKS, Math::max);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            blockTileClass = Class.forName("com.creativemd.littletiles.common.block.BlockTile");

            teLittleTilesClass = Class.forName("com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles");
            renderField = teLittleTilesClass.getField("render");

            Class<?> renderManagerClass = Class
                    .forName("com.creativemd.littletiles.client.render.world.TileEntityRenderManager");
            getBufferCacheMethod = renderManagerClass.getMethod("getBufferCache");
            requestedIndexField = findField(renderManagerClass, "requestedIndex");
            buildingField = findField(renderManagerClass, "building");
            chunkUpdateMethod = renderManagerClass.getMethod("chunkUpdate", Object.class);

            Class<?> bufferCacheClass = Class
                    .forName("com.creativemd.littletiles.client.render.cache.LayeredRenderBufferCache");
            queueField = findField(bufferCacheClass, "queue");

            Class<?> renderDataCacheInterface = Class
                    .forName("com.creativemd.littletiles.client.render.cache.IRenderDataCache");
            byteBufferMethod = renderDataCacheInterface.getMethod("byteBuffer");
            lengthMethod = renderDataCacheInterface.getMethod("length");

            Class<?> utils = Class.forName("com.creativemd.creativecore.client.rendering.model.BufferBuilderUtils");
            growMethod = utils.getMethod("growBufferSmall", BufferBuilder.class, int.class);

            byteBufferField = findField(BufferBuilder.class, "byteBuffer", "field_179001_a");
            vertexCountField = findField(BufferBuilder.class, "vertexCount", "field_178997_d");

            updateQuadCacheMethod = teLittleTilesClass.getMethod("updateQuadCache", Object.class);

            available = blockTileClass != null && queueField != null && byteBufferField != null &&
                    vertexCountField != null && requestedIndexField != null && buildingField != null &&
                    updateQuadCacheMethod != null;

        } catch (ClassNotFoundException e) {} catch (Exception e) {}
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    public static boolean isBlockTile(IBlockState blockState) {
        if (!initialized) init();
        return blockTileClass != null && blockTileClass.isAssignableFrom(blockState.getBlock().getClass());
    }

    public static void beginRenderBlock(IBlockState blockState) {
        if (!initialized) init();
        currentBlockState.set(blockState);
    }

    public static void endRenderBlock() {
        currentBlockState.set(null);
    }

    public static void setOpaque(VisibilityGraph graph, int x, int y, int z, Direction dir) {
        IBlockState blockState = currentBlockState.get();
        if (blockState != null && isBlockTile(blockState)) {
            blockTileSetOpaqueCount.incrementAndGet();
            return;
        }
        graph.setOpaque(x, y, z, dir);
    }

    public static boolean doesSideBlockRendering(IBlockState blockState, IBlockAccess world, BlockPos pos,
                                                 EnumFacing facing) {
        if (!initialized) init();
        if (isBlockTile(blockState)) {
            blockTileDoesCount.incrementAndGet();
            return false;
        }
        return blockState.doesSideBlockRendering(world, pos, facing);
    }

    public static boolean shouldSkipBlockRender(IBlockState blockState) {
        if (!initialized) init();
        return isBlockTile(blockState);
    }

    public static void appendLittleTilesData(IBlockAccess chunkCache, int baseX, int baseY, int baseZ,
                                             RegionRenderCacheBuilder buffers) {
        if (!initialized) init();

        int setOpaqueCount = blockTileSetOpaqueCount.getAndSet(0);
        int doesCount = blockTileDoesCount.getAndSet(0);
        if ((setOpaqueCount > 0 || doesCount > 0) && !logged) {
            logged = true;
        }

        if (!available) return;

        World world = getWorld(chunkCache);
        if (world == null) return;

        int sectionX = baseX >> 4;
        int sectionY = baseY >> 4;
        int sectionZ = baseZ >> 4;
        boolean hasLTBlocks = false;
        boolean anyNotReady = false;

        for (int x = baseX; x < baseX + 16; x++)
            for (int y = baseY; y < baseY + 16; y++)
                for (int z = baseZ; z < baseZ + 16; z++) {
                    TileEntity te;
                    try {
                        te = world.getTileEntity(new BlockPos(x, y, z));
                    } catch (Exception e) {
                        continue;
                    }
                    if (te == null || !teLittleTilesClass.isInstance(te)) continue;

                    hasLTBlocks = true;

                    try {
                        Object renderManager = renderField.get(te);
                        if (renderManager == null) {
                            anyNotReady = true;
                            continue;
                        }

                        int requestedIndex = requestedIndexField.getInt(renderManager);
                        boolean building = buildingField.getBoolean(renderManager);

                        if (requestedIndex == -1 || building) {
                            anyNotReady = true;
                            if (chunkUpdateMethod != null) {
                                try {
                                    chunkUpdateMethod.invoke(renderManager, (Object) null);
                                } catch (Exception ignored) {}
                            }
                            if (requestedIndex == -1 && !building && updateQuadCacheMethod != null) {
                                try {
                                    updateQuadCacheMethod.invoke(te, (Object) null);
                                } catch (Exception ignored) {}
                            }
                            continue;
                        }

                        Object bufferCache = getBufferCacheMethod.invoke(renderManager);
                        if (bufferCache == null) continue;

                        Object[] queue = (Object[]) queueField.get(bufferCache);
                        if (queue == null) {
                            anyNotReady = true;
                            continue;
                        }

                        boolean hasData = false;
                        for (int i = 0; i < queue.length; i++) {
                            if (queue[i] != null) {
                                hasData = true;
                                break;
                            }
                        }
                        if (!hasData) {
                            anyNotReady = true;
                            if (chunkUpdateMethod != null) {
                                try {
                                    chunkUpdateMethod.invoke(renderManager, (Object) null);
                                } catch (Exception ignored) {}
                            }
                            continue;
                        }

                    } catch (Exception e) {
                        continue;
                    }

                    appendTileData(te, baseX, baseY, baseZ, buffers);
                }

        if (hasLTBlocks) {
            if (anyNotReady) {
                scheduleDirty(sectionX, sectionY, sectionZ);
            } else {
                long key = packSection(sectionX, sectionY, sectionZ);
                pendingSections.remove(key);
            }
        }
    }

    private static World getWorld(IBlockAccess chunkCache) {
        if (chunkCache instanceof World) return (World) chunkCache;
        try {
            Field f = chunkCache.getClass().getDeclaredField("world");
            f.setAccessible(true);
            return (World) f.get(chunkCache);
        } catch (Exception e) {
            return null;
        }
    }

    public static void onWorldUnload() {
        pendingSections.clear();
    }

    private static void appendTileData(TileEntity te, int baseX, int baseY, int baseZ,
                                       RegionRenderCacheBuilder buffers) {
        try {
            Object renderManager = renderField.get(te);
            if (renderManager == null) return;
            Object bufferCache = getBufferCacheMethod.invoke(renderManager);
            if (bufferCache == null) return;

            Object[] queue = (Object[]) queueField.get(bufferCache);
            if (queue == null) return;

            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (layer == BlockRenderLayer.TRANSLUCENT) continue;

                int layerIdx = layer.ordinal();
                if (layerIdx >= queue.length) continue;

                Object renderData = queue[layerIdx];
                if (renderData == null) continue;

                ByteBuffer src = (ByteBuffer) byteBufferMethod.invoke(renderData);
                if (src == null) continue;

                int length = (int) lengthMethod.invoke(renderData);
                if (length <= 0) continue;

                BufferBuilder builder = buffers.getWorldRendererByLayer(layer);

                if (!builder.isDrawing) {
                    builder.begin(7, DefaultVertexFormats.BLOCK);
                    builder.setTranslation(-baseX, -baseY, -baseZ);
                }

                growMethod.invoke(null, builder, length);

                ByteBuffer dest = (ByteBuffer) byteBufferField.get(builder);
                int currentCount = vertexCountField.getInt(builder);
                int written = currentCount * BLOCK_VERTEX_STRIDE;

                dest.position(written);
                src.position(0);
                src.limit(length);
                dest.put(src);

                int added = length / BLOCK_VERTEX_STRIDE;
                vertexCountField.setInt(builder, currentCount + added);
            }
        } catch (Exception e) {}
    }
}
