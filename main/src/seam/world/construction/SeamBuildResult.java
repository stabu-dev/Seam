package seam.world.construction;

import seam.core.*;
import seam.runtime.*;
import mindustry.game.*;
import mindustry.world.*;

public final class SeamBuildResult{
    public final boolean success;

    public final int runtimeId;
    public final int x;
    public final int y;
    public final int tilePos;

    public final String previousBlockName;
    public final String currentBlockName;
    public final String requestedBlockName;

    public final Team team;
    public final int rotation;

    public final String message;
    public final Throwable throwable;

    private SeamBuildResult(
    boolean success,
    int runtimeId,
    int x,
    int y,
    int tilePos,
    String previousBlockName,
    String currentBlockName,
    String requestedBlockName,
    Team team,
    int rotation,
    String message,
    Throwable throwable
    ){
        this.success = success;
        this.runtimeId = runtimeId;
        this.x = x;
        this.y = y;
        this.tilePos = tilePos;
        this.previousBlockName = previousBlockName;
        this.currentBlockName = currentBlockName;
        this.requestedBlockName = requestedBlockName;
        this.team = team;
        this.rotation = rotation;
        this.message = message;
        this.throwable = throwable;
    }

    public static SeamBuildResult success(
    SeamRuntime runtime,
    Tile tile,
    Block previousBlock,
    Block requestedBlock,
    Team team,
    int rotation,
    String message
    ){
        return new SeamBuildResult(
        true,
        runtime.id,
        tile.x,
        tile.y,
        tile.pos(),
        blockName(previousBlock),
        blockName(tile.block()),
        blockName(requestedBlock),
        team,
        rotation,
        message,
        null
        );
    }

    public static SeamBuildResult failure(
    SeamRuntime runtime,
    int x,
    int y,
    int tilePos,
    Block requestedBlock,
    Team team,
    int rotation,
    String message
    ){
        return new SeamBuildResult(
        false,
        runtime == null ? -1 : runtime.id,
        x,
        y,
        tilePos,
        null,
        null,
        blockName(requestedBlock),
        team,
        rotation,
        message,
        null
        );
    }

    public static SeamBuildResult failure(
    SeamRuntime runtime,
    int x,
    int y,
    int tilePos,
    Block requestedBlock,
    Team team,
    int rotation,
    Throwable throwable
    ){
        return new SeamBuildResult(
        false,
        runtime == null ? -1 : runtime.id,
        x,
        y,
        tilePos,
        null,
        null,
        blockName(requestedBlock),
        team,
        rotation,
        SeamFailures.describe(throwable, "unknown error"),
        throwable
        );
    }

    private static String blockName(Block block){
        return block == null ? null : block.name;
    }

    @Override
    public String toString(){
        return "SeamBuildResult{" +
        "success=" + success +
        ", runtimeId=" + runtimeId +
        ", tile=" + x + "," + y +
        ", tilePos=" + tilePos +
        ", previousBlockName='" + previousBlockName + '\'' +
        ", currentBlockName='" + currentBlockName + '\'' +
        ", requestedBlockName='" + requestedBlockName + '\'' +
        ", team=" + team +
        ", rotation=" + rotation +
        ", message='" + message + '\'' +
        '}';
    }
}
