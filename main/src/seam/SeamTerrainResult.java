package seam;

import mindustry.world.blocks.environment.*;

public final class SeamTerrainResult{
    public final boolean success;
    public final int runtimeId;
    public final int changed;
    public final String floorName;
    public final String message;
    public final Throwable throwable;

    private SeamTerrainResult(
    boolean success,
    int runtimeId,
    int changed,
    String floorName,
    String message,
    Throwable throwable
    ){
        this.success = success;
        this.runtimeId = runtimeId;
        this.changed = changed;
        this.floorName = floorName;
        this.message = message;
        this.throwable = throwable;
    }

    public static SeamTerrainResult success(SeamRuntime runtime, int changed, Floor floor, String message){
        return new SeamTerrainResult(
        true,
        runtime.id,
        changed,
        floor == null ? null : floor.name,
        message,
        null
        );
    }

    public static SeamTerrainResult failure(SeamRuntime runtime, Floor floor, String message){
        return new SeamTerrainResult(
        false,
        runtime == null ? -1 : runtime.id,
        0,
        floor == null ? null : floor.name,
        message,
        null
        );
    }

    public static SeamTerrainResult failure(SeamRuntime runtime, Floor floor, Throwable throwable){
        return new SeamTerrainResult(
        false,
        runtime == null ? -1 : runtime.id,
        0,
        floor == null ? null : floor.name,
        throwable == null ? "unknown error" : throwable.getClass().getSimpleName() + ": " + throwable.getMessage(),
        throwable
        );
    }

    @Override
    public String toString(){
        return "SeamTerrainResult{" +
        "success=" + success +
        ", runtimeId=" + runtimeId +
        ", changed=" + changed +
        ", floorName='" + floorName + '\'' +
        ", message='" + message + '\'' +
        '}';
    }
}