package seam.runtime.services;

import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.Units.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;
import seam.runtime.*;

import static mindustry.Vars.*;

public class SeamBlockIndexer extends BlockIndexer{
    private static final int quadrantSize = 20;
    private final Rect rect = new Rect();

    private final SeamRuntime runtime;

    private int quadWidth;
    private int quadHeight;

    private IntSeq[][][] ores = {};
    private IntSeq[][][] wallOres = {};
    private Seq<Building>[] damagedTiles = new Seq[Team.all.length];
    private final Seq<Item> allPresentOres = new Seq<>();
    private final ObjectIntMap<Item> allOres = new ObjectIntMap<>();
    private final ObjectIntMap<Item> allWallOres = new ObjectIntMap<>();
    private Seq<Team> activeTeams = new Seq<>(Team.class);
    private Seq<Building>[][] flagMap = new Seq[Team.all.length][BlockFlag.all.length];
    private boolean[] blocksPresent = {};
    private final Seq<Building> returnBuildings = new Seq<>(Building.class);
    private Seq<Tile>[] floorMap = new Seq[BlockFlag.all.length];

    public SeamBlockIndexer(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
        clearFlags();
    }

    public SeamRuntime runtime(){
        return runtime;
    }

    public void rebuild(){
        runtime.requireWorldReady();

        damagedTiles = new Seq[Team.all.length];
        flagMap = new Seq[Team.all.length][BlockFlag.all.length];
        floorMap = new Seq[BlockFlag.all.length];
        activeTeams = new Seq<>(Team.class);

        clearFlags();

        allOres.clear();
        allWallOres.clear();
        allPresentOres.clear();

        int itemCount = content == null ? 0 : content.items().size;
        int blockCount = content == null ? 0 : content.blocks().size;

        ores = new IntSeq[itemCount][][];
        wallOres = new IntSeq[itemCount][][];
        quadWidth = Mathf.ceil(runtime.world.width() / (float)quadrantSize);
        quadHeight = Mathf.ceil(runtime.world.height() / (float)quadrantSize);
        blocksPresent = new boolean[blockCount];

        for(Team team : Team.all){
            TeamData data = runtime.state.teams.get(team);

            if(data != null){
                data.buildings.clear();
                data.buildingTypes.clear();
                data.unitCap = 0;

                if(data.buildingTree != null){
                    data.buildingTree.clear();
                }

                if(data.turretTree != null){
                    data.turretTree.clear();
                }
            }
        }

        for(Tile tile : runtime.world.tiles){
            if(tile == null){
                continue;
            }

            process(tile);
            addFloorIndex(tile, tile.floor());
            indexOre(tile);
        }

        updatePresentOres();
    }

    public Seq<Item> getAllPresentOres(){
        return allPresentOres;
    }

    public void removeIndex(Tile tile){
        if(!owns(tile)){
            return;
        }

        Team team = tile.team();

        if(tile.build != null && tile.isCenter()){
            Building build = tile.build;
            Block block = tile.block();
            TeamData data = runtime.state.teams.get(team);

            if(data == null){
                return;
            }

            if(block.flags.size > 0){
                for(BlockFlag flag : block.flags.array){
                    getFlagged(team)[flag.ordinal()].remove(build);
                }
            }

            removeIndexed(data.buildings, build, build.indexerBuildIndex, b -> b.indexerBuildIndex = (short)data.buildings.indexOf(b));

            Seq<Building> targetTypes = data.buildingTypes.get(block);
            if(targetTypes != null){
                removeIndexed(targetTypes, build, build.indexerBuildTypeIndex, b -> b.indexerBuildTypeIndex = (short)targetTypes.indexOf(b));
            }

            data.unitCap -= block.unitCapModifier;

            if(data.buildingTree != null){
                data.buildingTree.remove(build);
            }

            if(data.turretTree != null && block.attacks){
                data.turretTree.remove(build);
            }

            if(build.wasDamaged && damagedTiles[team.id] != null){
                damagedTiles[team.id].remove(build);
            }

            build.wasDamaged = false;
        }

        tile.getLinkedTiles(this::unindexOre);
    }

