package seam.runtime.services;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import seam.runtime.*;

import static mindustry.Vars.*;

public class SeamWaveSpawner{
    private static final float margin = 0f;
    private static final float coreMargin = tilesize * 2f;
    private static final float maxSteps = 30f;

    private final SeamRuntime runtime;
    private final Seq<Tile> spawns = new Seq<>(false);

    private boolean spawning;
    private boolean any;
    private int tmpCount;

    public SeamWaveSpawner(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
    }

    public Tile getFirstSpawn(){
        final Tile[] first = {null};

        eachGroundSpawn((cx, cy) -> {
            if(first[0] == null){
                first[0] = runtime.world.tile(cx, cy);
            }
        });

        return first[0];
    }

    public int countSpawns(){
        return spawns.size;
    }

    public Seq<Tile> getSpawns(){
        return spawns;
    }

    public boolean playerNear(){
        return runtime.state.hasSpawns()
        && player != null
        && !player.dead()
        && spawns.contains(tile -> Mathf.dst(tile.x * tilesize, tile.y * tilesize, player.x, player.y) < runtime.state.rules.dropZoneRadius && player.team() != runtime.state.rules.waveTeam);
    }

    public void spawnEnemies(){
        spawning = true;

        eachGroundSpawn(-1, (spawnX, spawnY, shockwave) -> {
            if(shockwave){
                doShockwave(spawnX, spawnY);
            }
        });

        for(SpawnGroup group : runtime.state.rules.spawns){
            if(group.type == null){
                continue;
            }

            int spawned = group.getSpawned(runtime.state.wave - 1);

            if(spawned == 0){
                continue;
            }

            int amount = spawned;

            if(group.type.flying){
                float spread = margin / 1.5f;

                eachFlyerSpawn(group.spawn, (spawnX, spawnY) -> {
                    for(int i = 0; i < amount; i++){
                        spawnUnit(group, spawnX + Mathf.range(spread), spawnY + Mathf.range(spread));
                    }
                });
            }else{
                float spread = tilesize * 2f;

                eachGroundSpawn(group.spawn, (spawnX, spawnY, shockwave) -> {
                    for(int i = 0; i < amount; i++){
                        Tmp.v1.rnd(spread);
                        spawnUnit(group, spawnX + Tmp.v1.x, spawnY + Tmp.v1.y);
                    }
                });
            }
        }

        Time.run(121f, () -> spawning = false);
    }

    public void spawnUnit(SpawnGroup group, float x, float y){
        group.createUnit(
        group.team == null ? runtime.state.rules.waveTeam : group.team,
        x,
        y,
        Angles.angle(x, y, runtime.world.width() / 2f * tilesize, runtime.world.height() / 2f * tilesize),
        runtime.state.wave - 1,
        this::spawnEffect
        );
    }

    public void doShockwave(float x, float y){
        Fx.spawnShockwave.at(x, y, runtime.state.rules.dropZoneRadius);
        Damage.damage(runtime.state.rules.waveTeam, x, y, runtime.state.rules.dropZoneRadius, 99999999f, true);
    }

    public void eachGroundSpawn(Intc2 cons){
        eachGroundSpawn(-1, (x, y, shockwave) -> cons.get(World.toTile(x), World.toTile(y)));
    }

    public int countGroundSpawns(){
        tmpCount = 0;
        eachGroundSpawn((x, y) -> tmpCount++);
        return tmpCount;
    }

    public int countFlyerSpawns(){
        tmpCount = 0;
        eachFlyerSpawn(-1, (x, y) -> tmpCount++);
        return tmpCount;
    }

    public boolean isSpawning(){
        return spawning && !net.client();
    }

    public void reset(){
        spawning = false;
        spawns.clear();

        if(!runtime.worldReady()){
            return;
        }

        for(Tile tile : runtime.world.tiles){
            if(tile != null && tile.overlay() == Blocks.spawn){
                spawns.add(tile);
            }
        }
    }

    public void spawnEffect(Unit unit){
        unit.apply(StatusEffects.unmoving, 30f);
        unit.apply(StatusEffects.invincible, 60f);
        unit.unloaded();

        Events.fire(new EventType.UnitSpawnEvent(unit));
        Call.spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
    }

    private void eachGroundSpawn(int filterPos, SpawnConsumer cons){
        if(runtime.state.hasSpawns()){
            for(Tile spawn : spawns){
                if(filterPos != -1 && filterPos != spawn.pos()){
                    continue;
                }

                cons.accept(spawn.worldx(), spawn.worldy(), true);
            }
        }

        if(runtime.state.rules.wavesSpawnAtCores
        && runtime.state.rules.attackMode
        && runtime.state.teams.isActive(runtime.state.rules.waveTeam)
        && !runtime.state.teams.playerCores().isEmpty()){
            Building firstCore = runtime.state.teams.playerCores().first();

            for(CoreBuild core : runtime.state.rules.waveTeam.cores()){
                if(filterPos != -1 && filterPos != core.pos()){
                    continue;
                }

                if(core.commandPos != null){
                    cons.accept(core.commandPos.x, core.commandPos.y, false);
                }else{
                    boolean valid = false;

                    Tmp.v1.set(firstCore).sub(core).limit(coreMargin + core.block.size * tilesize / 2f * Mathf.sqrt2);

                    int steps = 0;

                    while(steps++ < maxSteps){
                        int tx = World.toTile(core.x + Tmp.v1.x);
                        int ty = World.toTile(core.y + Tmp.v1.y);
                        any = false;

                        Geometry.circle(tx, ty, runtime.world.width(), runtime.world.height(), 3, (x, y) -> {
                            if(runtime.world.solid(x, y)){
                                any = true;
                            }
                        });

                        if(!any){
                            valid = true;
                            break;
                        }

                        Tmp.v1.setLength(Tmp.v1.len() + tilesize * 1.1f);
                    }

                    if(valid){
                        cons.accept(core.x + Tmp.v1.x, core.y + Tmp.v1.y, false);
                    }
                }
            }
        }
    }

    private void eachFlyerSpawn(int filterPos, Floatc2 cons){
        for(Tile tile : spawns){
            if(filterPos != -1 && filterPos != tile.pos()){
                continue;
            }

            if(!runtime.state.rules.airUseSpawns){
                float angle = Angles.angle(runtime.world.width() / 2f, runtime.world.height() / 2f, tile.x, tile.y);
                float trns = Math.max(runtime.world.width(), runtime.world.height()) * Mathf.sqrt2 * tilesize;
                float spawnX = Mathf.clamp(runtime.world.width() * tilesize / 2f + Angles.trnsx(angle, trns), -margin, runtime.world.width() * tilesize + margin);
                float spawnY = Mathf.clamp(runtime.world.height() * tilesize / 2f + Angles.trnsy(angle, trns), -margin, runtime.world.height() * tilesize + margin);
                cons.get(spawnX, spawnY);
            }else{
                cons.get(tile.worldx(), tile.worldy());
            }
        }

        if(runtime.state.rules.wavesSpawnAtCores && runtime.state.rules.attackMode && runtime.state.teams.isActive(runtime.state.rules.waveTeam)){
            for(Building core : runtime.state.rules.waveTeam.data().cores){
                if(filterPos != -1 && filterPos != core.pos()){
                    continue;
                }

                cons.get(core.x, core.y);
            }
        }
    }

    private interface SpawnConsumer{
        void accept(float x, float y, boolean shockwave);
    }
}
