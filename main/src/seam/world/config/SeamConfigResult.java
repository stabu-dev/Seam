package seam.world.config;

import seam.core.*;
import seam.runtime.*;

public final class SeamConfigResult{
    public final boolean success;

    public final int runtimeId;
    public final int tilePos;
    public final int tileX;
    public final int tileY;

    public final String blockName;
    public final String valueType;
    public final String message;

    public final Throwable throwable;

    private SeamConfigResult(
    boolean success,
    int runtimeId,
    int tilePos,
    int tileX,
    int tileY,
    String blockName,
    String valueType,
    String message,
    Throwable throwable
    ){
        this.success = success;
        this.runtimeId = runtimeId;
        this.tilePos = tilePos;
        this.tileX = tileX;
        this.tileY = tileY;
        this.blockName = blockName;
        this.valueType = valueType;
        this.message = message;
        this.throwable = throwable;
    }

    public static SeamConfigResult success(
    SeamRuntime runtime,
    int tilePos,
    int tileX,
    int tileY,
    String blockName,
    Object value
    ){
        return new SeamConfigResult(
        true,
        runtime.id,
        tilePos,
        tileX,
        tileY,
        blockName,
        valueType(value),
        "configured",
        null
        );
    }

    public static SeamConfigResult failure(
    SeamRuntime runtime,
    int tilePos,
    int tileX,
    int tileY,
    String blockName,
    Object value,
    String message
    ){
        return new SeamConfigResult(
        false,
        runtime == null ? -1 : runtime.id,
        tilePos,
        tileX,
        tileY,
        blockName,
        valueType(value),
        message,
        null
        );
    }

    public static SeamConfigResult failure(
    SeamRuntime runtime,
    int tilePos,
    int tileX,
    int tileY,
    String blockName,
    Object value,
    Throwable throwable
    ){
        return new SeamConfigResult(
        false,
        runtime == null ? -1 : runtime.id,
        tilePos,
        tileX,
        tileY,
        blockName,
        valueType(value),
        SeamFailures.describe(throwable, "unknown error"),
        throwable
        );
    }

    private static String valueType(Object value){
        return value == null ? "null" : value.getClass().getName();
    }

    @Override
    public String toString(){
        return "SeamConfigResult{" +
        "success=" + success +
        ", runtimeId=" + runtimeId +
        ", tilePos=" + tilePos +
        ", tile=" + tileX + "," + tileY +
        ", blockName='" + blockName + '\'' +
        ", valueType='" + valueType + '\'' +
        ", message='" + message + '\'' +
        '}';
    }
}
