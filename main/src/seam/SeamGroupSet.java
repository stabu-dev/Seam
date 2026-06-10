package seam;

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

    @SuppressWarnings("unchecked")
    public SeamGroupSet(int width, int height){
        float finalWorldBounds = 250f;
        float w = width * 8f + finalWorldBounds * 2;
        float h = height * 8f + finalWorldBounds * 2;

        all = new EntityGroup<>(Entityc.class, false, false, SeamGroupSetHelper.all);
        player = new EntityGroup<>(Player.class, false, true, SeamGroupSetHelper.player);
        bullet = new EntityGroup<>(Bullet.class, true, false, SeamGroupSetHelper.bullet);
        unit = new EntityGroup<>(Unit.class, true, true, SeamGroupSetHelper.unit);
        build = new EntityGroup<>(Building.class, false, false, SeamGroupSetHelper.build);
        sync = new EntityGroup<>(Syncc.class, false, true, SeamGroupSetHelper.sync);
        draw = new EntityGroup<>(Drawc.class, false, false, SeamGroupSetHelper.draw);
        fire = new EntityGroup<>(Fire.class, false, false, SeamGroupSetHelper.fire);
        puddle = new EntityGroup<>(Puddle.class, false, false, SeamGroupSetHelper.puddle);
        weather = new EntityGroup<>(WeatherState.class, false, false, SeamGroupSetHelper.weather);
        label = new EntityGroup<>(WorldLabel.class, false, true, SeamGroupSetHelper.label);
        powerGraph = new EntityGroup<>(PowerGraphUpdaterc.class, false, false, SeamGroupSetHelper.powerGraph);

        bullet.resize(-finalWorldBounds, -finalWorldBounds, w, h);
        unit.resize(-finalWorldBounds, -finalWorldBounds, w, h);
    }
}
