package seam.io;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.io.SaveFileReader.*;
import seam.core.*;
import seam.gen.*;

import java.io.*;

/** Named child runtimes embedded in one parent save chunk. Imports require a local declaration. */
public final class WorldStore implements CustomChunk, Disposable, AutoCloseable{
    private static final int version = 1;

    static{
        //keep WorldStore markers in child chunk maps so undeclared nested store imports fail explicitly
        Context.inheritChunks((key, chunk) -> chunk instanceof WorldStore);
    }

    /** Factories and hooks for each loaded key. */
    public Cons2<String, Context.Config> configure = (key, config) -> {};
    /** Resolves cross-world associations before publication; must keep the staged keys and runtimes usable. */
    public Cons<OrderedMap<String, Context>> afterRead = worlds -> {};

    private final String name;
    private Context parent;
    private OrderedMap<String, CustomChunk> registry;
    private OrderedMap<String, Context> worlds = new OrderedMap<>();
    private Cons<Context> releasePolicy;
    private boolean disposed, busy;
    private final Cons<ResetEvent> reset = event -> { if(!disposed) clear(); };

    /** Registers in the installed parent's chunk and event registries. {@code releasePolicy} handles published runtimes on reset, replacement and close. */
    public WorldStore(String name, Cons<Context> releasePolicy){
        Scopes.checkState();
        if(name == null || name.isEmpty() || releasePolicy == null) throw new IllegalArgumentException("A chunk name and explicit release policy are required.");
        this.name = name;
        this.releasePolicy = releasePolicy;
        parent = Context.current();
        if(parent != null) Scopes.checkUsable(parent);
        registry = Engine.saveCustomChunks();
        CustomChunk previous = registry.get(name);
        if(previous != null && !(previous instanceof WorldStore store && (store.disposed || store.parent != parent))){
            throw new IllegalArgumentException("Save chunk already registered: " + name);
        }
        if(parent != null) parent.own(this);
        SaveVersion.addCustomChunk(name, this);
        Events.on(ResetEvent.class, reset);
    }

    /** Finds the store belonging to the installed world; inherited parent handlers return null. */
    public static WorldStore current(String name){
        Scopes.checkState();
        CustomChunk chunk = Engine.saveCustomChunks().get(name);
        return chunk instanceof WorldStore store && store.parent == Context.current() && !store.disposed ? store : null;
    }

    /** @return world registered under {@code key}, or null if absent */
    public Context get(String key){
        Scopes.checkThread();
        checkOpen();
        return worlds.get(key);
    }

    /** @return copy of registered keys in insertion order */
    public Seq<String> keys(){
        Scopes.checkThread();
        checkOpen();
        return new Seq<>(worlds.orderedKeys());
    }

    /** Takes disposal responsibility under {@code key}. A world closed elsewhere drops out on the next write. */
    public void put(String key, Context world){
        checkMutable();
        if(key == null || key.isEmpty() || world == null || world == parent) throw new IllegalArgumentException("A child runtime and nonempty key are required.");
        Scopes.checkUsable(world);
        if(worlds.containsKey(key) || worlds.containsValue(world, true)) throw new IllegalArgumentException("A key and runtime may only be registered once.");
        worlds.put(key, world);
    }

    /** Detaches without closing; disposal responsibility returns to the caller. */
    public Context remove(String key){
        checkMutable();
        return worlds.remove(key);
    }

    /** Clears all registered worlds and passes each to the store's release policy. */
    public void clear(){
        checkMutable();
        OrderedMap<String, Context> previous = worlds;
        worlds = new OrderedMap<>();
        dispatch(previous.values().toSeq(), releasePolicy, null);
    }

    @Override
    public boolean shouldWrite(){
        return !disposed && parent == Context.current();
    }

    @Override
    public boolean writeNet(){
        return false;
    }

    @Override
    public void write(DataOutput output) throws IOException{
        Scopes.checkState();
        checkOpen();
        if(busy || parent != Context.current()) throw new IOException("Store is recursive or not installed: " + name);
        busy = true;
        try{
            for(String key : worlds.orderedKeys().copy()) if(worlds.get(key).isDisposed()) worlds.remove(key);
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.writeInt(version);
            data.writeInt(worlds.size);
            for(var entry : worlds){
                data.writeUTF(entry.key);
                Snapshot snapshot = entry.value.get(() -> new Snapshot(WorldIO.write(), Time.getInternalTime(), Time.time, Vars.state.getState()));
                data.writeUTF(snapshot.state.name());
                data.writeDouble(snapshot.raw);
                data.writeFloat(snapshot.time);
                data.writeInt(snapshot.bytes.length);
                data.write(snapshot.bytes);
            }
            output.writeInt(bytes.size());
            output.write(bytes.toByteArray());
        }finally{
            busy = false;
        }
    }

    @Override
    public void read(DataInput input) throws IOException{
        read(input, -1);
    }

    @Override
    public void read(DataInput input, int length) throws IOException{
        Scopes.checkState();
        if(disposed || parent != Context.current()) throw new IOException("Declare a local store with its configuration, resolver and release policy before importing: " + name);
        if(busy) throw new IOException("Store is already reading or writing: " + name);
        busy = true;
        try{
            replace(input, length);
        }finally{
            busy = false;
        }
    }

