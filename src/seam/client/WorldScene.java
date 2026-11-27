package seam.client;

import arc.*;
import arc.func.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.*;
import arc.util.pooling.*;
import mindustry.gen.*;
import seam.annotations.Annotations.*;
import seam.core.*;
import seam.gen.*;

/** Per-world Arc scene whose actions, drawing and input callbacks run with its world installed. */
@SceneFacade(scene = Scene.class, input = InputProcessor.class, className = "WorldSceneBase")
public final class WorldScene extends WorldSceneBase{
    private static Binding<Scene> scenes;

    /** World entered while drawing or handling input for this scene. */
    public final Context world;

    /** @return whether {@code world} has an attached UI scene */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return scenes != null && scenes.isOwned(world);
    }

    /** Gives {@code world} its own Arc {@link Scene} and initializes UI styles inside it. Host thread only. */
    public static WorldScene attach(Context world){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(Core.scene == null) throw new IllegalStateException("World scenes need an initialized client scene.");
        if(scenes == null) scenes = new Binding<>(() -> Core.scene, scene -> Core.scene = scene);
        if(attached(world)) throw new IllegalStateException("The world already has a scene.");
        var scene = new WorldScene(world);
        boolean committed = false;
        Throwable failure = null;
        try{
            scenes.own(world, scene);
            scene.run(Tex::loadStyles);
            world.onDispose(scene::clear);
            world.own(() -> { if(Core.input != null) Core.input.removeProcessor(scene); });
            committed = true;
            return scene;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!committed){
                Throwable cleanup = Scopes.cleanup(failure, () -> scenes.inherit(world));
                cleanup = Scopes.cleanup(cleanup, scene::clear);
                cleanup = Scopes.cleanup(cleanup, () -> { if(Core.input != null) Core.input.removeProcessor(scene); });
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    private WorldScene(Context world){
        this.world = world;
    }

    @Override
    public void run(Runnable task){
        world.run(task);
    }

    @Override
    public <T> T get(Prov<T> task){
        return world.get(task);
    }

    @Override
    public void draw(){
        run(() -> {
            Mat projection = Pools.obtain(Mat.class, Mat::new).set(Draw.proj());
            try{
                super.draw();
            }finally{
                try{
                    Draw.proj(projection);
                }finally{
                    Pools.free(projection);
                }
            }
        });
    }
}
