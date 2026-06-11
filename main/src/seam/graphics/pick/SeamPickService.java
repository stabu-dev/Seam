package seam.graphics.pick;

import seam.graphics.view.*;
import seam.runtime.*;
import seam.world.*;
import seam.world.tiles.*;
import arc.*;
import arc.math.geom.*;

public final class SeamPickService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamViewRegistry views;
    private final SeamQueryService query;
    private final Vec2 runtimeWorld = new Vec2();

    public SeamPickService(SeamRuntimeRegistry runtimes, SeamViewRegistry views, SeamQueryService query){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(views == null){
            throw new NullPointerException("views");
        }

        if(query == null){
            throw new NullPointerException("query");
        }

        this.runtimes = runtimes;
        this.views = views;
        this.query = query;
    }

    public SeamPickResult screen(float screenX, float screenY){
        if(Core.camera == null){
            return SeamPickResult.failure("camera is not available", screenX, screenY);
        }

        Vec2 world = Core.camera.unproject(screenX, screenY).cpy();

        return hostWorld(world.x, world.y);
    }

    public SeamPickResult hostWorld(float hostWorldX, float hostWorldY){
        SeamPickCandidate best = null;

        for(SeamView view : views.all()){
            if(!view.pickable()){
                continue;
            }

            SeamRuntime runtime = runtimes.get(view.runtimeId());

            if(runtime == null || !runtime.loaded() || runtime.disposed() || !runtime.worldReady()){
                continue;
            }

            view.projection().hostToRuntimeWorld(hostWorldX, hostWorldY, runtimeWorld);

            if(!view.projection().containsRuntimeWorld(runtime, runtimeWorld.x, runtimeWorld.y)){
                continue;
            }

            SeamTileRef ref = SeamTileRef.of(
            runtime.id,
            view.projection().runtimeTileX(runtimeWorld.x),
            view.projection().runtimeTileY(runtimeWorld.y)
            );

            SeamTileSnapshot snapshot = query.tile(ref);

            if(!snapshot.success){
                continue;
            }

            SeamPickCandidate candidate = new SeamPickCandidate(view, ref, snapshot, runtimeWorld.x, runtimeWorld.y);

            if(best == null || better(candidate, best)){
                best = candidate;
            }
        }

        if(best == null){
            return SeamPickResult.failure("nothing picked", hostWorldX, hostWorldY);
        }

        return SeamPickResult.success(
        best.view,
        best.ref,
        best.snapshot,
        hostWorldX,
        hostWorldY,
        best.runtimeWorldX,
        best.runtimeWorldY
        );
    }

    private boolean better(SeamPickCandidate candidate, SeamPickCandidate current){
        if(candidate.view.inputPriority() != current.view.inputPriority()){
            return candidate.view.inputPriority() > current.view.inputPriority();
        }

        return candidate.view.id() > current.view.id();
    }

    private static final class SeamPickCandidate{
        final SeamView view;
        final SeamTileRef ref;
        final SeamTileSnapshot snapshot;
        final float runtimeWorldX;
        final float runtimeWorldY;

        SeamPickCandidate(
        SeamView view,
        SeamTileRef ref,
        SeamTileSnapshot snapshot,
        float runtimeWorldX,
        float runtimeWorldY
        ){
            this.view = view;
            this.ref = ref;
            this.snapshot = snapshot;
            this.runtimeWorldX = runtimeWorldX;
            this.runtimeWorldY = runtimeWorldY;
        }
    }
}