    private void replace(DataInput input, int length) throws IOException{
        Cons2<String, Context.Config> factory = configure;
        Cons<OrderedMap<String, Context>> resolver = afterRead;
        int size = input.readInt();
        if(size < 0 || length >= 0 && size != length - 4) throw new IOException("Invalid world-store length.");
        byte[] bytes = readBytes(input, size);
        var data = new DataInputStream(new ByteArrayInputStream(bytes));
        if(data.readInt() != version) throw new IOException("Unknown world-store version.");
        int amount = data.readInt();
        if(amount < 0) throw new IOException("Negative world count.");
        var records = new OrderedMap<String, Snapshot>();
        for(int i = 0; i < amount; i++){
            String key = data.readUTF();
            if(key.isEmpty() || records.containsKey(key)) throw new IOException("Empty or duplicate world key: " + key);
            GameState.State state;
            try{
                state = GameState.State.valueOf(data.readUTF());
            }catch(IllegalArgumentException error){
                throw new IOException("Unknown runtime state.", error);
            }
            double raw = data.readDouble();
            float time = data.readFloat();
            int count = data.readInt();
            if(count < 0 || count > data.available()) throw new IOException("Invalid native world length.");
            records.put(key, new Snapshot(readBytes(data, count), raw, time, state));
        }
        if(data.available() != 0) throw new IOException("Trailing world-store data.");
        var staged = new OrderedMap<String, Context>();
        var created = new Seq<Context>();
        try{
            for(var entry : records){
                String key = entry.key;
                Snapshot snapshot = entry.value;
                Context loaded = WorldIO.read(snapshot.bytes, config -> {
                    factory.get(key, config);
                    Cons<Context> after = config.after;
                    config.after = world -> {
                        Time.setInternalTime(snapshot.raw);
                        Time.time = snapshot.time;
                        Vars.state.set(snapshot.state);
                        after.get(world);
                    };
                });
                created.add(loaded);
                checkStaged(key, loaded);
                staged.put(key, loaded);
            }
            resolver.get(staged);
            if(staged.size != created.size || !staged.orderedKeys().equals(records.orderedKeys())) throw new IOException("After-read changed world membership.");
            for(int i = 0; i < created.size; i++){
                String key = records.orderedKeys().get(i);
                Context world = created.get(i);
                if(staged.get(key) != world) throw new IOException("After-read replaced a world: " + key);
                checkStaged(key, world);
            }
        }catch(Throwable failure){
            dispatch(created, Context::closeLater, failure);
            throw failure;
        }
        OrderedMap<String, Context> previous = worlds;
        worlds = staged;
        dispatch(previous.values().toSeq(), releasePolicy, null);
    }

    private static void checkStaged(String key, Context world) throws IOException{
        if(!Scopes.isReady(world)) throw new IOException("Loaded world is unusable or closing: " + key);
    }

    @Override
    public boolean isDisposed(){
        Scopes.checkThread();
        return disposed;
    }

    /** Unregisters this chunk, stops listening for resets on the parent, and releases remaining worlds. */
    @Override
    public void dispose(){
        Scopes.checkHost();
        if(disposed) return;
        if(busy) throw new IllegalStateException("Store is reading or writing.");
        disposed = true;
        if(registry.get(name) == this) registry.remove(name);
        Context owner = parent;
        parent = null;
        registry = null;
        OrderedMap<String, Context> previous = worlds;
        worlds = new OrderedMap<>();
        Cons<Context> release = releasePolicy;
        releasePolicy = null;
        configure = null;
        afterRead = null;
        try{
            if(owner == null){
                Events.remove(ResetEvent.class, reset);
            }else if(!owner.isDisposed()){
                owner.run(() -> Events.remove(ResetEvent.class, reset));
            }
        }catch(Throwable failure){
            dispatch(previous.values().toSeq(), release, failure);
            throw failure;
        }
        dispatch(previous.values().toSeq(), release, null);
    }

    @Override
    public void close(){
        dispose();
    }

    private void checkOpen(){
        if(disposed) throw new IllegalStateException("World store is disposed.");
    }

    private void checkMutable(){
        Scopes.checkState();
        checkOpen();
        if(busy) throw new IllegalStateException("Store is reading or writing.");
    }

    private static void dispatch(Seq<Context> worlds, Cons<Context> release, Throwable failure){
        if(failure != null){
            for(Context world : worlds) Scopes.cleanup(failure, () -> release.get(world));
            return;
        }
        int index = 0;
        try{
            for(; index < worlds.size; index++) release.get(worlds.get(index));
        }catch(Throwable next){
            for(index++; index < worlds.size; index++){
                Context world = worlds.get(index);
                Scopes.cleanup(next, () -> release.get(world));
            }
            throw next;
        }
    }

    private static byte[] readBytes(DataInput input, int length) throws IOException{
        var bytes = new ByteArrayOutputStream(Math.min(length, 8192));
        byte[] buffer = new byte[Math.min(length, 8192)];
        for(int remaining = length; remaining > 0;){
            int count = Math.min(remaining, buffer.length);
            input.readFully(buffer, 0, count);
            bytes.write(buffer, 0, count);
            remaining -= count;
        }
        return bytes.toByteArray();
    }

    private record Snapshot(byte[] bytes, double raw, float time, GameState.State state){}
}
