package seam;

import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;

public final class SeamRuntime{
    public final int id;
    public final String name;

    public final World world;
    public final GameState state;
    public final SeamGroupSet groups;
    public final EntityCollisions collisions;

    public SeamRuntime(int id, String name, int width, int height){
        this.id = id;
        this.name = name;
        this.world = new World();
        this.state = new GameState();
        this.groups = new SeamGroupSet(width, height);
        this.collisions = new EntityCollisions();

        this.world.resize(width, height);

        if(this.world.tiles != null){
            this.world.tiles.fill();
        }
    }

    public SeamRuntime(int id, String name, World world, GameState state, SeamGroupSet groups, EntityCollisions collisions){
        this.id = id;
        this.name = name;
        this.world = world;
        this.state = state;
        this.groups = groups;
        this.collisions = collisions;
    }

    public static SeamRuntime wrapCurrent(int id, String name){
        return new SeamRuntime(
        id,
        name,
        Vars.world,
        Vars.state,
        SeamGroupSet.wrapCurrent(),
        Vars.collisions
        );
    }
}