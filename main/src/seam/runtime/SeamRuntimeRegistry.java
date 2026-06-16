package seam.runtime;

import arc.struct.*;

public final class SeamRuntimeRegistry{
    private final IntMap<SeamRuntime> byId = new IntMap<>();
    private final Seq<SeamRuntime> ordered = new Seq<>();

    public SeamRuntime create(SeamRuntimeConfig config){
        if(config == null){
            throw new NullPointerException("config");
        }

        return register(new SeamRuntime(config));
    }

    public SeamRuntime register(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(runtime.disposed()){
            throw new IllegalArgumentException("Cannot register disposed runtime: " + runtime);
        }

        if(byId.containsKey(runtime.id)){
            throw new IllegalArgumentException("Runtime id is already registered: " + runtime.id);
        }

        byId.put(runtime.id, runtime);
        ordered.add(runtime);

        return runtime;
    }

    public SeamRuntime get(int id){
        return byId.get(id);
    }

    public boolean contains(int id){
        return byId.containsKey(id);
    }

    public boolean contains(SeamRuntime runtime){
        return runtime != null && byId.get(runtime.id) == runtime;
    }

    public Seq<SeamRuntime> all(){
        return ordered;
    }

    public int size(){
        return ordered.size;
    }

    public boolean empty(){
        return ordered.isEmpty();
    }

    public SeamRuntime remove(int id){
        SeamRuntime runtime = byId.remove(id);

        if(runtime != null){
            ordered.remove(runtime, true);
            runtime.dispose();
        }

        return runtime;
    }

    public void clear(){
        for(SeamRuntime runtime : ordered){
            runtime.dispose();
        }

        byId.clear();
        ordered.clear();
    }
}