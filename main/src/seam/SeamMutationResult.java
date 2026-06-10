package seam;

public final class SeamMutationResult{
    public final boolean success;
    public final long mutationId;
    public final int runtimeId;
    public final SeamMutationType type;
    public final String message;
    public final Object payload;
    public final Throwable throwable;

    private SeamMutationResult(
    boolean success,
    long mutationId,
    int runtimeId,
    SeamMutationType type,
    String message,
    Object payload,
    Throwable throwable
    ){
        this.success = success;
        this.mutationId = mutationId;
        this.runtimeId = runtimeId;
        this.type = type;
        this.message = message;
        this.payload = payload;
        this.throwable = throwable;
    }

    public static SeamMutationResult success(SeamMutation mutation, String message, Object payload){
        return new SeamMutationResult(
        true,
        mutation.id,
        mutation.runtimeId,
        mutation.type,
        message,
        payload,
        null
        );
    }

    public static SeamMutationResult failure(SeamMutation mutation, String message, Object payload, Throwable throwable){
        return new SeamMutationResult(
        false,
        mutation.id,
        mutation.runtimeId,
        mutation.type,
        message,
        payload,
        throwable
        );
    }

    @Override
    public String toString(){
        return "SeamMutationResult{" +
        "success=" + success +
        ", mutationId=" + mutationId +
        ", runtimeId=" + runtimeId +
        ", type=" + type +
        ", message='" + message + '\'' +
        ", payload=" + payload +
        '}';
    }
}