    public void addIndex(Tile base){
        if(!owns(base)){
            return;
        }

        process(base);
        base.getLinkedTiles(this::indexOre);
        updatePresentOres();
    }

    public boolean isBlockPresent(Block block){
        return block != null && block.id >= 0 && block.id < blocksPresent.length && blocksPresent[block.id];
    }

    public Seq<Tile> getFlaggedFloors(BlockFlag flag){
        int index = flag.ordinal();

        if(floorMap[index] == null){
            floorMap[index] = new Seq<>(false);
        }

        return floorMap[index];
    }

    public boolean hasOre(Item item){
        return item != null && allOres.get(item) > 0;
    }

    public boolean hasWallOre(Item item){
        return item != null && allWallOres.get(item) > 0;
    }

    public Seq<Building> getDamaged(Team team){
        if(team == null){
            return returnBuildings.clear();
        }

        if(damagedTiles[team.id] == null){
            damagedTiles[team.id] = new Seq<>(false);
        }

        Seq<Building> tiles = damagedTiles[team.id];
        tiles.removeAll(build -> build == null || !build.damaged());

        return tiles;
    }

    public Seq<Building> getFlagged(Team team, BlockFlag type){
        return team == null || type == null ? returnBuildings.clear() : flagMap[team.id][type.ordinal()];
    }

    public Building findClosestFlag(float x, float y, Team team, BlockFlag flag){
        return Geometry.findClosest(x, y, getFlagged(team, flag));
    }

    public boolean eachBlock(Teamc team, float range, Boolf<Building> pred, Cons<Building> cons){
        return team != null && eachBlock(team.team(), team.getX(), team.getY(), range, pred, cons);
    }

    public boolean eachBlock(Team team, float wx, float wy, float range, Boolf<Building> pred, Cons<Building> cons){
        if(pred == null || cons == null){
            return false;
        }

        returnBuildings.clear();

        if(team == null){
            allBuildings(wx, wy, range, build -> {
                if(pred.get(build) && !build.block.privileged){
                    returnBuildings.add(build);
                }
            });
        }else{
            TeamData data = runtime.state.teams.get(team);
            QuadTree<Building> buildings = data.buildingTree;

            if(buildings == null){
                return false;
            }

            buildings.intersect(wx - range, wy - range, range * 2f, range * 2f, build -> {
                if(build.within(wx, wy, range + build.hitSize() / 2f) && pred.get(build) && !build.block.privileged){
                    returnBuildings.add(build);
                }
            });
        }

        int size = returnBuildings.size;

        for(int i = 0; i < size; i++){
            cons.get(returnBuildings.items[i]);
        }

        returnBuildings.clear();
        return size > 0;
    }

    public boolean eachBlock(Team team, Rect rect, Boolf<Building> pred, Cons<Building> cons){
        if(team == null || rect == null || pred == null || cons == null){
            return false;
        }

        TeamData data = runtime.state.teams.get(team);
        QuadTree<Building> buildings = data.buildingTree;

        if(buildings == null){
            return false;
        }

        returnBuildings.clear();

        buildings.intersect(rect, build -> {
            if(pred.get(build) && !build.block.privileged){
                returnBuildings.add(build);
            }
        });

        int size = returnBuildings.size;

        for(int i = 0; i < size; i++){
            cons.get(returnBuildings.items[i]);
        }

        returnBuildings.clear();
        return size > 0;
    }

    public Seq<Building> getEnemy(Team team, BlockFlag type){
        returnBuildings.clear();

        if(team == null || type == null){
            return returnBuildings;
        }

        Seq<TeamData> present = runtime.state.teams.present;

        if(present.isEmpty()){
            for(Team enemy : Team.all){
                if(enemy == team || (enemy == Team.derelict && !runtime.state.rules.coreCapture)){
                    continue;
                }

                returnBuildings.addAll(getFlagged(enemy)[type.ordinal()]);
            }
        }else{
            for(int i = 0; i < present.size; i++){
                Team enemy = present.items[i].team;

                if(enemy == team || (enemy == Team.derelict && !runtime.state.rules.coreCapture)){
                    continue;
                }

                returnBuildings.addAll(getFlagged(enemy)[type.ordinal()]);
            }
        }

        return returnBuildings;
    }

