package seam.runtime.services;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;
import seam.runtime.*;

import static mindustry.Vars.*;

public class SeamPathfinder{
    private final SeamRuntime runtime;
    private final IntMap<SeamFlowfield> fields = new IntMap<>();
    private int[] tiles = {};

    public SeamPathfinder(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
    }

    public void rebuild(){
        runtime.requireWorldReady();

        tiles = new int[runtime.world.width() * runtime.world.height()];

        for(int i = 0; i < tiles.length; i++){
            Tile tile = runtime.world.tiles.geti(i);
            tiles[i] = packTile(tile);
        }

        fields.clear();
    }

    public int packTile(Tile tile){
        if(tile == null){
            return 0;
        }

        boolean nearLiquid = false;
        boolean nearSolid = false;
        boolean nearLegSolid = false;
        boolean nearGround = false;
        boolean solid = tile.solid();
        boolean allDeep = tile.floor().isDeep();
        boolean nearDeep = allDeep;

        for(int i = 0; i < 4; i++){
            Tile other = tile.nearby(i);

            if(other == null){
                continue;
            }

            Floor floor = other.floor();
            boolean otherSolid = other.solid();

            if(floor.isLiquid && floor.isDeep()){
                nearLiquid = true;
            }

            if(otherSolid && !other.block().teamPassable){
                nearSolid = true;
            }

            if(!floor.isLiquid){
                nearGround = true;
            }

            if(!floor.isDeep()){
                allDeep = false;
            }else{
                nearDeep = true;
            }

            if(other.legSolid()){
                nearLegSolid = true;
            }
        }

        if(allDeep){
            for(int i = 0; i < 4; i++){
                Tile other = tile.nearby(Geometry.d8edge[i]);

                if(other != null && !other.floor().isDeep()){
                    allDeep = false;
                    break;
                }
            }
        }

        int teamId = tile.getTeamID();

        return PathTile.get(
        tile.build == null || !solid || tile.block() instanceof CoreBlock ? 0 : Math.min((int)(tile.build.health / 40), 80),
        teamId == 0 && tile.build != null && runtime.state.rules.coreCapture ? 255 : teamId,
        solid,
        tile.floor().isLiquid,
        tile.legSolid(),
        nearLiquid,
        nearGround,
        nearSolid,
        nearLegSolid,
        tile.floor().isDeep(),
        tile.floor().damages(),
        allDeep,
        nearDeep,
        tile.block().teamPassable
        );
    }

    public int get(int x, int y){
        if(x < 0 || y < 0 || x >= runtime.world.width() || y >= runtime.world.height()){
            return 0;
        }

        int index = x + y * runtime.world.width();
        return index >= 0 && index < tiles.length ? tiles[index] : 0;
    }

    public void updateTile(Tile tile){
        if(tile == null){
            return;
        }

        tile.getLinkedTiles(linked -> {
            int index = linked.x + linked.y * runtime.world.width();

            if(index >= 0 && index < tiles.length){
                tiles[index] = packTile(linked);
            }
        });

        fields.clear();
    }

    public Pathfinder.Flowfield getField(Team team, int costType, int fieldType){
        int key = (team == null ? 0 : team.id) | (costType << 8) | (fieldType << 16);
        SeamFlowfield field = fields.get(key);

        if(field == null){
            field = new SeamFlowfield(team == null ? Team.derelict : team, costType, fieldType);
            fields.put(key, field);
        }

        return field;
    }

    public Tile getTargetTile(Tile tile, Pathfinder.Flowfield path){
        return getTargetTile(tile, path, true, 0);
    }

    public Tile getTargetTile(Tile tile, Pathfinder.Flowfield path, boolean diagonals){
        return getTargetTile(tile, path, diagonals, 0);
    }

    public Tile getTargetTile(Tile tile, Pathfinder.Flowfield path, boolean diagonals, int avoidanceId){
        if(tile == null || !runtime.worldReady()){
            return tile;
        }

        Team team = Team.derelict;
        int costType = Pathfinder.costGround;
        int fieldType = Pathfinder.fieldCore;

        if(path instanceof SeamFlowfield field){
            team = field.team();
            costType = field.costType();
            fieldType = field.fieldType();
        }

        Tile target = targetFor(team, fieldType, tile);

        if(target == null){
            return tile;
        }

        Tile next = bestStep(tile, target, team, costType, diagonals);

        return next == null ? tile : next;
    }

    public Tile bestStep(Tile from, Tile target, Team team, int costType, boolean diagonals){
        if(from == null || target == null){
            return from;
        }

        Tile best = from;
        float bestDst = Mathf.dst2(from.x, from.y, target.x, target.y);
        Point2[] points = diagonals ? Geometry.d8 : Geometry.d4;

        for(Point2 point : points){
            Tile other = runtime.world.tile(from.x + point.x, from.y + point.y);

            if(other == null || !passable(other, team, costType)){
                continue;
            }

            if(point.x != 0 && point.y != 0){
                Tile horizontal = runtime.world.tile(from.x + point.x, from.y);
                Tile vertical = runtime.world.tile(from.x, from.y + point.y);

                if((horizontal != null && !passable(horizontal, team, costType))
                || (vertical != null && !passable(vertical, team, costType))){
                    continue;
                }
            }

            float dst = Mathf.dst2(other.x, other.y, target.x, target.y);

            if(dst < bestDst){
                best = other;
                bestDst = dst;
            }
        }

        return best;
    }

    public boolean passable(Tile tile, Team team, int costType){
        if(tile == null){
            return false;
        }

        int teamId = team == null ? Team.derelict.id : team.id;
        int costId = Mathf.clamp(costType, 0, Pathfinder.costTypes.size - 1);
        int cost = Pathfinder.costTypes.get(costId).getCost(teamId, packTile(tile));

        return cost >= 0 && !(costId == Pathfinder.costNaval && cost >= 6000);
    }

    private Tile targetFor(Team team, int fieldType, Tile from){
        if(fieldType != Pathfinder.fieldCore){
            return null;
        }

        Building closest = null;
        float closestDst = 0f;

        for(Building build : runtime.index.getEnemy(team, BlockFlag.core)){
            if(build == null || build.tile == null){
                continue;
            }

            float dst = build.dst2(from.worldx(), from.worldy());

            if(closest == null || dst < closestDst){
                closest = build;
                closestDst = dst;
            }
        }

        if(closest != null){
            return closest.tile;
        }

        if(runtime.state.rules.waves && team == runtime.state.rules.defaultTeam && runtime.waveSpawner != null){
            Tile spawn = null;
            float spawnDst = 0f;

            for(Tile tile : runtime.waveSpawner.getSpawns()){
                float dst = Mathf.dst2(from.worldx(), from.worldy(), tile.worldx(), tile.worldy());

                if(spawn == null || dst < spawnDst){
                    spawn = tile;
                    spawnDst = dst;
                }
            }

            return spawn;
        }

        return null;
    }

    private static class SeamFlowfield extends Pathfinder.Flowfield{
        private final Team team;
        private final int costType;
        private final int fieldType;

        SeamFlowfield(Team team, int costType, int fieldType){
            super();
            this.team = team;
            this.costType = costType;
            this.fieldType = fieldType;
        }

        Team team(){
            return team;
        }

        int costType(){
            return costType;
        }

        int fieldType(){
            return fieldType;
        }

        protected void getPositions(IntSeq out){
        }
    }
}
