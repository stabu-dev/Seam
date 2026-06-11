package seam.runtime;

import seam.runtime.control.*;
import arc.struct.*;

public final class SeamRuntimeRegistry{
    private final IntMap<SeamRuntime> byId = new IntMap<>();
    private final Seq<SeamRuntime> runtimes = new Seq<>();

    private SeamRuntime main;

    public SeamRuntime main(){
        return main;
    }

    public Seq<SeamRuntime> all(){
        return runtimes.copy();
    }

    public SeamRuntime get(int id){
        return byId.get(id);
    }

    public boolean contains(int id){
        return byId.containsKey(id);
    }

    public void refreshMain(){
        SeamRuntime refreshedMain = SeamRuntime.wrapCurrentMain();

        SeamRuntimeValidator.validateRuntime(refreshedMain, false);

        if(main != null){
            byId.remove(main.id);
            runtimes.remove(main, true);
        }

        main = refreshedMain;

        byId.put(main.id, main);
        runtimes.add(main);

        SeamRuntimeValidator.validateRegistry(this);
    }

    public SeamRuntime create(SeamRuntimeConfig config){
        config.validate();

        if(config.kind == SeamRuntime.Kind.main){
            throw new IllegalArgumentException("Main runtime is managed by refreshMain().");
        }

        if(config.id == 0){
            throw new IllegalArgumentException("Runtime id 0 is reserved for main runtime.");
        }

        if(byId.containsKey(config.id)){
            throw new IllegalArgumentException("Runtime id already exists: " + config.id);
        }

        SeamRuntime runtime = new SeamRuntime(config);

        SeamRuntimeValidator.validateRuntime(runtime, true);

        byId.put(runtime.id, runtime);
        runtimes.add(runtime);

        SeamRuntimeValidator.validateRegistry(this);

        return runtime;
    }

    public void remove(int id){
        SeamRuntime runtime = byId.get(id);

        if(runtime == null){
            return;
        }

        if(runtime.main()){
            throw new IllegalStateException("Main runtime cannot be removed.");
        }

        byId.remove(id);
        runtimes.remove(runtime, true);

        runtime.dispose();

        SeamRuntimeValidator.validateRegistry(this);
    }

    public void clearSubworlds(){
        Seq<SeamRuntime> copy = runtimes.copy();

        for(SeamRuntime runtime : copy){
            if(!runtime.main()){
                remove(runtime.id);
            }
        }

        SeamRuntimeValidator.validateRegistry(this);
    }
}