    public void notifyHealthChanged(Building build){
        if(build == null || build.team == null){
            return;
        }

        boolean damaged = build.damaged();

        if(build.wasDamaged != damaged){
            if(damagedTiles[build.team.id] == null){
                damagedTiles[build.team.id] = new Seq<>(false);
            }

            if(damaged){
                damagedTiles[build.team.id].addUnique(build);
            }else{
                damagedTiles[build.team.id].remove(build);
            }

            build.wasDamaged = damaged;
        }
    }

    public void allBuildings(float x, float y, float range, Cons<Building> cons){
        if(cons == null){
            return;
        }

        returnBuildings.clear();

        for(int i = 0; i < activeTeams.size; i++){
            Team team = activeTeams.items[i];
            TeamData data = runtime.state.teams.get(team);
            QuadTree<Building> buildings = data.buildingTree;

            if(buildings != null){
                buildings.intersect(x - range, y - range, range * 2f, range * 2f, returnBuildings);
            }
        }

        int size = returnBuildings.size;

        for(int i = 0; i < size; i++){
            Building build = returnBuildings.items[i];

            if(build != null && build.within(x, y, range + build.hitSize() / 2f)){
                cons.get(build);
            }
        }

        returnBuildings.clear();
    }

    public Building findEnemyTile(Team team, float x, float y, float range, BuildingPriorityf priority, Boolf<Building> pred){
        return findEnemyTile(team, x, y, range, priority, pred, null);
    }

    public Building findEnemyTile(Team team, float x, float y, float range, BuildingPriorityf priority, Boolf<Building> pred, Team source){
        if(team == null || priority == null || pred == null){
            return null;
        }

        Building target = null;
        float targetDist = 0f;

        for(int i = 0; i < activeTeams.size; i++){
            Team enemy = activeTeams.items[i];

            if(enemy == team || (enemy == Team.derelict && !runtime.state.rules.coreCapture)){
                continue;
            }

            Building candidate = findTile(enemy, x, y, range, build -> pred.get(build) && build.isDiscovered(team), true, source);

            if(candidate == null){
                continue;
            }

            float dist = candidate.dst(x, y) - candidate.hitSize() / 2f;

            if(target == null
            || (dist < targetDist && priority.priority(candidate) >= priority.priority(target))
            || priority.priority(candidate) > priority.priority(target)){
                target = candidate;
                targetDist = dist;
            }
        }

        return target;
    }

    public Building findEnemyTile(Team team, float x, float y, float range, Boolf<Building> pred){
        return findEnemyTile(team, x, y, range, UnitSorts.buildingDefault, pred);
    }

    public Building findTile(Team team, float x, float y, float range, Boolf<Building> pred){
        return findTile(team, x, y, range, pred, false);
    }

    public Building findTile(Team team, float x, float y, float range, Boolf<Building> pred, boolean usePriority){
        return findTile(team, x, y, range, pred, usePriority, null);
    }

    public Building findTile(Team team, float x, float y, float range, Boolf<Building> pred, boolean usePriority, Team source){
        if(team == null || pred == null){
            return null;
        }

        TeamData data = runtime.state.teams.get(team);
        QuadTree<Building> buildings = data.buildingTree;

        if(buildings == null){
            return null;
        }

        Building closest = null;
        float dst = 0f;

        returnBuildings.clear();
        buildings.intersect(rect.setCentered(x, y, range * 2f), returnBuildings);

        for(int i = 0; i < returnBuildings.size; i++){
            Building next = returnBuildings.items[i];

            if(next == null || !pred.get(next) || (next.team != source && !next.block.targetable)){
                continue;
            }

            float nextDst = next.dst(x, y) - next.hitSize() / 2f;

            if(nextDst < range && (closest == null
            || (nextDst < dst && (!usePriority || closest.block.priority <= next.block.priority))
            || (usePriority && closest.block.priority < next.block.priority))){
                dst = nextDst;
                closest = next;
            }
        }

        returnBuildings.clear();
        return closest;
    }

