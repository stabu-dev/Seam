package seam;

public final class SeamBuildRemoveMutation extends SeamMutation{
    public final int x;
    public final int y;

    public SeamBuildRemoveMutation(int runtimeId, int x, int y, String source){
        super(runtimeId, SeamMutationType.buildRemove, source);

        this.x = x;
        this.y = y;
    }

    @Override
    public SeamMutationResult apply(SeamRuntime runtime){
        SeamBuildResult result = SeamTileMutator.remove(runtime, x, y);

        if(result.success){
            return SeamMutationResult.success(this, result.message, result);
        }

        return SeamMutationResult.failure(this, result.message, result, result.throwable);
    }
}