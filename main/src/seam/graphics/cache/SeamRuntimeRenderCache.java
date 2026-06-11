package seam.graphics.cache;

import seam.graphics.invalidation.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.world.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;

public final class SeamRuntimeRenderCache{
    private final SeamRuntime runtime;

    private final Rect worldBounds = new Rect();
    private final Rect queryRuntimeBounds = new Rect();

    private final Seq<Tile> tileView = new Seq<>(false, 32 * 32, Tile.class);
    private final Seq<Tile> lightView = new Seq<>(false, 32 * 32, Tile.class);

    private final Seq<Building> outBuildings = new Seq<>();

    private final IntSet procLinks = new IntSet();
    private final IntSet procLights = new IntSet();

    private final IntMap<CachedTile> blockEntries = new IntMap<>();
    private final IntMap<CachedTile> blockLightEntries = new IntMap<>();
    private final IntMap<CachedTile> overlayEntries = new IntMap<>();
    private final IntMap<CachedTile> floorEntries = new IntMap<>();

    private QuadTree<CachedTile> blockTree;
    private QuadTree<CachedTile> blockLightTree;
    private QuadTree<CachedTile> overlayTree;
    private QuadTree<CachedTile> floorTree;

    private long version;
    private boolean built;

    public SeamRuntimeRenderCache(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;

        rebuild();
    }

    public SeamRuntime runtime(){
        return runtime;
    }

    public long version(){
        return version;
    }

    public boolean built(){
        return built;
    }

    public void rebuild(){
        runtime.requireWorldReady();

        worldBounds.set(0f, 0f, runtime.world.unitWidth(), runtime.world.unitHeight());

        blockTree = new QuadTree<>(new Rect(worldBounds));
        blockLightTree = new QuadTree<>(new Rect(worldBounds));
        overlayTree = new QuadTree<>(new Rect(worldBounds));
        floorTree = new QuadTree<>(new Rect(worldBounds));

        blockEntries.clear();
        blockLightEntries.clear();
        overlayEntries.clear();
        floorEntries.clear();

        for(Tile tile : runtime.world.tiles){
            recordIndex(tile);
        }

        built = true;
        version++;
    }

    public void clear(){
        blockTree = new QuadTree<>(new Rect(0f, 0f, 1f, 1f));
        blockLightTree = new QuadTree<>(new Rect(0f, 0f, 1f, 1f));
        overlayTree = new QuadTree<>(new Rect(0f, 0f, 1f, 1f));
        floorTree = new QuadTree<>(new Rect(0f, 0f, 1f, 1f));

        blockEntries.clear();
        blockLightEntries.clear();
        overlayEntries.clear();
        floorEntries.clear();

        tileView.clear();
        lightView.clear();
        procLinks.clear();
        procLights.clear();
        outBuildings.clear();

        built = false;
        version++;
    }

    public Seq<SeamRenderInvalidation> applyPendingInvalidations(){
        Seq<SeamRenderInvalidation> invalidations = runtime.renderInvalidation.drain(runtime);

        applyInvalidations(invalidations);

        return invalidations;
    }

    public void applyInvalidations(Seq<SeamRenderInvalidation> invalidations){
        runtime.requireWorldReady();

        if(invalidations == null || invalidations.isEmpty()){
            return;
        }

        for(SeamRenderInvalidation invalidation : invalidations){
            if(invalidation.full()){
                rebuild();
                return;
            }
        }

        for(SeamRenderInvalidation invalidation : invalidations){
            reindexAround(invalidation.x, invalidation.y, invalidation.radius);
        }

        version++;
    }

    public SeamRenderViewBatch query(SeamView view, Rect hostBounds){
        if(view == null){
            throw new NullPointerException("view");
        }

        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        if(view.runtimeId() != runtime.id){
            return SeamRenderViewBatch.failure(view.id(), view.runtimeId(), "view runtime does not match cache runtime");
        }

        if(!view.renderable()){
            return SeamRenderViewBatch.failure(view.id(), view.runtimeId(), "view is not renderable");
        }

        if(!runtime.worldReady()){
            return SeamRenderViewBatch.failure(view.id(), view.runtimeId(), "runtime world is not ready");
        }

        if(!built){
            rebuild();
        }

        applyPendingInvalidations();

        view.projection().runtimeBounds(runtime, hostBounds, Vars.tilesize * 3f, queryRuntimeBounds);

        tileView.clear();
        lightView.clear();
        procLinks.clear();
        procLights.clear();
        outBuildings.clear();

        floorTree.intersect(queryRuntimeBounds, entry -> {
            lightView.add(entry.tile);
        });

        overlayTree.intersect(queryRuntimeBounds, entry -> {
            lightView.add(entry.tile);
        });

        blockLightTree.intersect(queryRuntimeBounds, entry -> {
            Tile tile = entry.tile;
            Building build = tile.build;

            if(build == null || procLights.add(build.pos())){
                lightView.add(tile);
            }
        });

        blockTree.intersect(queryRuntimeBounds, entry -> {
            Tile tile = entry.tile;
            Building build = tile.build;

            if(build == null || procLinks.add(build.id)){
                tileView.add(tile);
            }

            if(build != null && build.power != null && build.power.links.size > 0){
                outBuildings.clear();

                for(Building other : build.getPowerConnections(outBuildings)){
                    if(other != null && other.block instanceof PowerNode && procLinks.add(other.id)){
                        tileView.add(other.tile);
                    }
                }
            }
        });

        return SeamRenderViewBatch.success(
        view,
        hostBounds,
        queryRuntimeBounds,
        tileView,
        lightView,
        version
        );
    }