    public Tile findClosestOre(float xp, float yp, Item item){
        if(item == null || item.id < 0 || item.id >= ores.length || ores[item.id] == null){
            return null;
        }

        return findClosestOreIn(ores[item.id], xp, yp, true);
    }

    public Tile findClosestWallOre(float xp, float yp, Item item){
        if(item == null || item.id < 0 || item.id >= wallOres.length || wallOres[item.id] == null){
            return null;
        }

        return findClosestOreIn(wallOres[item.id], xp, yp, false);
    }

    public Tile findClosestOre(Unit unit, Item item){
        return unit == null ? null : findClosestOre(unit.x, unit.y, item);
    }

    public Tile findClosestWallOre(Unit unit, Item item){
        return unit == null ? null : findClosestWallOre(unit.x, unit.y, item);
    }

    public void removeFloorIndex(Tile tile, Floor floor){
        if(floor == null || floor.flags.size == 0 || floorMap == null){
            return;
        }

        for(BlockFlag flag : floor.flags.array){
            getFlaggedFloors(flag).remove(tile);
        }
    }

    public void addFloorIndex(Tile tile, Floor floor){
        if(tile == null || floor == null || floor.flags.size == 0 || !floor.shouldIndex(tile) || floorMap == null){
            return;
        }

        for(BlockFlag flag : floor.flags.array){
            getFlaggedFloors(flag).addUnique(tile);
        }
    }

    private Tile findClosestOreIn(IntSeq[][] quadrants, float xp, float yp, boolean floor){
        float minDst = 0f;
        Tile closest = null;

        for(int qx = 0; qx < quadWidth; qx++){
            for(int qy = 0; qy < quadHeight; qy++){
                IntSeq positions = quadrants[qx][qy];

                if(positions == null || positions.size <= 0){
                    continue;
                }

                for(int i = 0; i < positions.size; i++){
                    Tile tile = runtime.world.tile(positions.items[i]);

                    if(tile == null || (floor && tile.block() != Blocks.air) || (!floor && tile.block() == Blocks.air)){
                        continue;
                    }

                    float dst = Mathf.dst2(xp, yp, tile.worldx(), tile.worldy());

                    if(closest == null || dst < minDst){
                        closest = tile;
                        minDst = dst;
                    }
                }
            }
        }

        return closest;
    }

    private void process(Tile tile){
        if(tile == null){
            return;
        }

        Team team = tile.team();

        if(tile.isCenter() && tile.build != null){
            Building build = tile.build;
            Block block = tile.block();
            TeamData data = runtime.state.teams.get(team);

            if(block.flags.size > 0){
                Seq<Building>[] map = getFlagged(team);

                for(BlockFlag flag : block.flags.array){
                    map[flag.ordinal()].addUnique(build);
                }
            }

            Seq<Building> targetTypes = data.buildingTypes.get(block, () -> new Seq<>(false));

            if(!data.buildings.contains(build)){
                data.buildings.add(build);
            }

            if(!targetTypes.contains(build)){
                targetTypes.add(build);
            }

            build.indexerBuildIndex = (short)data.buildings.indexOf(build);
            build.indexerBuildTypeIndex = (short)targetTypes.indexOf(build);
            data.unitCap += block.unitCapModifier;

            if(!activeTeams.contains(team)){
                activeTeams.add(team);
            }

            if(data.buildingTree == null){
                data.buildingTree = new QuadTree<>(new Rect(0f, 0f, runtime.world.unitWidth(), runtime.world.unitHeight()));
            }

            data.buildingTree.insert(build);

            if(block.attacks && build instanceof Ranged){
                if(data.turretTree == null){
                    data.turretTree = new TurretTree(new Rect(0f, 0f, runtime.world.unitWidth(), runtime.world.unitHeight()));
                }

                data.turretTree.insert(build);
            }

            notifyHealthChanged(build);
        }

        if(blocksPresent != null && blocksPresent.length > 0){
            if(!tile.block().isStatic()){
                setPresent(tile.floorID());
                setPresent(tile.overlayID());
            }

            setPresent(tile.blockID());
        }
    }

    private void setPresent(int blockId){
        if(blockId >= 0 && blockId < blocksPresent.length){
            blocksPresent[blockId] = true;
        }
    }

