package seam;

import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;

public final class SeamRuntime{
    public final int id;
    public final String name;
    public final SeamRuntimeKind kind;

    public final World world;
    public final GameState state;
    public final SeamGroupSet groups;
    public final EntityCollisions collisions;
    public final SeamClock clock;
    public final SeamMutationQueue mutations;

    private SeamRuntimeStatus status = SeamRuntimeStatus.created;

    private SeamRuntimeUpdatePolicy updatePolicy;
    private boolean validateOnUpdate = true;

    public SeamRuntime(SeamRuntimeConfig config){
        config.validate();

        if(config.kind == SeamRuntimeKind.main){
            throw new IllegalArgumentException("Main runtime must be created with wrapCurrentMain().");
        }

        this.id = config.id;
        this.name = config.name;
        this.kind = config.kind;
        this.updatePolicy = config.updatePolicy;

        this.world = new World();
        this.state = new GameState();
        this.groups = new SeamGroupSet(config.width, config.height);
        this.collisions = new EntityCollisions();
        this.clock = new SeamClock();
        this.mutations = new SeamMutationQueue();

        loadEmptyWorld(config.width, config.height);
    }

    public SeamRuntime(int id, String name, int width, int height){
        this(
        SeamRuntimeConfig.builder()
        .id(id)
        .name(name)
        .size(width, height)
        .kind(SeamRuntimeKind.subworld)
        .updatePolicy(SeamRuntimeUpdatePolicy.buildingsOnly())
        .build()
        );
    }

    private SeamRuntime(
    int id,
    String name,
    SeamRuntimeKind kind,
    World world,
    GameState state,
    SeamGroupSet groups,
    EntityCollisions collisions,
    SeamClock clock,
    SeamMutationQueue mutations,
    SeamRuntimeUpdatePolicy updatePolicy
    ){
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.world = world;
        this.state = state;
        this.groups = groups;
        this.collisions = collisions;
        this.clock = clock;
        this.mutations = mutations;
        this.updatePolicy = updatePolicy;
        this.status = SeamRuntimeStatus.loaded;
    }

    public static SeamRuntime wrapCurrentMain(){
        return new SeamRuntime(
        0,
        "main",
        SeamRuntimeKind.main,
        Vars.world,
        Vars.state,
        SeamGroupSet.wrapCurrent(),
        Vars.collisions,
        new SeamClock(),
        new SeamMutationQueue(),
        SeamRuntimeUpdatePolicy.disabled()
        );
    }

    private void loadEmptyWorld(int width, int height){
        setStatus(SeamRuntimeStatus.loading);

        world.resize(width, height);

        if(world.tiles != null){
            world.tiles.fill();
        }

        groups.resize(width, height);
        clock.reset();
        mutations.clear();

        setStatus(SeamRuntimeStatus.loaded);
    }

    public SeamRuntimeStatus status(){
        return status;
    }

    public boolean disposed(){
        return status == SeamRuntimeStatus.disposed;
    }

    public boolean loaded(){
        return status == SeamRuntimeStatus.loaded;
    }

    public boolean main(){
        return kind == SeamRuntimeKind.main;
    }

    public boolean worldReady(){
        return SeamLifecycle.worldReady(world);
    }

    public SeamRuntimeUpdatePolicy updatePolicy(){
        return updatePolicy;
    }

    public void updatePolicy(SeamRuntimeUpdatePolicy updatePolicy){
        if(updatePolicy == null){
            throw new NullPointerException("updatePolicy");
        }

        if(main() && updatePolicy.enabled){
            throw new IllegalStateException("Main runtime cannot be updated by SeamEngine.");
        }

        this.updatePolicy = updatePolicy;
    }

    public boolean updateEnabled(){
        return updatePolicy.enabled && loaded() && !main();
    }

    public void updateEnabled(boolean updateEnabled){
        updatePolicy(updatePolicy.withEnabled(updateEnabled));
    }

    public boolean validateOnUpdate(){
        return validateOnUpdate;
    }

    public void validateOnUpdate(boolean validateOnUpdate){
        this.validateOnUpdate = validateOnUpdate;
    }

    public void requireAlive(){
        if(disposed()){
            throw new IllegalStateException("Runtime '" + name + "' is disposed.");
        }
    }

    public void requireLoaded(){
        requireAlive();

        if(status != SeamRuntimeStatus.loaded){
            throw new IllegalStateException("Runtime '" + name + "' is not loaded. Current status: " + status);
        }
    }

    public void requireWorldReady(){
        requireLoaded();

        if(!worldReady()){
            throw new IllegalStateException("Runtime '" + name + "' world is not ready.");
        }
    }

    public void dispose(){
        if(main()){
            throw new IllegalStateException("Main runtime cannot be disposed.");
        }

        if(status == SeamRuntimeStatus.disposed){
            return;
        }

        groups.clear();
        clock.reset();
        mutations.clear();
        status = SeamRuntimeStatus.disposed;
        updatePolicy = updatePolicy.withEnabled(false);
    }

    private void setStatus(SeamRuntimeStatus status){
        this.status = status;
    }

    @Override
    public String toString(){
        return "SeamRuntime{" +
        "id=" + id +
        ", name='" + name + '\'' +
        ", kind=" + kind +
        ", status=" + status +
        ", clock=" + clock +
        ", updatePolicy=" + updatePolicy +
        '}';
    }
}