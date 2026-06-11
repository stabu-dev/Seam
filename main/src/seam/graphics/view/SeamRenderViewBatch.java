package seam.graphics.view;

import arc.math.geom.*;
import arc.struct.*;
import mindustry.world.*;

public final class SeamRenderViewBatch{
    public final boolean success;
    public final String message;

    public final int viewId;
    public final int runtimeId;

    public final Rect hostBounds;
    public final Rect runtimeBounds;

    public final Seq<Tile> tiles;
    public final Seq<Tile> lights;

    public final long cacheVersion;

    private SeamRenderViewBatch(
    boolean success,
    String message,
    int viewId,
    int runtimeId,
    Rect hostBounds,
    Rect runtimeBounds,
    Seq<Tile> tiles,
    Seq<Tile> lights,
    long cacheVersion
    ){
        this.success = success;
        this.message = message;
        this.viewId = viewId;
        this.runtimeId = runtimeId;
        this.hostBounds = hostBounds == null ? new Rect() : new Rect(hostBounds);
        this.runtimeBounds = runtimeBounds == null ? new Rect() : new Rect(runtimeBounds);
        this.tiles = tiles == null ? new Seq<>() : tiles.copy();
        this.lights = lights == null ? new Seq<>() : lights.copy();
        this.cacheVersion = cacheVersion;
    }

    public static SeamRenderViewBatch failure(int viewId, int runtimeId, String message){
        return new SeamRenderViewBatch(
        false,
        message,
        viewId,
        runtimeId,
        null,
        null,
        null,
        null,
        -1L
        );
    }

    public static SeamRenderViewBatch success(
    SeamView view,
    Rect hostBounds,
    Rect runtimeBounds,
    Seq<Tile> tiles,
    Seq<Tile> lights,
    long cacheVersion
    ){
        return new SeamRenderViewBatch(
        true,
        "ok",
        view.id(),
        view.runtimeId(),
        hostBounds,
        runtimeBounds,
        tiles,
        lights,
        cacheVersion
        );
    }

    public int tileCount(){
        return tiles.size;
    }

    public int lightCount(){
        return lights.size;
    }

    @Override
    public String toString(){
        return "SeamRenderViewBatch{" +
        "success=" + success +
        ", message='" + message + '\'' +
        ", viewId=" + viewId +
        ", runtimeId=" + runtimeId +
        ", hostBounds=" + hostBounds +
        ", runtimeBounds=" + runtimeBounds +
        ", tileCount=" + tileCount() +
        ", lightCount=" + lightCount() +
        ", cacheVersion=" + cacheVersion +
        '}';
    }
}