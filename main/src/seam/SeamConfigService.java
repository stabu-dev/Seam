package seam;

import arc.math.geom.*;
import mindustry.gen.*;
import mindustry.world.*;

public final class SeamConfigService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeExecutor executor;

    public SeamConfigService(SeamRuntimeRegistry runtimes, SeamRuntimeExecutor executor){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(executor == null){
            throw new NullPointerException("executor");
        }

        this.runtimes = runtimes;
        this.executor = executor;
    }

    public SeamConfigResult configureInt(int runtimeId, int tilePos, int value){
        return configure(runtimeId, tilePos, value);
    }

    public SeamConfigResult configureBool(int runtimeId, int tilePos, boolean value){
        return configure(runtimeId, tilePos, value);
    }

    public SeamConfigResult configureFloat(int runtimeId, int tilePos, float value){
        return configure(runtimeId, tilePos, value);
    }

    public SeamConfigResult configureString(int runtimeId, int tilePos, String value){
        return configure(runtimeId, tilePos, value);
    }

    public SeamConfigResult configure(int runtimeId, int tilePos, Object value){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            return SeamConfigResult.failure(
            null,
            tilePos,
            Point2.x(tilePos),
            Point2.y(tilePos),
            null,
            value,
            "runtime not found"
            );
        }

        return configure(runtime, tilePos, value);
    }

    public SeamConfigResult configureInt(SeamRuntime runtime, int tilePos, int value){
        return configure(runtime, tilePos, value);
    }

    public SeamConfigResult configureBool(SeamRuntime runtime, int tilePos, boolean value){
        return configure(runtime, tilePos, value);
    }

    public SeamConfigResult configureFloat(SeamRuntime runtime, int tilePos, float value){
        return configure(runtime, tilePos, value);
    }

    public SeamConfigResult configureString(SeamRuntime runtime, int tilePos, String value){
        return configure(runtime, tilePos, value);
    }

    public SeamConfigResult configure(SeamRuntime runtime, int tilePos, Object value){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        int x = Point2.x(tilePos);
        int y = Point2.y(tilePos);

        if(value == null){
            return SeamConfigResult.failure(
            runtime,
            tilePos,
            x,
            y,
            null,
            null,
            "null config values are not supported by SeamConfigService yet"
            );
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.configure, active -> {
                Tile tile = active.world.tile(x, y);

                if(tile == null){
                    return SeamConfigResult.failure(active, tilePos, x, y, null, value, "tile not found");
                }

                Building build = tile.build;

                if(build == null){
                    return SeamConfigResult.failure(active, tilePos, x, y, tile.block().name, value, "tile has no building");
                }

                build.configured(null, value);

                return SeamConfigResult.success(active, tilePos, x, y, tile.block().name, value);
            });
        }catch(Throwable throwable){
            return SeamConfigResult.failure(runtime, tilePos, x, y, null, value, throwable);
        }
    }

    public SeamConfigResult configureInt(SeamRuntime runtime, Building build, int value){
        return configure(runtime, build, value);
    }

    public SeamConfigResult configureBool(SeamRuntime runtime, Building build, boolean value){
        return configure(runtime, build, value);
    }

    public SeamConfigResult configureFloat(SeamRuntime runtime, Building build, float value){
        return configure(runtime, build, value);
    }

    public SeamConfigResult configureString(SeamRuntime runtime, Building build, String value){
        return configure(runtime, build, value);
    }

    public SeamConfigResult configure(SeamRuntime runtime, Building build, Object value){
        if(build == null){
            throw new NullPointerException("build");
        }

        return configure(runtime, build.pos(), value);
    }
}