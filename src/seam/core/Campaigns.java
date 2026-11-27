package seam.core;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.game.SectorInfo.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.modules.*;
import seam.gen.*;

/** Coordinates shared campaign clocks, multi-sector turns, and same-sector checks. */
final class Campaigns{
    private static final Object hostCampaign = new Object();
    private static final ObjectSet<Context> hostCaptureListeners = new ObjectSet<>();
    private static final ObjectMap<Context, CampaignUniverse> isolatedUniverses = new ObjectMap<>();
    private static @Nullable Boolf<Context> viewCheck;
    private static @Nullable Func<Context, Object> campaignResolver;
    private static @Nullable Cons<Context> campaignGuard;
    private static @Nullable CampaignUniverse hostUniverse;

    private Campaigns(){}

    static void setViewCheck(Boolf<Context> check){
        if(check == null) throw new IllegalArgumentException("View check is required.");
        viewCheck = check;
    }

    static void setCampaignResolver(Func<Context, Object> resolver){
        if(resolver == null) throw new IllegalArgumentException("Campaign resolver is required.");
        campaignResolver = resolver;
    }

    static void setCampaignGuard(Cons<Context> guard){
        if(guard == null) throw new IllegalArgumentException("Campaign guard is required.");
        campaignGuard = guard;
    }

    static Universe newIsolatedUniverse(){
        return new CampaignUniverse(false);
    }

    static Object campaignOf(@Nullable Context world){
        if(world == null || campaignResolver == null) return hostCampaign;
        Object resolved = campaignResolver.get(world);
        return resolved == null ? hostCampaign : resolved;
    }

    static void onDispose(Context world){
        hostCaptureListeners.remove(world);
        if(hostUniverse != null) hostUniverse.onOwnerDisposed(world.id);
        CampaignUniverse isolated = isolatedUniverses.remove(world);
        if(isolated != null) isolated.onOwnerDisposed(world.id);
    }

    static void beforeStep(Context world){
        if(!Vars.state.isGame()){
            throw new IllegalStateException("Campaign ticks require a playing or paused map.");
        }
        if(campaignGuard != null) campaignGuard.get(world);
        Object campaign = campaignOf(world);
        if(campaign == hostCampaign && !Vars.headless && (viewCheck == null || !viewCheck.get(world))){
            throw new IllegalStateException("A displayed campaign needs its own WorldView.");
        }
        Sector sector = Vars.state.rules.sector;
        if(campaign == hostCampaign && isLiveOnSector(Context.stateOf(null), sector)){
            throw new IllegalStateException("Sector " + sector + " is already active on the host in the same campaign. Attach a separate CampaignProfile to run it independently.");
        }
        Context.eachLive(other -> {
            if(other != world && campaignOf(other) == campaign && isLiveOnSector(Context.stateOf(other), sector)){
                throw new IllegalStateException("Sector " + sector + " is already active in another world in the same campaign. Attach a separate CampaignProfile to run it independently.");
            }
        });
        if(campaign == hostCampaign && Vars.universe != null){
            if(!(Vars.universe instanceof CampaignUniverse)){
                Vars.universe = CampaignUniverse.wrapHost(Vars.universe);
                hostCaptureListeners.add(world);
            }else if(hostCaptureListeners.add(world)){
                Events.on(SectorCaptureEvent.class, e -> {
                    if(!Vars.net.client() && Vars.state.isCampaign()){
                        Vars.state.getSector().planet.updateBaseCoverage();
                    }
                });
            }
        }
    }

    private static boolean isLiveOnSector(@Nullable GameState state, Sector sector){
        return state != null && state.isGame() && !state.gameOver && state.rules.sector == sector;
    }

    private static final class CampaignUniverse extends Universe{
        final boolean host;
        private long lastTickFrame = Long.MIN_VALUE;
        private long lastTickOwner = Long.MIN_VALUE;

        CampaignUniverse(boolean host){
            this.host = host;
        }

