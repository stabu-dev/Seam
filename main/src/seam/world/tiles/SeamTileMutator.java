package seam.world.tiles;

import seam.runtime.*;
import seam.world.construction.*;
import seam.world.config.*;
import seam.world.terrain.*;
import seam.runtime.services.*;
import arc.math.geom.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

public final class SeamTileMutator{
    private SeamTileMutator(){
    }

    public static SeamBuildResult place(SeamRuntime runtime, int x, int y, Block block, Team team, int rotation){
        int tilePos = Point2.pack(x, y);

        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(block == null){
            return SeamBuildResult.failure(runtime, x, y, tilePos, null, team, rotation, "block is null");
        }

        if(team == null){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, null, rotation, "team is null");
        }

        if(!runtime.worldReady()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "runtime world is not ready");
        }

        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "tile coordinates are out of bounds");
        }

        Tile tile = runtime.world.tile(x, y);

        if(tile == null){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "tile not found");
        }

        boolean previousGenerating = runtime.world.isGenerating();

        try{
            Block previous = tile.block();

            removeIndexerEntries(runtime, tile);

            runtime.world.setGenerating(true);
            tile.setBlock(block, team, rotation);

            runtime.world.setGenerating(previousGenerating);
            runtime.world.tileChanges++;

            addIndexerEntries(runtime, tile);
            updatePathing(runtime, tile);
            updateProximity(runtime, tile);

            runtime.renderInvalidation.blockChanged(runtime, x, y, previous, tile.block());

            return SeamBuildResult.success(runtime, tile, previous, block, team, rotation, "placed");
        }catch(Throwable throwable){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, throwable);
        }finally{
            runtime.world.setGenerating(previousGenerating);
        }
    }

    public static SeamBuildResult remove(SeamRuntime runtime, int x, int y){
        int tilePos = Point2.pack(x, y);

        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(!runtime.worldReady()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0, "runtime world is not ready");
        }

        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0, "tile coordinates are out of bounds");
        }

        Tile tile = runtime.world.tile(x, y);

        if(tile == null){
            return SeamBuildResult.failure(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0, "tile not found");
        }

        boolean previousGenerating = runtime.world.isGenerating();

        try{
            Block previous = tile.block();

            removeIndexerEntries(runtime, tile);

            runtime.world.setGenerating(true);
            tile.setBlock(Blocks.air);

            runtime.world.setGenerating(previousGenerating);
            runtime.world.tileChanges++;

            addIndexerEntries(runtime, tile);
            updatePathing(runtime, tile);
            updateProximity(runtime, tile);

            runtime.renderInvalidation.blockChanged(runtime, x, y, previous, tile.block());

            return SeamBuildResult.success(runtime, tile, previous, Blocks.air, Team.derelict, 0, "removed");
        }catch(Throwable throwable){
            return SeamBuildResult.failure(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0, throwable);
        }finally{
            runtime.world.setGenerating(previousGenerating);
        }
    }

    public static SeamConfigResult configure(SeamRuntime runtime, int tilePos, Object value){
        int x = Point2.x(tilePos);
        int y = Point2.y(tilePos);

        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(!runtime.worldReady()){
            return SeamConfigResult.failure(runtime, tilePos, x, y, null, value, "runtime world is not ready");
        }

        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return SeamConfigResult.failure(runtime, tilePos, x, y, null, value, "tile coordinates are out of bounds");
        }

        Tile tile = runtime.world.tile(x, y);

        if(tile == null){
            return SeamConfigResult.failure(runtime, tilePos, x, y, null, value, "tile not found");
        }

        Building build = tile.build;

        if(build == null){
            return SeamConfigResult.failure(runtime, tilePos, x, y, tile.block().name, value, "tile has no building");
        }

        try{
            build.configured(null, value);

            runtime.renderInvalidation.configChanged(runtime, tile, value);

            return SeamConfigResult.success(runtime, tilePos, x, y, tile.block().name, value);
        }catch(Throwable throwable){
            return SeamConfigResult.failure(runtime, tilePos, x, y, tile.block().name, value, throwable);
        }
    }

    public static SeamTerrainResult setFloor(SeamRuntime runtime, int x, int y, Floor floor){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(floor == null){
            return SeamTerrainResult.failure(runtime, null, "floor is null");
        }

        if(!runtime.worldReady()){
            return SeamTerrainResult.failure(runtime, floor, "runtime world is not ready");
        }

        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return SeamTerrainResult.failure(runtime, floor, "tile coordinates are out of bounds");
        }

        Tile tile = runtime.world.tile(x, y);

        if(tile == null){
            return SeamTerrainResult.failure(runtime, floor, "tile not found");
        }

        boolean previousGenerating = runtime.world.isGenerating();

        try{
            if(tile.floor() == floor){
                return SeamTerrainResult.success(runtime, 0, floor, "unchanged");
            }

            Floor previous = tile.floor();

            runtime.world.setGenerating(true);
            tile.setFloor(floor);

            runtime.world.setGenerating(previousGenerating);
            runtime.world.floorChanges++;

            updateFloorIndexer(runtime, tile, previous, floor);

            if(tile.build != null){
                tile.build.onProximityUpdate();
            }

            runtime.renderInvalidation.floorChanged(runtime, x, y);

            return SeamTerrainResult.success(runtime, 1, floor, "floor set");
        }catch(Throwable throwable){
            return SeamTerrainResult.failure(runtime, floor, throwable);
        }finally{
            runtime.world.setGenerating(previousGenerating);
        }
    }

    public static SeamTerrainResult fillFloor(SeamRuntime runtime, Floor floor){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(floor == null){
            return SeamTerrainResult.failure(runtime, null, "floor is null");
        }

        if(!runtime.worldReady()){
            return SeamTerrainResult.failure(runtime, floor, "runtime world is not ready");
        }

        boolean previousGenerating = runtime.world.isGenerating();
        int changed = 0;

        try{
            runtime.world.setGenerating(true);

            for(Tile tile : runtime.world.tiles){
                if(tile == null || tile.floor() == floor){
                    continue;
                }

                Floor previous = tile.floor();

                tile.setFloor(floor);
                changed++;

                updateFloorIndexer(runtime, tile, previous, floor);
            }

            runtime.world.setGenerating(previousGenerating);

            if(changed > 0){
                runtime.world.floorChanges++;
                runtime.renderInvalidation.markFull();
            }

            for(Building build : Groups.build){
                if(build != null){
                    build.onProximityUpdate();
                }
            }

            return SeamTerrainResult.success(runtime, changed, floor, "floor fill");
        }catch(Throwable throwable){
            return SeamTerrainResult.failure(runtime, floor, throwable);
        }finally{
            runtime.world.setGenerating(previousGenerating);
        }
    }

    private static void removeIndexerEntries(SeamRuntime runtime, Tile tile){
        if(runtime == null || tile == null || runtime.index == null){
            return;
        }

        if(!owns(runtime, tile)){
            return;
        }

        try{
            if(tile.build != null){
                Tile center = tile.build.tile;

                if(center != null && owns(runtime, center)){
                    runtime.index.removeIndex(center);
                    return;
                }
            }

            runtime.index.removeIndex(tile);
        }catch(Throwable ignored){
        }
    }

    private static void addIndexerEntries(SeamRuntime runtime, Tile tile){
        if(runtime == null || tile == null || runtime.index == null){
            return;
        }

        if(!owns(runtime, tile)){
            return;
        }

        try{
            if(tile.build != null){
                Tile center = tile.build.tile;

                if(center != null && owns(runtime, center)){
                    runtime.index.addIndex(center);
                    return;
                }
            }

            runtime.index.addIndex(tile);
        }catch(Throwable ignored){
        }
    }

    private static void updateFloorIndexer(SeamRuntime runtime, Tile tile, Floor previous, Floor current){
        if(runtime == null || tile == null || runtime.index == null){
            return;
        }

        if(!owns(runtime, tile)){
            return;
        }

        /*
         * Vanilla floor index updates are normally driven by TileFloorChangeEvent.
         * Seam suppresses that event with world.setGenerating(true), so the safe fallback is
         * to refresh the full tile index. This also keeps floor/wall ore state sane enough
         * for vanilla consumers without emitting global events.
         */
        try{
            runtime.index.removeFloorIndex(tile, previous);
            runtime.index.addFloorIndex(tile, current);
            runtime.index.removeIndex(tile);
            runtime.index.addIndex(tile);

            if(runtime.pathing != null){
                runtime.pathing.updateTile(tile);
            }
        }catch(Throwable ignored){
        }
    }

    private static boolean owns(SeamRuntime runtime, Tile tile){
        return runtime != null
        && tile != null
        && runtime.worldReady()
        && tile.x >= 0
        && tile.y >= 0
        && tile.x < runtime.world.width()
        && tile.y < runtime.world.height()
        && runtime.world.tile(tile.x, tile.y) == tile;
    }

    private static void updateProximity(SeamRuntime runtime, Tile tile){
        if(runtime == null || tile == null){
            return;
        }

        if(tile.build != null){
            tile.build.updateProximity();
        }

        for(Point2 point : Geometry.d4){
            Tile other = runtime.world.tile(tile.x + point.x, tile.y + point.y);

            if(other != null && other.build != null){
                other.build.updateProximity();
            }
        }
    }

    private static void updatePathing(SeamRuntime runtime, Tile tile){
        if(runtime == null || tile == null || runtime.pathing == null){
            return;
        }

        try{
            runtime.pathing.updateTile(tile);
        }catch(Throwable ignored){
        }
    }
}
