package seam.world.terrain;

import seam.core.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.world.tiles.*;
import arc.math.geom.*;
import mindustry.world.blocks.environment.*;

public final class SeamTerrainService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeExecutor executor;

    public SeamTerrainService(SeamRuntimeRegistry runtimes, SeamRuntimeExecutor executor){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(executor == null){
            throw new NullPointerException("executor");
        }

        this.runtimes = runtimes;
        this.executor = executor;
    }

    public SeamTerrainResult setFloor(int runtimeId, int x, int y, Floor floor){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            return SeamTerrainResult.failure(null, floor, "runtime not found");
        }

        return setFloor(runtime, x, y, floor);
    }

    public SeamTerrainResult setFloor(int runtimeId, int tilePos, Floor floor){
        return setFloor(runtimeId, Point2.x(tilePos), Point2.y(tilePos), floor);
    }

    public SeamTerrainResult setFloor(SeamRuntime runtime, int x, int y, Floor floor){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.buildPlace, active -> {
                return SeamTileMutator.setFloor(active, x, y, floor);
            });
        }catch(Throwable throwable){
            return SeamTerrainResult.failure(runtime, floor, throwable);
        }
    }

    public SeamTerrainResult fillFloor(int runtimeId, Floor floor){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            return SeamTerrainResult.failure(null, floor, "runtime not found");
        }

        return fillFloor(runtime, floor);
    }

    public SeamTerrainResult fillFloor(SeamRuntime runtime, Floor floor){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.buildPlace, active -> {
                return SeamTileMutator.fillFloor(active, floor);
            });
        }catch(Throwable throwable){
            return SeamTerrainResult.failure(runtime, floor, throwable);
        }
    }
}