package seam;

import arc.math.geom.*;
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

            runtime.world.setGenerating(true);
            tile.setBlock(block, team, rotation);

            runtime.world.setGenerating(previousGenerating);
            runtime.world.tileChanges++;

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

            runtime.world.setGenerating(true);
            tile.setBlock(Blocks.air);

            runtime.world.setGenerating(previousGenerating);
            runtime.world.tileChanges++;

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

        if(value == null){
            return SeamConfigResult.failure(runtime, tilePos, x, y, null, null, "null config values are not supported");
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

            runtime.world.setGenerating(true);
            tile.setFloor(floor);

            runtime.world.setGenerating(previousGenerating);
            runtime.world.floorChanges++;

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
                if(tile != null && tile.floor() != floor){
                    tile.setFloor(floor);
                    changed++;
                }
            }

            runtime.world.setGenerating(previousGenerating);

            if(changed > 0){
                runtime.world.floorChanges++;
                runtime.renderInvalidation.markFull();
            }

            return SeamTerrainResult.success(runtime, changed, floor, "floor filled");
        }catch(Throwable throwable){
            return SeamTerrainResult.failure(runtime, floor, throwable);
        }finally{
            runtime.world.setGenerating(previousGenerating);
        }
    }

    private static void updateProximity(SeamRuntime runtime, Tile tile){
        if(runtime == null || tile == null || !runtime.worldReady()){
            return;
        }

        if(tile.build != null){
            tile.build.updateProximity();
            return;
        }

        for(Point2 point : Geometry.d4){
            Building other = runtime.world.build(tile.x + point.x, tile.y + point.y);

            if(other != null){
                other.onProximityUpdate();
            }
        }
    }
}