        static CampaignUniverse wrapHost(Universe original){
            var wrapped = new CampaignUniverse(true);
            Engine.universeSetSeconds(wrapped, Engine.universeSeconds(original));
            Engine.universeSetNetSeconds(wrapped, Engine.universeNetSeconds(original));
            Engine.universeSetSecondCounter(wrapped, Engine.universeSecondCounter(original));
            Engine.universeSetTurn(wrapped, Engine.universeTurn(original));
            Engine.universeSetTurnCounter(wrapped, Engine.universeTurnCounter(original));
            Engine.universeSetLastLoadout(wrapped, Engine.universeLastLoadout(original));
            Engine.universeSetLastLaunchResources(wrapped, Engine.universeLastLaunchResources(original));
            hostUniverse = wrapped;
            return wrapped;
        }

        void onOwnerDisposed(long ownerId){
            if(lastTickOwner == ownerId){
                lastTickFrame = Long.MIN_VALUE;
                lastTickOwner = Long.MIN_VALUE;
            }
        }

        private boolean shouldAdvanceClock(){
            long frame = Core.graphics == null ? Long.MIN_VALUE : Core.graphics.getFrameId();
            Context active = Context.current();
            long owner = Context.id(active);
            if(frame != Long.MIN_VALUE && lastTickFrame == frame && lastTickOwner != owner) return false;
            lastTickFrame = frame;
            lastTickOwner = owner;
            if(!host && active != null) isolatedUniverses.put(active, this);
            return true;
        }

        @Override
        public void update(){
            if(!Vars.net.client() && shouldAdvanceClock()){
                float secondCounter = Engine.universeSecondCounter(this) + Time.delta / 60f;
                float turnCounter = Engine.universeTurnCounter(this) + Time.delta;

                if(turnCounter >= Vars.turnDuration){
                    Engine.universeSetTurnCounter(this, 0f);
                    runTurn();
                }else{
                    Engine.universeSetTurnCounter(this, turnCounter);
                }

                if(secondCounter >= 1f){
                    int seconds = Engine.universeSeconds(this) + (int)secondCounter;
                    Engine.universeSetSeconds(this, seconds);
                    Engine.universeSetSecondCounter(this, secondCounter % 1f);
                    if(seconds % 10 == 1) Engine.universeSave(this);
                }else{
                    Engine.universeSetSecondCounter(this, secondCounter);
                }
            }

            if(Vars.state.hasSector() && Vars.state.getSector().planet.updateLighting && !(Vars.state.getSector().preset != null && Vars.state.getSector().preset.noLighting)){
                var planet = Vars.state.getSector().planet;
                float light = Vars.state.getSector().getLight();
                float alpha = Mathf.clamp(Mathf.map(light, planet.lightSrcFrom, planet.lightSrcTo, planet.lightDstFrom, planet.lightDstTo));
                Vars.state.rules.ambientLight.a = 1f - alpha;
                Vars.state.rules.lighting = !Mathf.equal(alpha, 1f);
            }
        }

