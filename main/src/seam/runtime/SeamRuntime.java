package seam.runtime;

import arc.graphics.*;
import arc.struct.*;
import mindustry.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.core.GameState.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.blocks.*;
import seam.entities.*;

public final class SeamRuntime{
    public enum Status{
        created,
        loading,
        loaded,
        disposed
    }

    public final int id;
    public final String name;
    public final int initialWidth;
    public final int initialHeight;

    public final World world;
    public final GameState state;
    public final SeamGroupSet groups;
    public final EntityCollisions collisions;
    public final Waves waves;
    public final Player player;

    public PhysicsProcess physics;
    public AvoidanceProcess avoidance;
    public final Seq<AsyncProcess> asyncProcesses = new Seq<>();

    public float cameraX;
    public float cameraY;
    public float cameraWidth;
    public float cameraHeight;

    private Status status = Status.created;
    private Status statusBeforeLoad = Status.created;

    public SeamRuntime(SeamRuntimeConfig config){
        config.validate();

        id = config.id;
        name = config.name;
        initialWidth = config.width;
        initialHeight = config.height;

        world = new World();
        state = new GameState();
        groups = new SeamGroupSet(config.width, config.height);
        collisions = new EntityCollisions();
        waves = new Waves();
        player = createRuntimePlayer();

        resetCameraToSize(config.width, config.height);
    }

    public SeamRuntime(int id, String name, int width, int height){
        this(
        SeamRuntimeConfig.builder()
        .id(id)
        .name(name)
        .size(width, height)
        .build()
        );
    }

    public Status status(){
        return status;
    }

    public boolean disposed(){
        return status == Status.disposed;
    }

    public boolean loaded(){
        return status == Status.loaded;
    }

    public boolean loading(){
        return status == Status.loading;
    }

    public boolean created(){
        return status == Status.created;
    }

    public boolean worldReady(){
        return status == Status.loaded
        && world != null
        && world.tiles != null
        && world.tiles.width > 0
        && world.tiles.height > 0;
    }

    public void resetCamera(){
        resetCameraToSize(world.width(), world.height());
    }

    public void resetCameraToSize(int width, int height){
        cameraX = width * Vars.tilesize / 2f;
        cameraY = height * Vars.tilesize / 2f;
        cameraWidth = Math.max(width * Vars.tilesize, Vars.tilesize);
        cameraHeight = Math.max(height * Vars.tilesize, Vars.tilesize);
    }

    public void setCamera(float x, float y, float width, float height){
        if(width <= 0f || height <= 0f){
            throw new IllegalArgumentException("Runtime camera size must be positive.");
        }

        cameraX = x;
        cameraY = y;
        cameraWidth = width;
        cameraHeight = height;
    }

    public void captureCamera(Camera camera){
        if(camera == null){
            return;
        }

        cameraX = camera.position.x;
        cameraY = camera.position.y;

        if(camera.width > 0f){
            cameraWidth = camera.width;
        }

        if(camera.height > 0f){
            cameraHeight = camera.height;
        }
    }

    public void applyCamera(Camera camera){
        if(camera == null){
            return;
        }

        if(cameraWidth <= 0f || cameraHeight <= 0f){
            resetCamera();
        }

        camera.position.x = cameraX;
        camera.position.y = cameraY;
        camera.width = cameraWidth;
        camera.height = cameraHeight;
        camera.update();
    }

    public void beginLoad(){
        requireAlive();

        if(status == Status.loading){
            throw new IllegalStateException("Runtime '" + name + "' is already loading.");
        }

        statusBeforeLoad = status;
        status = Status.loading;
    }

    public void prepareForStateLoad(){
        requireLoading();

        groups.clear();
        resetStateForLoad();
        resetRuntimePlayer();
        resetAsyncProcesses();
    }

    public void completeLoad(){
        requireLoading();

        resetCamera();
        status = Status.loaded;
        statusBeforeLoad = Status.loaded;
    }

    public void failLoad(){
        if(status == Status.loading){
            status = statusBeforeLoad == null ? Status.created : statusBeforeLoad;
        }
    }

    public void ensureAsyncProcessesForInstalledContext(){
        requireWorldReady();

        boolean rebuild = physics == null
        || avoidance == null
        || asyncProcesses.size != 2
        || !asyncProcesses.contains(process -> process == physics)
        || !asyncProcesses.contains(process -> process == avoidance);

        if(rebuild){
            resetAsyncProcesses();

            physics = new PhysicsProcess();
            avoidance = new AvoidanceProcess();

            asyncProcesses.clear();
            asyncProcesses.add(physics);
            asyncProcesses.add(avoidance);
        }

        for(AsyncProcess process : asyncProcesses){
            process.init();
        }
    }

    public void requireAlive(){
        if(disposed()){
            throw new IllegalStateException("Runtime '" + name + "' is disposed.");
        }
    }

    public void requireLoadable(){
        requireAlive();

        if(status == Status.loading){
            throw new IllegalStateException("Runtime '" + name + "' is already loading.");
        }
    }

    public void requireLoading(){
        requireAlive();

        if(status != Status.loading){
            throw new IllegalStateException("Runtime '" + name + "' is not loading. Current status: " + status);
        }
    }

    public void requireLoaded(){
        requireAlive();

        if(status != Status.loaded){
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
        if(status == Status.disposed){
            return;
        }

        groups.clear();

        if(player.isAdded()){
            player.remove();
        }

        resetAsyncProcesses();

        status = Status.disposed;
        statusBeforeLoad = Status.disposed;
    }

    private Player createRuntimePlayer(){
        Player created = Player.create();
        Player host = Vars.player;

        if(host != null){
            created.name = host.name;
            created.locale = host.locale;
            created.color.set(host.color);
            created.admin = host.admin;
            created.team(host.team());
        }else{
            created.team(Team.sharded);
        }

        return created;
    }

    private void resetRuntimePlayer(){
        if(player.isAdded()){
            player.remove();
        }

        player.reset();

        Player host = Vars.player;

        if(host != null && host != player){
            player.name = host.name;
            player.locale = host.locale;
            player.color.set(host.color);
            player.admin = host.admin;
            player.team(host.team());
        }else{
            player.team(Team.sharded);
        }
    }

    private void resetAsyncProcesses(){
        for(AsyncProcess process : asyncProcesses){
            process.reset();
        }

        asyncProcesses.clear();
        physics = null;
        avoidance = null;
    }

    private void resetStateForLoad(){
        state.wave = 1;
        state.wavetime = 0f;
        state.tick = 0;
        state.updateId = 0;
        state.gameOver = false;
        state.afterGameOver = false;
        state.won = false;
        state.serverTps = -1;
        state.map = Vars.emptyMap;
        state.rules = new Rules();
        state.stats = new GameStats();
        state.markers = new MapMarkers();
        state.mapLocales = new MapLocales();
        state.envAttrs = new Attributes();
        state.teams = new Teams();
        state.patcher = new DataPatcher();
        state.enemies = 0;
        state.playtestingMap = null;
        state.set(State.menu);
    }

    @Override
    public String toString(){
        return "SeamRuntime{" +
        "id=" + id +
        ", name='" + name + '\'' +
        ", status=" + status +
        '}';
    }
}