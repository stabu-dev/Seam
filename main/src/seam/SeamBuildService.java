package seam;

import arc.math.geom.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.world.*;

public final class SeamBuildService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeExecutor executor;

    public SeamBuildService(SeamRuntimeRegistry runtimes, SeamRuntimeExecutor executor){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(executor == null){
            throw new NullPointerException("executor");
        }

        this.runtimes = runtimes;
        this.executor = executor;
    }

    public SeamBuildResult place(int runtimeId, int x, int y, Block block, Team team, int rotation){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            return SeamBuildResult.failure(null, x, y, Point2.pack(x, y), block, team, rotation, "runtime not found");
        }

        return place(runtime, x, y, block, team, rotation);
    }

    public SeamBuildResult place(int runtimeId, int tilePos, Block block, Team team, int rotation){
        return place(runtimeId, Point2.x(tilePos), Point2.y(tilePos), block, team, rotation);
    }

    public SeamBuildResult place(SeamRuntime runtime, int x, int y, Block block, Team team, int rotation){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        int tilePos = Point2.pack(x, y);

        SeamBuildResult preflight = preflight(runtime, x, y, tilePos, block, team, rotation);

        if(preflight != null){
            return preflight;
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.buildPlace, active -> {
                return SeamTileMutator.place(active, x, y, block, team, rotation);
            });
        }catch(Throwable throwable){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, throwable);
        }
    }

    public SeamBuildResult place(SeamRuntime runtime, int tilePos, Block block, Team team, int rotation){
        return place(runtime, Point2.x(tilePos), Point2.y(tilePos), block, team, rotation);
    }

    public SeamBuildResult remove(int runtimeId, int x, int y){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            return SeamBuildResult.failure(null, x, y, Point2.pack(x, y), Blocks.air, Team.derelict, 0, "runtime not found");
        }

        return remove(runtime, x, y);
    }

    public SeamBuildResult remove(int runtimeId, int tilePos){
        return remove(runtimeId, Point2.x(tilePos), Point2.y(tilePos));
    }

    public SeamBuildResult remove(SeamRuntime runtime, int x, int y){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        int tilePos = Point2.pack(x, y);

        SeamBuildResult preflight = preflight(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0);

        if(preflight != null){
            return preflight;
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.buildRemove, active -> {
                return SeamTileMutator.remove(active, x, y);
            });
        }catch(Throwable throwable){
            return SeamBuildResult.failure(runtime, x, y, tilePos, Blocks.air, Team.derelict, 0, throwable);
        }
    }

    public SeamBuildResult remove(SeamRuntime runtime, int tilePos){
        return remove(runtime, Point2.x(tilePos), Point2.y(tilePos));
    }

    public SeamMutation deferPlace(int runtimeId, int x, int y, Block block, Team team, int rotation){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            throw new IllegalArgumentException("Runtime not found: " + runtimeId);
        }

        SeamMutation mutation = new SeamBuildPlaceMutation(runtimeId, x, y, block, team, rotation, "SeamBuildService.deferPlace");
        runtime.mutations.enqueue(mutation);

        return mutation;
    }

    public SeamMutation deferPlace(SeamRuntime runtime, int x, int y, Block block, Team team, int rotation){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        return deferPlace(runtime.id, x, y, block, team, rotation);
    }

    public SeamMutation deferRemove(int runtimeId, int x, int y){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            throw new IllegalArgumentException("Runtime not found: " + runtimeId);
        }

        SeamMutation mutation = new SeamBuildRemoveMutation(runtimeId, x, y, "SeamBuildService.deferRemove");
        runtime.mutations.enqueue(mutation);

        return mutation;
    }

    public SeamMutation deferRemove(SeamRuntime runtime, int x, int y){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        return deferRemove(runtime.id, x, y);
    }

    private SeamBuildResult preflight(SeamRuntime runtime, int x, int y, int tilePos, Block block, Team team, int rotation){
        if(runtime.disposed()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "runtime is disposed");
        }

        if(!runtime.loaded()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "runtime is not loaded");
        }

        if(!runtime.worldReady()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "runtime world is not ready");
        }

        if(block == null){
            return SeamBuildResult.failure(runtime, x, y, tilePos, null, team, rotation, "block is null");
        }

        if(team == null){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, null, rotation, "team is null");
        }

        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return SeamBuildResult.failure(runtime, x, y, tilePos, block, team, rotation, "tile coordinates are out of bounds");
        }

        return null;
    }
}