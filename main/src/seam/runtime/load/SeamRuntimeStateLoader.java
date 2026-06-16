package seam.runtime.load;

import arc.func.*;
import mindustry.world.*;
import seam.runtime.*;
import seam.runtime.control.*;

public final class SeamRuntimeStateLoader{
    public void loadConfigured(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        loadEmpty(runtime, runtime.initialWidth, runtime.initialHeight);
    }

    public void loadEmpty(SeamRuntime runtime, int width, int height){
        load(runtime, width, height, Tiles::fill);
    }

    public void load(SeamRuntime runtime, int width, int height, Cons<Tiles> generator){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(generator == null){
            throw new NullPointerException("generator");
        }

        if(width <= 0 || height <= 0){
            throw new IllegalArgumentException("World size must be positive.");
        }

        runtime.requireLoadable();
        runtime.beginLoad();

        try{
            runtime.prepareForStateLoad();

            Tiles tiles = runtime.world.resize(width, height);
            generator.get(tiles);
            validateTilesFilled(tiles);

            runtime.groups.resize(width, height);
            runtime.completeLoad();
            SeamRuntimeValidator.validateRuntime(runtime, true);
        }catch(Throwable e){
            runtime.failLoad();
            throw e;
        }
    }

    private static void validateTilesFilled(Tiles tiles){
        for(int i = 0; i < tiles.width * tiles.height; i++){
            if(tiles.geti(i) == null){
                throw new IllegalStateException("Runtime state generator left null tile at raw index " + i + ".");
            }
        }
    }
}