        @Override
        public void runTurn(){
            ObjectMap<Sector, LiveSector> liveSiblings = collectSiblings();
            if(liveSiblings.isEmpty()){
                super.runTurn();
                return;
            }

            Engine.universeSetTurn(this, Engine.universeTurn(this) + 1);
            int newSecondsPassed = (int)(Vars.turnDuration / 60);
            Seq<Planet> activePlanets = collectActivePlanets(liveSiblings);

            for(Planet planet : Vars.content.planets()){
                if(!isPlanetActive(planet, activePlanets)) continue;

                if(planet.campaignRules.legacyLaunchPads){
                    for(Sector sector : planet.sectors){
                        if(hasBase(sector, liveSiblings) && !isLive(sector, liveSiblings)){
                            sector.info.lastImported.clear();
                        }
                    }

                    for(Sector sector : planet.sectors){
                        if(hasBase(sector, liveSiblings) && !isLive(sector, liveSiblings) && !isAttacked(sector, liveSiblings)){
                            if(sector.info.destination != null){
                                Sector to = sector.info.destination;
                                if(hasBase(to, liveSiblings) && to.planet == planet){
                                    ItemSeq items = new ItemSeq();
                                    sector.info.export.each((item, stat) -> items.add(item, (int)(stat.mean * newSecondsPassed)));
                                    addItems(to, items, liveSiblings);
                                    to.info.lastImported.add(items);
                                }
                            }
                        }
                    }
                }

                for(Sector sector : planet.sectors){
                    if(!hasBase(sector, liveSiblings)) continue;
                    if(sector.info.importRateCache != null){
                        sector.info.refreshImportRates(planet);
                    }

                    boolean attacked = isAttacked(sector, liveSiblings);
                    if(attacked){
                        sector.info.minutesCaptured = 0;
                    }else{
                        sector.info.minutesCaptured += Vars.turnDuration / 60 / 60;
                    }

                    if(!attacked){
                        boolean live = isLive(sector, liveSiblings);
                        if(!live){
                            sector.info.production.each((item, stat) -> sector.info.items.add(item, Math.min((int)(stat.mean * newSecondsPassed), sector.info.storageCapacity - sector.info.items.get(item))));

                            if(planet.campaignRules.legacyLaunchPads){
                                sector.info.export.each((item, stat) -> {
                                    if(sector.info.items.get(item) <= 0 && sector.info.production.get(item, ExportStat::new).mean < 0 && stat.mean > 0){
                                        stat.mean = Math.min(sector.info.lastImported.get(item) / (float)newSecondsPassed, stat.mean);
                                    }
                                });
                            }

                            sector.info.items.checkNegative();
                            sector.saveInfo();
                        }

                        if(sector.planet.campaignRules.sectorInvasion && sector.info.minutesCaptured > Vars.invasionGracePeriod && sector.info.hasSpawns){
                            int count = sector.near().count(s -> hasEnemyBase(s, liveSiblings) && !hasBase(s, liveSiblings) && (s.preset == null || !s.preset.requireUnlock));
                            if(count > 0 && Mathf.chance(Vars.baseInvasionChance * (0.8f + (count - 1) * 0.3f))){
                                LiveSector sibling = liveSiblings.get(sector);
                                int wave = sibling != null ? sibling.state.wave : sector.isBeingPlayed() ? Vars.state.wave : sector.info.wave;
                                int waveMax = Math.max(sector.info.winWave, wave) + Mathf.random(2, 4) * 5;

                                if(sibling != null){
                                    sibling.state.rules.winWave = waveMax;
                                    sibling.state.rules.waves = true;
                                    sibling.state.rules.attackMode = false;
                                    planet.campaignRules.apply(planet, sibling.state.rules);
                                    notifyInvasion(sibling.owner, sector);
                                }else if(sector.isBeingPlayed()){
                                    Vars.state.rules.winWave = waveMax;
                                    Vars.state.rules.waves = true;
                                    Vars.state.rules.attackMode = false;
                                    planet.campaignRules.apply(planet, Vars.state.rules);
                                    if(Vars.net.server()) Call.setRules(Vars.state.rules);
                                    Events.fire(new SectorInvasionEvent(sector));
                                }else{
                                    sector.info.winWave = waveMax;
                                    sector.info.waves = true;
                                    sector.info.attack = false;
                                    sector.saveInfo();
                                    Events.fire(new SectorInvasionEvent(sector));
                                }
                            }
                        }
                    }
                }
            }

            Events.fire(new TurnEvent());
            Engine.universeSave(this);
        }

        private ObjectMap<Sector, LiveSector> collectSiblings(){
            ObjectMap<Sector, LiveSector> result = new ObjectMap<>();
            Context active = Context.current();
            Object self = host ? hostCampaign : this;
            if(host && active != null){
                addSibling(result, null, Context.stateOf(null));
            }
            Context.eachLive(world -> {
                if(world != active && campaignOf(world) == self){
                    addSibling(result, world, Context.stateOf(world));
                }
            });
            return result;
        }

