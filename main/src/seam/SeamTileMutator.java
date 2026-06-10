package seam;

import arc.math.geom.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;

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

        try{
            Block previous = tile.block();

            tile.setBlock(block, team, rotation);

            return SeamBuildResult.success(runtime, tile, previous, block, team, rotation, "placed");
        }catch(Throwable throwable){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, throwable);
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

        try{
            Block previous = tile.block();

            tile.setBlock(Blocks.air);

            return SeamBuildResult.success(runtime, tile, previous, Blocks.air, Team.derelict, 0, "removed");
        }catch(Throwable throwable){
            return SeamBuildResult.failure(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0, throwable);
        }
    }

    public static SeamConfigResult configure(SeamRuntime runtime, int tilePos, Object value){
        int x = Point2.x(tilePos);
        int y = Point2.y(tilePos);

        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(value == null){
            return SeamConfigResult.failure(
            runtime,
            tilePos,
            x,
            y,
            null,
            null,
            "null config values are not supported"
            );
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

            return SeamConfigResult.success(runtime, tilePos, x, y, tile.block().name, value);
        }catch(Throwable throwable){
            return SeamConfigResult.failure(runtime, tilePos, x, y, tile.block().name, value, throwable);
        }
    }
}