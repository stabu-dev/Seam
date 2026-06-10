package seam;

public final class SeamWorldDrawStats{
    public int viewsVisited;
    public int viewsDrawn;
    public int viewsSkipped;

    public int batchesFailed;

    public int floorChunksVisited;
    public int floorChunksCached;
    public int floorLayersDrawn;
    public int floorMeshesDrawn;

    public int shadowMasksPrepared;
    public int staticShadowsDrawn;

    public int tilesVisited;
    public int blocksDrawn;
    public int customShadowsDrawn;
    public int cracksDrawn;
    public int teamOverlaysDrawn;
    public int statusDrawn;

    public int drawEntitiesQueued;

    public int lightsSkipped;

    public long startedNanos;
    public long endedNanos;

    public void begin(){
        startedNanos = System.nanoTime();
        endedNanos = 0L;
    }

    public void end(){
        endedNanos = System.nanoTime();
    }

    public void reset(){
        viewsVisited = 0;
        viewsDrawn = 0;
        viewsSkipped = 0;

        batchesFailed = 0;

        floorChunksVisited = 0;
        floorChunksCached = 0;
        floorLayersDrawn = 0;
        floorMeshesDrawn = 0;

        shadowMasksPrepared = 0;
        staticShadowsDrawn = 0;

        tilesVisited = 0;
        blocksDrawn = 0;
        customShadowsDrawn = 0;
        cracksDrawn = 0;
        teamOverlaysDrawn = 0;
        statusDrawn = 0;

        drawEntitiesQueued = 0;

        lightsSkipped = 0;

        startedNanos = 0L;
        endedNanos = 0L;
    }

    public void addFloorResult(SeamFloorDrawResult result){
        if(result == null || !result.success){
            return;
        }

        floorChunksVisited += result.chunksVisited;
        floorChunksCached += result.chunksCached;
        floorLayersDrawn += result.layersDrawn;
        floorMeshesDrawn += result.meshesDrawn;
    }

    public long durationNanos(){
        return endedNanos - startedNanos;
    }

    public double durationMillis(){
        return durationNanos() / 1_000_000.0;
    }

    public SeamWorldDrawStats copy(){
        SeamWorldDrawStats copy = new SeamWorldDrawStats();

        copy.viewsVisited = viewsVisited;
        copy.viewsDrawn = viewsDrawn;
        copy.viewsSkipped = viewsSkipped;
        copy.batchesFailed = batchesFailed;

        copy.floorChunksVisited = floorChunksVisited;
        copy.floorChunksCached = floorChunksCached;
        copy.floorLayersDrawn = floorLayersDrawn;
        copy.floorMeshesDrawn = floorMeshesDrawn;

        copy.shadowMasksPrepared = shadowMasksPrepared;
        copy.staticShadowsDrawn = staticShadowsDrawn;

        copy.tilesVisited = tilesVisited;
        copy.blocksDrawn = blocksDrawn;
        copy.customShadowsDrawn = customShadowsDrawn;
        copy.cracksDrawn = cracksDrawn;
        copy.teamOverlaysDrawn = teamOverlaysDrawn;
        copy.statusDrawn = statusDrawn;

        copy.drawEntitiesQueued = drawEntitiesQueued;

        copy.lightsSkipped = lightsSkipped;

        copy.startedNanos = startedNanos;
        copy.endedNanos = endedNanos;

        return copy;
    }

    @Override
    public String toString(){
        return "SeamWorldDrawStats{" +
        "durationMs=" + durationMillis() +
        ", viewsVisited=" + viewsVisited +
        ", viewsDrawn=" + viewsDrawn +
        ", viewsSkipped=" + viewsSkipped +
        ", batchesFailed=" + batchesFailed +
        ", floorChunksVisited=" + floorChunksVisited +
        ", floorChunksCached=" + floorChunksCached +
        ", floorLayersDrawn=" + floorLayersDrawn +
        ", floorMeshesDrawn=" + floorMeshesDrawn +
        ", shadowMasksPrepared=" + shadowMasksPrepared +
        ", staticShadowsDrawn=" + staticShadowsDrawn +
        ", tilesVisited=" + tilesVisited +
        ", blocksDrawn=" + blocksDrawn +
        ", customShadowsDrawn=" + customShadowsDrawn +
        ", cracksDrawn=" + cracksDrawn +
        ", teamOverlaysDrawn=" + teamOverlaysDrawn +
        ", statusDrawn=" + statusDrawn +
        ", drawEntitiesQueued=" + drawEntitiesQueued +
        ", lightsSkipped=" + lightsSkipped +
        '}';
    }
}