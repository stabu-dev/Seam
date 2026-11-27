package seam.core;

import arc.func.*;
import arc.struct.*;
import arc.util.*;

/**
 * Exchanged storage cell per world context. Unconfigured worlds share the host cell.
 * Register once in host scope, one binding per storage location; callbacks must not enter contexts.
 */
public final class Binding<T> implements Disposable, AutoCloseable{
    static final Seq<Binding<?>> all = new Seq<>();

    private Prov<T> read;
    private Cons<T> write;
    private final Seq<Value<T>> cells = new Seq<>();
    private final Seq<Context> owners = new Seq<>();
    private final Value<T> host = new Value<>(null);
    private Value<T> installed = host;
    //a throwing writer may have partially mutated storage, so retry even the last installed cell
    private boolean uncertain, disposed;

    /** Binds a static field or global slot via {@code read}/{@code write}. Register once on startup per target. */
    public Binding(Prov<T> read, Cons<T> write){
        Context.checkHost();
        if(read == null || write == null) throw new IllegalArgumentException("A reader and writer are required.");
        this.read = read;
        this.write = write;
        all.add(this);
    }

    /** Assigns a fresh cell to {@code world}; existing aliases keep their previous cell. */
    public void own(Context world, T value){
        Context.checkInactive(world);
        checkOpen();
        world.checkUsable();
        Value<T> cell = new Value<>(value);
        cells.add(cell);
        assign(world, cell);
    }

    /** Aliases {@code source}'s current cell (null selects host storage). */
    public void share(Context world, @Nullable Context source){
        Context.checkInactive(world);
        checkOpen();
        world.checkUsable();
        if(source != null) source.checkUsable();
        assign(world, value(source));
    }

    /** Returns {@code world} to shared host storage. */
    public void inherit(Context world){
        share(world, null);
    }

    /** @return whether {@code world} uses its own cell instead of inheriting from the host */
    public boolean isOwned(Context world){
        Context.checkThread();
        return differs(world, null);
    }

    @Override
    public boolean isDisposed(){
        Context.checkThread();
        return disposed;
    }

    /** Drops all stored values and unregisters this binding on the host thread. */
    @Override
    public void dispose(){
        Context.checkHost();
        if(disposed) return;
        disposed = true;
        all.remove(this, true);
        read = null;
        write = null;
        host.value = null;
        installed = host;
        for(Context world : owners) world.bindings.remove(this);
        owners.clear();
        for(Value<T> cell : cells) cell.value = null;
        cells.clear();
    }

    @Override
    public void close(){
        dispose();
    }

    private void assign(Context world, Value<T> cell){
        if(cell != host) cell.references++;
        Value<?> previous = world.bindings.remove(this);
        if(previous != null) detach(world, previous);
        if(cell != host){
            world.bindings.put(this, cell);
            owners.add(world);
        }
    }

    @SuppressWarnings("unchecked")
    void detach(Context world, Value<?> cell){
        owners.remove(world, true);
        if(--cell.references == 0){
            cells.remove((Value<T>)cell, true);
            cell.value = null;
        }
    }

    private void checkOpen(){
        if(disposed) throw new IllegalStateException("Binding is disposed.");
    }

    @SuppressWarnings("unchecked")
    private Value<T> value(Context world){
        return world == null ? host : (Value<T>)world.bindings.get(this, host);
    }

    boolean differs(Context first, Context second){
        return value(first) != value(second);
    }

    boolean rejectExposed(Context target){
        if(installed == value(target) && !uncertain) return false;
        installed.valid = false;
        return true;
    }

    void capture(Context world, boolean leaving, boolean salvage){
        Value<T> cell = value(world);
        if(salvage && (!cell.valid || installed != cell || uncertain)) return;
        if(!cell.valid) throw new IllegalStateException("Invalid binding state: " + this);
        try{
            cell.value = read.get();
        }catch(Throwable failure){
            if(leaving) cell.valid = false;
            throw failure;
        }
    }

    void install(Context world){
        Value<T> cell = value(world);
        if(cell == installed && !uncertain) return;
        if(!cell.valid) throw new IllegalStateException("Invalid binding state: " + this);
        uncertain = true;
        write.get(cell.value);
        installed = cell;
        uncertain = false;
    }

    void release(){
        host.value = null;
    }

    static void validate(Context world){
        Binding<?> binding = invalid(world);
        if(binding != null) throw new IllegalStateException("Invalid binding state: " + binding);
    }

    static Binding<?> invalid(Context world){
        for(Binding<?> binding : all){
            if(!binding.value(world).valid) return binding;
        }
        return null;
    }

    static final class Value<T>{
        T value;
        boolean valid = true;
        int references;

        Value(T value){
            this.value = value;
        }
    }
}
