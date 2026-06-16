package seam.runtime.control;

import arc.*;
import arc.struct.*;
import seam.entities.*;
import seam.runtime.*;

public final class SeamRuntimeValidator{
    private static final float cameraEpsilon = 0.001f;

    private SeamRuntimeValidator(){
    }

    public static void validateRegistry(SeamRuntimeRegistry registry){
        require(registry != null, "Runtime registry is null.");

        IntSet ids = new IntSet();

        for(SeamRuntime runtime : registry.all()){
            require(runtime != null, "Registry contains null runtime.");
            require(ids.add(runtime.id), "Duplicate runtime id: " + runtime.id);
            require(registry.get(runtime.id) == runtime, "Registry id " + runtime.id + " does not point to its runtime.");
            validateRuntimeRecord(runtime);
        }
    }

    public static void validateRuntimeRecord(SeamRuntime runtime){
        require(runtime != null, "Runtime is null.");
        require(runtime.id >= 0, "Runtime id must be >= 0.");
        require(runtime.name != null && !runtime.name.isBlank(), "Runtime name is blank.");
        require(runtime.status() != SeamRuntime.Status.disposed, "Runtime is disposed: " + runtime);

        require(runtime.world != null, "Runtime world is null: " + runtime);
        require(runtime.state != null, "Runtime state is null: " + runtime);
        require(runtime.groups != null, "Runtime group set is null: " + runtime);
        require(runtime.collisions != null, "Runtime collisions is null: " + runtime);
        require(runtime.waves != null, "Runtime waves is null: " + runtime);
        require(runtime.player != null, "Runtime player is null: " + runtime);
        require(runtime.asyncProcesses != null, "Runtime async process list is null: " + runtime);
        require(runtime.cameraWidth > 0f, "Runtime camera width must be positive: " + runtime);
        require(runtime.cameraHeight > 0f, "Runtime camera height must be positive: " + runtime);

        if(runtime.physics != null){
            require(runtime.asyncProcesses.contains(process -> process == runtime.physics), "Runtime async process list does not contain physics process: " + runtime);
        }

        if(runtime.avoidance != null){
            require(runtime.asyncProcesses.contains(process -> process == runtime.avoidance), "Runtime async process list does not contain avoidance process: " + runtime);
        }

        validateGroups(runtime.groups);
    }

    public static void validateRuntime(SeamRuntime runtime){
        validateRuntime(runtime, false);
    }

    public static void validateRuntime(SeamRuntime runtime, boolean deepTiles){
        validateRuntimeRecord(runtime);

        require(runtime.status() == SeamRuntime.Status.loaded, "Runtime is not loaded: " + runtime);
        require(runtime.world.tiles != null, "Runtime world tiles are null: " + runtime);
        require(runtime.world.width() > 0, "Runtime world width must be positive: " + runtime);
        require(runtime.world.height() > 0, "Runtime world height must be positive: " + runtime);

        if(deepTiles){
            validateTiles(runtime);
        }
    }

    public static void validateActiveContext(SeamRuntime runtime){
        validateRuntime(runtime, false);
        SeamContextSlots.validateRuntimeInstalled(runtime);

        if(Core.camera != null){
            near(Core.camera.position.x, runtime.cameraX, "Core.camera.position.x mismatch.");
            near(Core.camera.position.y, runtime.cameraY, "Core.camera.position.y mismatch.");
            near(Core.camera.width, runtime.cameraWidth, "Core.camera.width mismatch.");
            near(Core.camera.height, runtime.cameraHeight, "Core.camera.height mismatch.");
        }
    }

    public static void validateInactive(SeamStateInstaller installer){
        require(installer != null, "Runtime state installer is null.");
        require(!installer.active(), "Runtime state installer is still active.");
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

    private static void near(float actual, float expected, String message){
        if(Math.abs(actual - expected) > cameraEpsilon){
            throw new IllegalStateException("[Seam validation] " + message + " Expected " + expected + ", got " + actual + ".");
        }
    }
}