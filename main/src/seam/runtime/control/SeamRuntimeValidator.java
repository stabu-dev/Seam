package seam.runtime.control;

import seam.entities.*;
import seam.runtime.*;
import arc.struct.*;
import mindustry.*;
import mindustry.gen.*;

public final class SeamRuntimeValidator{
    private SeamRuntimeValidator(){
    }

    public static void validateRegistry(SeamRuntimeRegistry registry){
        require(registry != null, "Runtime registry is null.");
        require(registry.main() != null, "Main runtime is not registered.");

        IntSet ids = new IntSet();

        for(SeamRuntime runtime : registry.all()){
            require(runtime != null, "Registry contains null runtime.");
            require(ids.add(runtime.id), "Duplicate runtime id: " + runtime.id);
            require(registry.get(runtime.id) == runtime, "Registry id " + runtime.id + " does not point to its runtime.");

            validateRuntime(runtime, false);
        }

        SeamRuntime main = registry.main();

        require(main.id == 0, "Main runtime id must be 0.");
        require(main.kind == SeamRuntime.Kind.main, "Main runtime kind must be main.");
        require(registry.get(0) == main, "Registry id 0 does not point to main runtime.");
    }

    public static void validateRuntime(SeamRuntime runtime){
        validateRuntime(runtime, false);
    }

    public static void validateRuntime(SeamRuntime runtime, boolean deepTiles){
        require(runtime != null, "Runtime is null.");
        require(runtime.id >= 0, "Runtime id must be >= 0.");
        require(runtime.name != null && !runtime.name.isBlank(), "Runtime name is blank.");
        require(runtime.kind != null, "Runtime kind is null.");
        require(runtime.status() == SeamRuntime.Status.loaded, "Runtime is not loaded: " + runtime);

        require(runtime.world != null, "Runtime world is null: " + runtime);
        require(runtime.state != null, "Runtime state is null: " + runtime);
        require(runtime.groups != null, "Runtime group set is null: " + runtime);
        require(runtime.collisions != null, "Runtime collisions is null: " + runtime);
        require(runtime.clock != null, "Runtime clock is null: " + runtime);
        require(runtime.mutations != null, "Runtime mutation queue is null: " + runtime);
        require(runtime.renderInvalidation != null, "Runtime render invalidation queue is null: " + runtime);

        validateGroups(runtime.groups);

        if(runtime.kind == SeamRuntime.Kind.main){
            require(runtime.id == 0, "Main runtime id must be 0.");

            if(runtime.worldReady()){
                require(runtime.world.width() > 0, "Main runtime world width must be positive when ready.");
                require(runtime.world.height() > 0, "Main runtime world height must be positive when ready.");
            }

            return;
        }

        require(runtime.id != 0, "Subworld runtime id cannot be 0.");
        require(runtime.world.tiles != null, "Subworld runtime world tiles are null: " + runtime);
        require(runtime.world.width() > 0, "Subworld runtime world width must be positive: " + runtime);
        require(runtime.world.height() > 0, "Subworld runtime world height must be positive: " + runtime);

        if(deepTiles){
            validateTiles(runtime);
        }
    }

    public static void validateActiveContext(SeamRuntime runtime){
        validateRuntime(runtime, false);

        same(Vars.world, runtime.world, "Vars.world does not point to active runtime world.");
        same(Vars.state, runtime.state, "Vars.state does not point to active runtime state.");
        same(Vars.collisions, runtime.collisions, "Vars.collisions does not point to active runtime collisions.");

        same(Groups.all, runtime.groups.all, "Groups.all mismatch.");
        same(Groups.player, runtime.groups.player, "Groups.player mismatch.");
        same(Groups.bullet, runtime.groups.bullet, "Groups.bullet mismatch.");
        same(Groups.unit, runtime.groups.unit, "Groups.unit mismatch.");
        same(Groups.build, runtime.groups.build, "Groups.build mismatch.");
        same(Groups.sync, runtime.groups.sync, "Groups.sync mismatch.");
        same(Groups.draw, runtime.groups.draw, "Groups.draw mismatch.");
        same(Groups.fire, runtime.groups.fire, "Groups.fire mismatch.");
        same(Groups.puddle, runtime.groups.puddle, "Groups.puddle mismatch.");
        same(Groups.weather, runtime.groups.weather, "Groups.weather mismatch.");
        same(Groups.label, runtime.groups.label, "Groups.label mismatch.");
        same(Groups.powerGraph, runtime.groups.powerGraph, "Groups.powerGraph mismatch.");
    }

    public static void validateRestoredToMain(SeamRuntimeRegistry registry, SeamRuntimeStack stack){
        validateRegistry(registry);

        require(stack != null, "Runtime stack is null.");
        require(!stack.active(), "Runtime stack is still active.");

        validateActiveContext(registry.main());
    }

    public static void validateGroups(SeamGroupSet groups){
        require(groups != null, "Group set is null.");

        require(groups.all != null, "Groups.all is null.");
        require(groups.player != null, "Groups.player is null.");
        require(groups.bullet != null, "Groups.bullet is null.");
        require(groups.unit != null, "Groups.unit is null.");
        require(groups.build != null, "Groups.build is null.");
        require(groups.sync != null, "Groups.sync is null.");
        require(groups.draw != null, "Groups.draw is null.");
        require(groups.fire != null, "Groups.fire is null.");
        require(groups.puddle != null, "Groups.puddle is null.");
        require(groups.weather != null, "Groups.weather is null.");
        require(groups.label != null, "Groups.label is null.");
        require(groups.powerGraph != null, "Groups.powerGraph is null.");
    }

    private static void validateTiles(SeamRuntime runtime){
        for(int x = 0; x < runtime.world.width(); x++){
            for(int y = 0; y < runtime.world.height(); y++){
                require(
                runtime.world.tile(x, y) != null,
                "Runtime tile is null at " + x + "," + y + " in " + runtime
                );
            }
        }
    }

    private static void require(boolean condition, String message){
        if(!condition){
            throw new IllegalStateException("[Seam validation] " + message);
        }
    }

    private static void same(Object actual, Object expected, String message){
        if(actual != expected){
            throw new IllegalStateException("[Seam validation] " + message);
        }
    }
}
