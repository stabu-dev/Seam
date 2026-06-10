package seam;

public final class SeamFloorDrawResult{
    public final boolean success;
    public final String message;

    public final int runtimeId;
    public final int viewId;

    public final int chunksVisited;
    public final int chunksCached;
    public final int layersDrawn;
    public final int meshesDrawn;

    public final long cacheVersion;

    private SeamFloorDrawResult(
    boolean success,
    String message,
    int runtimeId,
    int viewId,
    int chunksVisited,
    int chunksCached,
    int layersDrawn,
    int meshesDrawn,
    long cacheVersion
    ){
        this.success = success;
        this.message = message;
        this.runtimeId = runtimeId;
        this.viewId = viewId;
        this.chunksVisited = chunksVisited;
        this.chunksCached = chunksCached;
        this.layersDrawn = layersDrawn;
        this.meshesDrawn = meshesDrawn;
        this.cacheVersion = cacheVersion;
    }

    public static SeamFloorDrawResult failure(int runtimeId, int viewId, String message){
        return new SeamFloorDrawResult(false, message, runtimeId, viewId, 0, 0, 0, 0, -1L);
    }

    public static SeamFloorDrawResult success(
    int runtimeId,
    int viewId,
    int chunksVisited,
    int chunksCached,
    int layersDrawn,
    int meshesDrawn,
    long cacheVersion
    ){
        return new SeamFloorDrawResult(
        true,
        "ok",
        runtimeId,
        viewId,
        chunksVisited,
        chunksCached,
        layersDrawn,
        meshesDrawn,
        cacheVersion
        );
    }

    @Override
    public String toString(){
        return "SeamFloorDrawResult{" +
        "success=" + success +
        ", message='" + message + '\'' +
        ", runtimeId=" + runtimeId +
        ", viewId=" + viewId +
        ", chunksVisited=" + chunksVisited +
        ", chunksCached=" + chunksCached +
        ", layersDrawn=" + layersDrawn +
        ", meshesDrawn=" + meshesDrawn +
        ", cacheVersion=" + cacheVersion +
        '}';
    }
}