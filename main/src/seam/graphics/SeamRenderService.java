package seam.graphics;

import seam.graphics.cache.*;
import seam.graphics.draw.*;
import seam.graphics.invalidation.*;
import seam.graphics.view.*;
import seam.runtime.*;
import arc.*;
import arc.math.geom.*;
import arc.struct.*;

public final class SeamRenderService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamViewRegistry views;

    private final IntMap<SeamRuntimeRenderCache> blockCaches = new IntMap<>();
    private final IntMap<SeamFloorRenderCache> floorCaches = new IntMap<>();
    private final IntMap<Seq<SeamRenderInvalidation>> lastInvalidations = new IntMap<>();
    private final Rect cameraBounds = new Rect();

    public SeamRenderService(SeamRuntimeRegistry runtimes, SeamViewRegistry views){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(views == null){
            throw new NullPointerException("views");
        }

        this.runtimes = runtimes;
        this.views = views;
    }

    public SeamRuntimeRenderCache cache(int runtimeId){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null || runtime.disposed()){
            blockCaches.remove(runtimeId);
            throw new IllegalArgumentException("Runtime not found or disposed: " + runtimeId);
        }

        SeamRuntimeRenderCache cache = blockCaches.get(runtimeId);

        if(cache == null || cache.runtime() != runtime){
            cache = new SeamRuntimeRenderCache(runtime);
            blockCaches.put(runtimeId, cache);
        }

        return cache;
    }

    public SeamFloorRenderCache floorCache(int runtimeId){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null || runtime.disposed()){
            floorCaches.remove(runtimeId);
            throw new IllegalArgumentException("Runtime not found or disposed: " + runtimeId);
        }

        SeamFloorRenderCache cache = floorCaches.get(runtimeId);

        if(cache == null || cache.runtime() != runtime){
            cache = new SeamFloorRenderCache(runtime);
            floorCaches.put(runtimeId, cache);
        }

        return cache;
    }

    public Seq<SeamRenderInvalidation> lastInvalidations(int runtimeId){
        Seq<SeamRenderInvalidation> invalidations = lastInvalidations.get(runtimeId);

        if(invalidations == null){
            return new Seq<>();
        }

        return invalidations.copy();
    }

    public Seq<SeamRenderInvalidation> syncRuntime(int runtimeId){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null || runtime.disposed()){
            removeRuntime(runtimeId);
            return new Seq<>();
        }

        SeamRuntimeRenderCache blockCache = cache(runtimeId);
        SeamFloorRenderCache floorCache = floorCache(runtimeId);

        Seq<SeamRenderInvalidation> invalidations = blockCache.applyPendingInvalidations();

        floorCache.applyInvalidations(invalidations);
        lastInvalidations.put(runtimeId, invalidations.copy());

        return invalidations;
    }

    public boolean hasCache(int runtimeId){
        return blockCaches.containsKey(runtimeId) || floorCaches.containsKey(runtimeId);
    }

    public void removeRuntime(int runtimeId){
        SeamRuntimeRenderCache blockCache = blockCaches.remove(runtimeId);

        if(blockCache != null){
            blockCache.clear();
        }

        SeamFloorRenderCache floorCache = floorCaches.remove(runtimeId);

        if(floorCache != null){
            floorCache.dispose();
        }

        lastInvalidations.remove(runtimeId);
    }

    public void clear(){
        for(SeamRuntimeRenderCache cache : blockCaches.values()){
            cache.clear();
        }

        for(SeamFloorRenderCache cache : floorCaches.values()){
            cache.dispose();
        }

        blockCaches.clear();
        floorCaches.clear();
        lastInvalidations.clear();
    }

    public SeamRenderViewBatch queryView(int viewId, Rect hostBounds){
        if(hostBounds == null){
            return SeamRenderViewBatch.failure(viewId, -1, "host bounds is null");
        }

        SeamView view = views.get(viewId);

        if(view == null){
            return SeamRenderViewBatch.failure(viewId, -1, "view not found");
        }

        SeamRuntime runtime = runtimes.get(view.runtimeId());

        if(runtime == null || runtime.disposed()){
            return SeamRenderViewBatch.failure(view.id(), view.runtimeId(), "runtime not found or disposed");
        }

        syncRuntime(runtime.id);

        return cache(runtime.id).query(view, hostBounds);
    }

    public SeamRenderViewBatch queryViewCamera(int viewId){
        if(Core.camera == null){
            return SeamRenderViewBatch.failure(viewId, -1, "camera is not available");
        }

        return queryView(viewId, Core.camera.bounds(cameraBounds));
    }

    public SeamFloorDrawResult drawFloor(int viewId, Rect hostBounds){
        if(hostBounds == null){
            return SeamFloorDrawResult.failure(-1, viewId, "host bounds is null");
        }

        SeamView view = views.get(viewId);

        if(view == null){
            return SeamFloorDrawResult.failure(-1, viewId, "view not found");
        }

        SeamRuntime runtime = runtimes.get(view.runtimeId());

        if(runtime == null || runtime.disposed()){
            return SeamFloorDrawResult.failure(view.runtimeId(), view.id(), "runtime not found or disposed");
        }

        syncRuntime(runtime.id);

        return floorCache(runtime.id).draw(view, hostBounds);
    }
}
