package seam;

public final class SeamConfigMutation extends SeamMutation{
    public final int tilePos;
    public final Object value;

    public SeamConfigMutation(int runtimeId, int tilePos, Object value, String source){
        super(runtimeId, SeamMutationType.configure, source);

        this.tilePos = tilePos;
        this.value = value;
    }

    @Override
    public SeamMutationResult apply(SeamRuntime runtime){
        SeamConfigResult result = SeamTileMutator.configure(runtime, tilePos, value);

        if(result.success){
            return SeamMutationResult.success(this, result.message, result);
        }

        return SeamMutationResult.failure(this, result.message, result, result.throwable);
    }
}