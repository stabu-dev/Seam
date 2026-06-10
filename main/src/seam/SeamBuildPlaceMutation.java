package seam;

import mindustry.game.*;
import mindustry.world.*;

public final class SeamBuildPlaceMutation extends SeamMutation{
    public final int x;
    public final int y;
    public final Block block;
    public final Team team;
    public final int rotation;

    public SeamBuildPlaceMutation(int runtimeId, int x, int y, Block block, Team team, int rotation, String source){
        super(runtimeId, SeamMutationType.buildPlace, source);

        this.x = x;
        this.y = y;
        this.block = block;
        this.team = team;
        this.rotation = rotation;
    }

    @Override
    public SeamMutationResult apply(SeamRuntime runtime){
        SeamBuildResult result = SeamTileMutator.place(runtime, x, y, block, team, rotation);

        if(result.success){
            return SeamMutationResult.success(this, result.message, result);
        }

        return SeamMutationResult.failure(this, result.message, result, result.throwable);
    }
}