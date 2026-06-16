package seam.entities;

import mindustry.*;
import mindustry.entities.*;
import mindustry.gen.*;

public final class SeamGroupSet{
    public final EntityGroup<Entityc> all;
    public final EntityGroup<Player> player;
    public final EntityGroup<Bullet> bullet;
    public final EntityGroup<Unit> unit;
    public final EntityGroup<Building> build;
    public final EntityGroup<Syncc> sync;
    public final EntityGroup<Drawc> draw;
    public final EntityGroup<Fire> fire;
    public final EntityGroup<Puddle> puddle;
    public final EntityGroup<WeatherState> weather;
    public final EntityGroup<WorldLabel> label;
    public final EntityGroup<PowerGraphUpdaterc> powerGraph;

    public SeamGroupSet(int width, int height){
        all = new SeamEntityGroup<>(Entityc.class, false, false);
        player = new SeamEntityGroup<>(Player.class, false, true);
        bullet = new SeamEntityGroup<>(Bullet.class, true, false);
        unit = new SeamEntityGroup<>(Unit.class, true, true);
        build = new SeamEntityGroup<>(Building.class, false, false);
        sync = new SeamEntityGroup<>(Syncc.class, false, true);
        draw = new SeamEntityGroup<>(Drawc.class, false, false);
        fire = new SeamEntityGroup<>(Fire.class, false, false);
        puddle = new SeamEntityGroup<>(Puddle.class, false, false);
        weather = new SeamEntityGroup<>(WeatherState.class, false, false);
        label = new SeamEntityGroup<>(WorldLabel.class, false, true);
        powerGraph = new SeamEntityGroup<>(PowerGraphUpdaterc.class, false, false);

        resize(width, height);
    }

    public void resize(int width, int height){
        float finalWorldBounds = Vars.finalWorldBounds;
        float w = width * Vars.tilesize + finalWorldBounds * 2f;
        float h = height * Vars.tilesize + finalWorldBounds * 2f;

        bullet.resize(-finalWorldBounds, -finalWorldBounds, w, h);
        unit.resize(-finalWorldBounds, -finalWorldBounds, w, h);
    }

    public void clear(){
        all.clear();
        player.clear();
        bullet.clear();
        unit.clear();
        build.clear();
        sync.clear();
        draw.clear();
        fire.clear();
        puddle.clear();
        weather.clear();
        label.clear();
        powerGraph.clear();
    }
}