package seam.campaign;

import arc.*;
import arc.func.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Saves.*;
import mindustry.io.*;
import mindustry.type.*;
import seam.client.*;
import seam.core.*;
import seam.gen.*;

/** Per-world or shared vanilla campaign state: universe, sector records, planet rules/positions and research. */
public final class CampaignProfile{
    private static final ObjectMap<Context, Universe> universes = new ObjectMap<>();
    private static final ObjectMap<Context, Cons<?>> captureListeners = new ObjectMap<>();
    private static Records records;
    private static Binding<State> storage;
    private static Binding<Universe> universe;

    static{
        Scopes.setCampaignGuard(CampaignProfile::checkStep);
        Scopes.setCampaignResolver(CampaignProfile::campaignKey);
    }

    private CampaignProfile(){}

    /** @return whether {@code world} has an attached campaign profile */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return storage != null && storage.isOwned(world) && universe.isOwned(world);
    }

    /** Gives {@code world} isolated planet, sector, and research state backed by {@code profile}. Host thread only. */
    public static void attach(Context world, Settings profile){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has a campaign profile.");
        boolean attachedResearch = false, committed = false;
        Throwable failure = null;
        try{
            CampaignResearch.attach(world, profile);
            attachedResearch = true;
            if(records == null){
                records = new Records();
                storage = new Binding<>(records::capture, records::install);
                universe = new Binding<>(() -> Vars.universe, value -> Vars.universe = value);
            }
            storage.own(world, records.empty());
            Universe created = world.get(() -> {
                for(Sector sector : records.sectors) sector.loadInfo();
                for(Planet planet : records.planets){
                    planet.loadRules();
                    planet.loadStats();
                }
                return Scopes.newCampaignUniverse();
            });
            universe.own(world, created);
            universes.put(world, created);
            world.run(() -> Vars.universe.updateGlobal());
            world.own(() -> {
                if(universes.get(world) == created) universes.remove(world);
            });
            committed = true;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!committed){
                universes.remove(world);
                Throwable cleanup = failure;
                if(universe != null) cleanup = Scopes.cleanup(cleanup, () -> universe.inherit(world));
                if(storage != null) cleanup = Scopes.cleanup(cleanup, () -> storage.inherit(world));
                if(attachedResearch) cleanup = Scopes.cleanup(cleanup, () -> CampaignResearch.detach(world));
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    /** Shares {@code source}'s isolated campaign profile with {@code world} (null detaches back to host). Host thread only. */
    public static void share(Context world, @Nullable Context source){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        Scopes.checkUsable(world);
        if(source == null){
            universes.remove(world);
            Cons<?> listener = captureListeners.remove(world);
            if(listener != null){
                world.run(() -> Subscriptions.listeners(SectorCaptureEvent.class).remove(listener, true));
            }
            if(universe != null) universe.inherit(world);
            if(storage != null) storage.inherit(world);
            CampaignResearch.detach(world);
            return;
        }
        Scopes.checkUsable(source);
        if(!attached(source)) throw new IllegalStateException("The source world has no attached campaign profile.");
        if(world == source) return;
        if(attached(world)) throw new IllegalStateException("The world already has a campaign profile.");
        Universe shared = universes.get(source);
        boolean attachedResearch = false, committed = false;
        Cons<?>[] addedListener = new Cons[1];
        Throwable failure = null;
        try{
            CampaignResearch.shareState(world, source);
            attachedResearch = true;
            storage.share(world, source);
            universe.share(world, source);
            world.run(() -> {
                Seq<Cons<?>> bucket = Subscriptions.listeners(SectorCaptureEvent.class);
                int before = bucket.size;
                Events.on(SectorCaptureEvent.class, e -> {
                    if(!Vars.net.client() && Vars.state.isCampaign()){
                        Vars.state.getSector().planet.updateBaseCoverage();
                    }
                });
                if(bucket.size > before) addedListener[0] = bucket.get(before);
                Vars.universe.updateGlobal();
            });
            universes.put(world, shared);
            if(addedListener[0] != null) captureListeners.put(world, addedListener[0]);
            world.own(() -> {
                if(universes.get(world) == shared) universes.remove(world);
                if(addedListener[0] != null && captureListeners.get(world) == addedListener[0]) captureListeners.remove(world);
            });
            committed = true;
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            if(!committed){
                universes.remove(world);
                if(addedListener[0] != null && captureListeners.get(world) == addedListener[0]) captureListeners.remove(world);
                Throwable cleanup = failure;
                if(addedListener[0] != null){
                    cleanup = Scopes.cleanup(cleanup, () -> world.run(() -> Subscriptions.listeners(SectorCaptureEvent.class).remove(addedListener[0], true)));
                }
                if(universe != null) cleanup = Scopes.cleanup(cleanup, () -> universe.inherit(world));
                if(storage != null) cleanup = Scopes.cleanup(cleanup, () -> storage.inherit(world));
                if(attachedResearch) cleanup = Scopes.cleanup(cleanup, () -> CampaignResearch.detach(world));
                if(failure == null && cleanup != null){
                    if(cleanup instanceof RuntimeException error) throw error;
                    if(cleanup instanceof Error error) throw error;
                    throw new RuntimeException(cleanup);
                }
            }
        }
    }

    private static @Nullable Object campaignKey(Context world){
        return universes.get(world);
    }

    private static void checkStep(Context world){
        if(attached(world) && !Vars.headless && (!CampaignSaves.attached(world) || !WorldView.attached(world))){
            throw new IllegalStateException("A displayed campaign needs its own WorldView and native save catalog.");
        }
    }

    private static final class Records{
        final Seq<Sector> sectors = new Seq<>();
        final Seq<Planet> planets = new Seq<>();
        State installed;

        Records(){
            planets.addAll(Vars.content.planets());
            for(Planet planet : planets) sectors.addAll(planet.sectors);
        }

        State empty(){
            var state = snapshot();
            for(int i = 0; i < sectors.size; i++) state.info[i] = new SectorInfo();
            for(int i = 0; i < planets.size; i++){
                state.stats[i] = new CampaignStats();
                state.rules[i] = JsonIO.copy(planets.get(i).campaignRules);
                state.positions[i] = new Vec3();
            }
            return state;
        }

        State snapshot(){
            return new State(new SectorInfo[sectors.size], new SaveSlot[sectors.size], new CampaignStats[planets.size], new CampaignRules[planets.size], new Vec3[planets.size]);
        }

        State capture(){
            if(installed == null) installed = snapshot();
            for(int i = 0; i < sectors.size; i++){
                Sector sector = sectors.get(i);
                installed.info[i] = sector.info;
                installed.save[i] = sector.save;
            }
            for(int i = 0; i < planets.size; i++){
                Planet planet = planets.get(i);
                installed.stats[i] = planet.stats();
                installed.rules[i] = planet.campaignRules;
                installed.positions[i] = planet.position;
            }
            return installed;
        }

        void install(State state){
            for(int i = 0; i < sectors.size; i++){
                Sector sector = sectors.get(i);
                sector.info = state.info[i];
                sector.save = state.save[i];
            }
            for(int i = 0; i < planets.size; i++){
                Planet planet = planets.get(i);
                Engine.planetSetCampaignStats(planet, state.stats[i]);
                planet.campaignRules = state.rules[i];
                planet.position = state.positions[i];
            }
            installed = state;
        }
    }

    private record State(SectorInfo[] info, SaveSlot[] save, CampaignStats[] stats, CampaignRules[] rules, Vec3[] positions){}
}