    private void reindexAround(int centerX, int centerY, int radius){
        int minX = Math.max(0, centerX - radius);
        int minY = Math.max(0, centerY - radius);
        int maxX = Math.min(runtime.world.width() - 1, centerX + radius);
        int maxY = Math.min(runtime.world.height() - 1, centerY + radius);

        for(int x = minX; x <= maxX; x++){
            for(int y = minY; y <= maxY; y++){
                Tile tile = runtime.world.tile(x, y);

                if(tile == null){
                    continue;
                }

                removeIndex(tile);
                recordIndex(tile);
            }
        }
    }

    private void removeIndex(Tile tile){
        if(tile == null){
            return;
        }

        removeEntry(blockTree, blockEntries, tile.pos());
        removeEntry(blockLightTree, blockLightEntries, tile.pos());
        removeEntry(overlayTree, overlayEntries, tile.pos());
        removeEntry(floorTree, floorEntries, tile.pos());
    }

    private void removeEntry(QuadTree<CachedTile> tree, IntMap<CachedTile> entries, int tilePos){
        CachedTile entry = entries.remove(tilePos);

        if(entry != null){
            tree.remove(entry);
        }
    }

    private void recordIndex(Tile tile){
        if(tile == null){
            return;
        }

        if(indexBlock(tile)){
            CachedTile block = CachedTile.block(tile);
            CachedTile light = CachedTile.blockLight(tile);

            blockEntries.put(tile.pos(), block);
            blockLightEntries.put(tile.pos(), light);

            blockTree.insert(block);
            blockLightTree.insert(light);
        }

        if(indexOverlay(tile)){
            CachedTile overlay = CachedTile.overlay(tile);

            overlayEntries.put(tile.pos(), overlay);
            overlayTree.insert(overlay);
        }

        if(indexFloor(tile)){
            CachedTile floor = CachedTile.floor(tile);

            floorEntries.put(tile.pos(), floor);
            floorTree.insert(floor);
        }
    }

    private boolean indexBlock(Tile tile){
        Block block = tile.block();

        return tile.isCenter()
        && block != null
        && block != Blocks.air
        && block.cacheLayer == CacheLayer.normal;
    }

    private boolean indexOverlay(Tile tile){
        return tile.block() != null
        && tile.overlay() != null
        && !tile.block().obstructsLight
        && tile.overlay().emitLight
        && runtime.world.getDarkness(tile.x, tile.y) < 3;
    }

    private boolean indexFloor(Tile tile){
        return tile.block() != null
        && tile.floor() != null
        && !tile.block().obstructsLight
        && tile.floor().emitLight
        && runtime.world.getDarkness(tile.x, tile.y) < 3;
    }

    private enum EntryKind{
        block,
        blockLight,
        overlay,
        floor
    }

    private static final class CachedTile implements QuadTree.QuadTreeObject{
        final Tile tile;
        final EntryKind kind;

        CachedTile(Tile tile, EntryKind kind){
            if(tile == null){
                throw new NullPointerException("tile");
            }

            if(kind == null){
                throw new NullPointerException("kind");
            }

            this.tile = tile;
            this.kind = kind;
        }

        static CachedTile block(Tile tile){
            return new CachedTile(tile, EntryKind.block);
        }

        static CachedTile blockLight(Tile tile){
            return new CachedTile(tile, EntryKind.blockLight);
        }

        static CachedTile overlay(Tile tile){
            return new CachedTile(tile, EntryKind.overlay);
        }

        static CachedTile floor(Tile tile){
            return new CachedTile(tile, EntryKind.floor);
        }

        @Override
        public void hitbox(Rect out){
            switch(kind){
                case block -> {
                    Block block = tile.block();
                    out.setCentered(
                    tile.worldx() + block.offset,
                    tile.worldy() + block.offset,
                    block.clipSize,
                    block.clipSize
                    );
                }

                case blockLight -> {
                    Block block = tile.block();
                    out.setCentered(
                    tile.worldx() + block.offset,
                    tile.worldy() + block.offset,
                    block.lightClipSize,
                    block.lightClipSize
                    );
                }

                case overlay -> out.setCentered(
                tile.worldx(),
                tile.worldy(),
                tile.overlay().lightClipSize,
                tile.overlay().lightClipSize
                );

                case floor -> out.setCentered(
                tile.worldx(),
                tile.worldy(),
                tile.floor().lightClipSize,
                tile.floor().lightClipSize
                );
            }
        }

        @Override
        public String toString(){
            return "CachedTile{" +
            "tile=" + tile.x + "," + tile.y +
            ", kind=" + kind +
            '}';
        }
    }

    @Override
    public String toString(){
        return "SeamRuntimeRenderCache{" +
        "runtimeId=" + runtime.id +
        ", built=" + built +
        ", version=" + version +
        ", worldBounds=" + worldBounds +
        '}';
    }
}
