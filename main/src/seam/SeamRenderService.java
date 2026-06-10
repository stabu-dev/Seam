package seam;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;

public final class SeamRenderService{
    private final SeamRuntimeRegistry runtimes;
    private final SeamViewRegistry views;

    private final IntMap<SeamRuntimeRenderCache> blockCaches = new IntMap<>();
    private final IntMap<SeamFloorRenderCache> floorCaches = new IntMap<>();

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

    public void syncRuntime(int runtimeId){
        SeamRuntime runtime = runtimes.get(runtimeId);

        if(runtime == null || runtime.disposed()){
            removeRuntime(runtimeId);
            return;
        }

        SeamRuntimeRenderCache blockCache = cache(runtimeId);
        SeamFloorRenderCache floorCache = floorCache(runtimeId);

        Seq<SeamRenderInvalidation> invalidations = blockCache.applyPendingInvalidations();

        floorCache.applyInvalidations(invalidations);
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
    }

    public SeamRenderViewBatch queryView(int viewId, Rect hostBounds){
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
        return queryView(viewId, Core.camera.bounds(new Rect()));
    }

    public SeamFloorDrawResult drawFloor(int viewId, Rect hostBounds){
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