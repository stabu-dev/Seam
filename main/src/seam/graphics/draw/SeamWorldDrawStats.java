package seam.graphics.draw;

public final class SeamWorldDrawStats{
    public int viewsVisited;
    public int viewsDrawn;
    public int viewsSkipped;

    public int batchesFailed;
    public int isolatedBatchesDrawn;

    public int floorChunksVisited;
    public int floorChunksCached;
    public int floorLayersDrawn;
    public int floorMeshesDrawn;

    public int shadowMasksPrepared;
    public int staticShadowsDrawn;

    public int darknessTilesDrawn;
    public int darknessPasses;
    public int darknessDraws;

    public int tilesVisited;
    public int blocksDrawn;
    public int customShadowsDrawn;
    public int cracksDrawn;
    public int teamOverlaysDrawn;
    public int statusDrawn;

    public int drawEntitiesQueued;
    public int drawEntityDuplicatesSkipped;
    public int weatherDrawn;

    public int lightsQueued;
    public int lightPasses;
    public int lightsSkipped;
    public int staticLightsQueued;
    public int dynamicLightsCaptured;
    public int dynamicLightRunsCaptured;

    public int bloomCaptures;
    public int bloomRenders;
    public int animatedShieldPasses;
    public int animatedBuildBeamPasses;

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
        isolatedBatchesDrawn = 0;

        floorChunksVisited = 0;
        floorChunksCached = 0;
        floorLayersDrawn = 0;
        floorMeshesDrawn = 0;

        shadowMasksPrepared = 0;
        staticShadowsDrawn = 0;

        darknessTilesDrawn = 0;
        darknessPasses = 0;
        darknessDraws = 0;

        tilesVisited = 0;
        blocksDrawn = 0;
        customShadowsDrawn = 0;
        cracksDrawn = 0;
        teamOverlaysDrawn = 0;
        statusDrawn = 0;

        drawEntitiesQueued = 0;
        drawEntityDuplicatesSkipped = 0;
        weatherDrawn = 0;

        lightsQueued = 0;
        lightPasses = 0;
        lightsSkipped = 0;
        staticLightsQueued = 0;
        dynamicLightsCaptured = 0;
        dynamicLightRunsCaptured = 0;

        bloomCaptures = 0;
        bloomRenders = 0;
        animatedShieldPasses = 0;
        animatedBuildBeamPasses = 0;

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
        if(startedNanos == 0L || endedNanos == 0L){
            return 0L;
        }

        return Math.max(0L, endedNanos - startedNanos);
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
        copy.isolatedBatchesDrawn = isolatedBatchesDrawn;

        copy.floorChunksVisited = floorChunksVisited;
        copy.floorChunksCached = floorChunksCached;
        copy.floorLayersDrawn = floorLayersDrawn;
        copy.floorMeshesDrawn = floorMeshesDrawn;

        copy.shadowMasksPrepared = shadowMasksPrepared;
        copy.staticShadowsDrawn = staticShadowsDrawn;

        copy.darknessTilesDrawn = darknessTilesDrawn;
        copy.darknessPasses = darknessPasses;
        copy.darknessDraws = darknessDraws;

        copy.tilesVisited = tilesVisited;
        copy.blocksDrawn = blocksDrawn;
        copy.customShadowsDrawn = customShadowsDrawn;
        copy.cracksDrawn = cracksDrawn;
        copy.teamOverlaysDrawn = teamOverlaysDrawn;
        copy.statusDrawn = statusDrawn;

        copy.drawEntitiesQueued = drawEntitiesQueued;
        copy.drawEntityDuplicatesSkipped = drawEntityDuplicatesSkipped;
        copy.weatherDrawn = weatherDrawn;

        copy.lightsQueued = lightsQueued;
        copy.lightPasses = lightPasses;
        copy.lightsSkipped = lightsSkipped;
        copy.staticLightsQueued = staticLightsQueued;
        copy.dynamicLightsCaptured = dynamicLightsCaptured;
        copy.dynamicLightRunsCaptured = dynamicLightRunsCaptured;

        copy.bloomCaptures = bloomCaptures;
        copy.bloomRenders = bloomRenders;
        copy.animatedShieldPasses = animatedShieldPasses;
        copy.animatedBuildBeamPasses = animatedBuildBeamPasses;

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
        ", isolatedBatchesDrawn=" + isolatedBatchesDrawn +
        ", floorChunksVisited=" + floorChunksVisited +
        ", floorChunksCached=" + floorChunksCached +
        ", floorLayersDrawn=" + floorLayersDrawn +
        ", floorMeshesDrawn=" + floorMeshesDrawn +
        ", shadowMasksPrepared=" + shadowMasksPrepared +
        ", staticShadowsDrawn=" + staticShadowsDrawn +
        ", darknessTilesDrawn=" + darknessTilesDrawn +
        ", darknessPasses=" + darknessPasses +
        ", darknessDraws=" + darknessDraws +
        ", tilesVisited=" + tilesVisited +
        ", blocksDrawn=" + blocksDrawn +
        ", customShadowsDrawn=" + customShadowsDrawn +
        ", cracksDrawn=" + cracksDrawn +
        ", teamOverlaysDrawn=" + teamOverlaysDrawn +
        ", statusDrawn=" + statusDrawn +
        ", drawEntitiesQueued=" + drawEntitiesQueued +
        ", drawEntityDuplicatesSkipped=" + drawEntityDuplicatesSkipped +
        ", weatherDrawn=" + weatherDrawn +
        ", lightsQueued=" + lightsQueued +
        ", lightPasses=" + lightPasses +
        ", lightsSkipped=" + lightsSkipped +
        ", staticLightsQueued=" + staticLightsQueued +
        ", dynamicLightsCaptured=" + dynamicLightsCaptured +
        ", dynamicLightRunsCaptured=" + dynamicLightRunsCaptured +
        ", bloomCaptures=" + bloomCaptures +
        ", bloomRenders=" + bloomRenders +
        ", animatedShieldPasses=" + animatedShieldPasses +
        ", animatedBuildBeamPasses=" + animatedBuildBeamPasses +
        '}';
    }
}