        private static void addSibling(ObjectMap<Sector, LiveSector> result, @Nullable Context owner, @Nullable GameState state){
            if(state == null || !state.isGame() || state.gameOver || state.rules.sector == null) return;
            result.put(state.rules.sector, new LiveSector(owner, state));
        }

        private static Seq<Planet> collectActivePlanets(ObjectMap<Sector, LiveSector> liveSiblings){
            Seq<Planet> result = new Seq<>();
            result.add(Vars.state.getPlanet());
            for(var entry : liveSiblings){
                Planet planet = entry.key.planet;
                if(!result.contains(planet, true)) result.add(planet);
            }
            return result;
        }

        private static boolean isPlanetActive(Planet planet, Seq<Planet> activePlanets){
            for(int i = 0; i < activePlanets.size; i++){
                Planet current = activePlanets.get(i);
                if(current == null || current == planet || current.updateGroup.contains(planet) || planet.updateGroup.contains(current)){
                    return true;
                }
            }
            return false;
        }

        private static boolean isLive(Sector sector, ObjectMap<Sector, LiveSector> liveSiblings){
            return sector.isBeingPlayed() || liveSiblings.containsKey(sector);
        }

        private static boolean isAttacked(Sector sector, ObjectMap<Sector, LiveSector> liveSiblings){
            LiveSector sibling = liveSiblings.get(sector);
            if(sibling != null) return sibling.state.rules.waves || sibling.state.rules.attackMode;
            return sector.isAttacked();
        }

        private static @Nullable CoreBuild coreOf(GameState state){
            var data = state.teams.get(state.rules.defaultTeam);
            return data == null ? null : data.core();
        }

        private static boolean hasBase(Sector sector, ObjectMap<Sector, LiveSector> liveSiblings){
            LiveSector sibling = liveSiblings.get(sector);
            if(sibling != null){
                return !sibling.state.gameOver && ((sector.save != null && sector.info.hasCore) || coreOf(sibling.state) != null);
            }
            if(sector.isBeingPlayed()){
                return !Vars.state.gameOver && ((sector.save != null && sector.info.hasCore) || coreOf(Vars.state) != null);
            }
            return sector.hasBase();
        }

        private static boolean hasEnemyBase(Sector sector, ObjectMap<Sector, LiveSector> liveSiblings){
            return ((sector.generateEnemyBase && sector.preset == null) || (sector.preset != null && sector.preset.captureWave == 0))
                && (sector.save == null || sector.info.attack || !hasBase(sector, liveSiblings));
        }

        private static void addItems(Sector to, ItemSeq items, ObjectMap<Sector, LiveSector> liveSiblings){
            LiveSector sibling = liveSiblings.get(to);
            if(sibling != null){
                CoreBuild core = coreOf(sibling.state);
                if(core != null){
                    ItemModule storage = core.items;
                    int cap = core.storageCapacity;
                    items.each((item, amount) -> storage.add(item, Math.min(cap - storage.get(item), amount)));
                }
                return;
            }
            to.addItems(items);
        }

        private static void notifyInvasion(@Nullable Context owner, Sector sector){
            Runnable task = () -> {
                if(Vars.state.getSector() != sector) return;
                if(Vars.net.server()) Call.setRules(Vars.state.rules);
                Events.fire(new SectorInvasionEvent(sector));
            };
            if(owner == null){
                if(Core.app != null){
                    Core.app.post(() -> {
                        if(Context.current() == null) task.run();
                    });
                }
            }else if(owner.deferred != null && Core.app != null){
                Core.app.post(owner.deferred.wrap(task));
            }
        }

        private record LiveSector(@Nullable Context owner, GameState state){}
    }
}
