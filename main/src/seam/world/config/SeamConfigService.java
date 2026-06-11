package seam.world.config;

import seam.core.*;
import seam.runtime.mutations.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.world.tiles.*;
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
            return SeamConfigResult.failure(null, tilePos, Point2.x(tilePos), Point2.y(tilePos), null, value, "runtime not found");
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
            return SeamConfigResult.failure(runtime, tilePos, x, y, null, null, "null config values are not supported by SeamConfigService yet");
        }

        try{
            return executor.callRegisteredExclusive(runtime, SeamPhase.configure, active -> {
                return SeamTileMutator.configure(active, tilePos, value);
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

    public SeamMutation deferConfigureInt(int runtimeId, int tilePos, int value){
        return deferConfigure(runtimeId, tilePos, value);
    }

    public SeamMutation deferConfigureBool(int runtimeId, int tilePos, boolean value){
        return deferConfigure(runtimeId, tilePos, value);
    }

    public SeamMutation deferConfigureFloat(int runtimeId, int tilePos, float value){
        return deferConfigure(runtimeId, tilePos, value);
    }

    public SeamMutation deferConfigureString(int runtimeId, int tilePos, String value){
        return deferConfigure(runtimeId, tilePos, value);
    }

    public SeamMutation deferConfigure(int runtimeId, int tilePos, Object value){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null){
            throw new IllegalArgumentException("Runtime not found: " + runtimeId);
        }

        SeamMutation mutation = new ConfigMutation(runtimeId, tilePos, value, "SeamConfigService.deferConfigure");
        runtime.mutations.enqueue(mutation);

        return mutation;
    }

    public SeamMutation deferConfigure(SeamRuntime runtime, int tilePos, Object value){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        return deferConfigure(runtime.id, tilePos, value);
    }

    private static final class ConfigMutation extends SeamMutation{
        final int tilePos;
        final Object value;

        ConfigMutation(int runtimeId, int tilePos, Object value, String source){
            super(runtimeId, Type.configure, source);

            this.tilePos = tilePos;
            this.value = value;
        }

        @Override
        public SeamMutationResult apply(SeamRuntime runtime){
            SeamConfigResult result = SeamTileMutator.configure(runtime, tilePos, value);

            if(result.success){
                return SeamMutationResult.success(this, result.message, result);
            }

            return SeamMutationResult.failure(this, result.message, result, result.throwable);
        }
    }
}
