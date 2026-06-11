package seam.runtime.mutations;

import seam.runtime.*;
import java.util.concurrent.atomic.*;

public abstract class SeamMutation{
    public enum Type{
        buildPlace,
        buildRemove,
        configure
    }

    private static final AtomicLong nextId = new AtomicLong(1L);

    public final long id;
    public final int runtimeId;
    public final Type type;
    public final String source;

    protected SeamMutation(int runtimeId, Type type, String source){
        this.id = nextId.getAndIncrement();
        this.runtimeId = runtimeId;
        this.type = type;
        this.source = source == null ? "unknown" : source;
    }

    public abstract SeamMutationResult apply(SeamRuntime runtime);

    @Override
    public String toString(){
        return "SeamMutation{" +
        "id=" + id +
        ", runtimeId=" + runtimeId +
        ", type=" + type +
        ", source='" + source + '\'' +
        '}';
    }
}
