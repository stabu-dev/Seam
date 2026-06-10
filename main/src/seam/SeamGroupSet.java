package seam;

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
        float finalWorldBounds = Vars.finalWorldBounds;
        float w = width * Vars.tilesize + finalWorldBounds * 2f;
        float h = height * Vars.tilesize + finalWorldBounds * 2f;

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

        bullet.resize(-finalWorldBounds, -finalWorldBounds, w, h);
        unit.resize(-finalWorldBounds, -finalWorldBounds, w, h);
    }

    private SeamGroupSet(
    EntityGroup<Entityc> all,
    EntityGroup<Player> player,
    EntityGroup<Bullet> bullet,
    EntityGroup<Unit> unit,
    EntityGroup<Building> build,
    EntityGroup<Syncc> sync,
    EntityGroup<Drawc> draw,
    EntityGroup<Fire> fire,
    EntityGroup<Puddle> puddle,
    EntityGroup<WeatherState> weather,
    EntityGroup<WorldLabel> label,
    EntityGroup<PowerGraphUpdaterc> powerGraph
    ){
        this.all = all;
        this.player = player;
        this.bullet = bullet;
        this.unit = unit;
        this.build = build;
        this.sync = sync;
        this.draw = draw;
        this.fire = fire;
        this.puddle = puddle;
        this.weather = weather;
        this.label = label;
        this.powerGraph = powerGraph;
    }

    public static SeamGroupSet wrapCurrent(){
        return new SeamGroupSet(
        Groups.all,
        Groups.player,
        Groups.bullet,
        Groups.unit,
        Groups.build,
        Groups.sync,
        Groups.draw,
        Groups.fire,
        Groups.puddle,
        Groups.weather,
        Groups.label,
        Groups.powerGraph
        );
    }
}