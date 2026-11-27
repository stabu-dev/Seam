package seam.core;

import arc.struct.*;
import arc.util.*;

/** Explicit reverse-order disposal owner for contexts and resources. */
public final class Lifetime implements Disposable, AutoCloseable{
    private final Seq<Disposable> owned = new Seq<>();
    private boolean disposed;

    /** Registers {@code resource} to be disposed in reverse order when this lifetime closes. Host thread only. */
    public <T extends Disposable> T own(T resource){
        Context.checkHost();
        if(disposed) throw new IllegalStateException("Lifetime is disposed.");
        if(resource == null || resource == this || owned.contains(resource, true)) throw new IllegalArgumentException("A distinct resource is required.");
        owned.add(resource);
        return resource;
    }

    @Override
    public boolean isDisposed(){
        Context.checkThread();
        return disposed;
    }

    /** Disposes owned resources in reverse registration order on the host thread. */
    @Override
    public void dispose(){
        Context.checkHost();
        if(disposed) return;
        disposed = true;
        int index = owned.size - 1;
        try{
            for(; index >= 0; index--) owned.get(index).dispose();
        }catch(Throwable failure){
            for(index--; index >= 0; index--) Context.cleanup(failure, owned.get(index)::dispose);
            throw failure;
        }finally{
            owned.clear();
        }
    }

    @Override
    public void close(){
        dispose();
    }
}