    private void indexOre(Tile tile){
        if(tile == null || ores.length == 0){
            return;
        }

        Item drop = tile.drop();
        Item wallDrop = tile.wallDrop();
        int qx = tile.x / quadrantSize;
        int qy = tile.y / quadrantSize;
        int pos = tile.pos();

        if(tile.block() == Blocks.air){
            if(drop != null){
                addOre(ores, allOres, drop, qx, qy, pos);
            }

            if(wallDrop != null){
                removeOre(wallOres, allWallOres, wallDrop, qx, qy, pos);
            }
        }else{
            if(wallDrop != null){
                addOre(wallOres, allWallOres, wallDrop, qx, qy, pos);
            }

            if(drop != null){
                removeOre(ores, allOres, drop, qx, qy, pos);
            }
        }
    }

    private void unindexOre(Tile tile){
        if(tile == null){
            return;
        }

        Item drop = tile.drop();
        Item wallDrop = tile.wallDrop();
        int qx = tile.x / quadrantSize;
        int qy = tile.y / quadrantSize;
        int pos = tile.pos();

        if(drop != null){
            removeOre(ores, allOres, drop, qx, qy, pos);
        }

        if(wallDrop != null){
            removeOre(wallOres, allWallOres, wallDrop, qx, qy, pos);
        }
    }

    private void addOre(IntSeq[][][] store, ObjectIntMap<Item> counts, Item item, int qx, int qy, int pos){
        if(item.id < 0 || item.id >= store.length || qx < 0 || qy < 0 || qx >= quadWidth || qy >= quadHeight){
            return;
        }

        if(store[item.id] == null){
            store[item.id] = new IntSeq[quadWidth][quadHeight];
        }

        if(store[item.id][qx][qy] == null){
            store[item.id][qx][qy] = new IntSeq(false, 16);
        }

        if(store[item.id][qx][qy].addUnique(pos)){
            counts.increment(item);
        }
    }

    private void removeOre(IntSeq[][][] store, ObjectIntMap<Item> counts, Item item, int qx, int qy, int pos){
        if(item.id < 0 || item.id >= store.length || store[item.id] == null || qx < 0 || qy < 0 || qx >= quadWidth || qy >= quadHeight){
            return;
        }

        IntSeq positions = store[item.id][qx][qy];

        if(positions != null && positions.removeValue(pos)){
            counts.increment(item, -1);
        }
    }

    private void updatePresentOres(){
        allPresentOres.clear();

        if(content == null){
            return;
        }

        for(Item item : content.items()){
            if(hasOre(item) || hasWallOre(item)){
                allPresentOres.add(item);
            }
        }
    }

    private void clearFlags(){
        for(int i = 0; i < flagMap.length; i++){
            for(int j = 0; j < BlockFlag.all.length; j++){
                flagMap[i][j] = new Seq<>();
            }
        }
    }

    private Seq<Building>[] getFlagged(Team team){
        return flagMap[team.id];
    }

    private boolean owns(Tile tile){
        return tile != null
        && runtime.worldReady()
        && tile.x >= 0
        && tile.y >= 0
        && tile.x < runtime.world.width()
        && tile.y < runtime.world.height()
        && runtime.world.tile(tile.x, tile.y) == tile;
    }

    private interface IndexUpdate{
        void update(Building build);
    }

    private void removeIndexed(Seq<Building> buildings, Building build, int packedIndex, IndexUpdate update){
        int index = packedIndex & 0xffff;

        if(index < buildings.size && buildings.get(index) == build){
            buildings.remove(index);
        }else{
            index = buildings.indexOf(build);

            if(index != -1){
                buildings.remove(index);
            }
        }

        if(index >= 0 && index < buildings.size){
            update.update(buildings.get(index));
        }
    }

    private static final class TurretTree extends QuadTree<Building>{
        TurretTree(Rect bounds){
            super(bounds);
        }

        @Override
        public void hitbox(Building build){
            tmp.setCentered(build.x, build.y, ((Ranged)build).range() * 2f);
        }

        @Override
        protected QuadTree<Building> newChild(Rect rect){
            return new TurretTree(rect);
        }
    }
}
