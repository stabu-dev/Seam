package seam;

import arc.*;
import arc.func.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.core.GameState.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.Weather.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock.*;

/** Runs production Context bytecode in a stock application's initialized environment. */
public class NativeProbe extends Seam{
    @Override
    public void init(){
        super.init();
        Core.app.post(() -> {
            try{
                String mode = System.getProperty("seam.probe", "context");
                switch(mode){
                    case "restore-failure", "capture-restore-failure" -> {
                        verifyFailedRestore(mode.equals("capture-restore-failure"));
                        Log.info("seam-restore-probe: passed");
                    }
                    case "context" -> {
                        verifyThreadBinding();
                        verify();
                        Log.info("seam-context-probe: passed");
                    }
                    case "nested-restore-failure" -> {
                        verifyNestedRestore();
                        Log.info("seam-nested-restore-probe: passed");
                    }
                    case "boundary-failure" -> {
                        verifyCaughtFailures();
                        Log.info("seam-boundary-probe: passed");
                    }
                    case "simulation" -> {
                        verifySimulation();
                        return;
                    }
                    default -> throw new IllegalArgumentException("Unknown native probe: " + mode);
                }
                Core.app.exit();
            }catch(Throwable error){
                Log.err(error);
                System.exit(1);
            }
        });
    }

    private void verifySimulation(){
        check(Vars.logic != null && !Vars.net.active(), "Expected an inactive official server.");
        generate(50, 50, false);
        Vars.logic.play();
        Context a = new Context(), b = new Context();
        World first = a.get(() -> Vars.world);
        int[] delayed = {0}, updates = {0};
        a.run(() -> {
            generate(30, 30, true);
            Vars.logic.play();
            Time.run(60f, () -> delayed[0]++);
            Events.run(Trigger.update, () -> {
                if(updates[0]++ != 30) return;
                try{
                    a.update(1f);
                    throw new AssertionError("A world updated itself.");
                }catch(IllegalStateException expected){
                }
                b.update(0f);
                check(Context.current() == a && Vars.world == first, "A nested update did not restore its caller.");
            });
        });
        b.run(() -> {
            generate(60, 40, false);
            Vars.logic.play();
        });
        double[] paused = new double[2];
        Core.app.addListener(new ApplicationListener(){
            int frames;

            @Override
            public void update(){
                try{
                    a.update(Time.delta);
                    b.update(Time.delta);
                    if(++frames == 120){
                        checkCrafting();
                        a.run(NativeProbe.this::checkCrafting);
                        b.run(() -> {
                            checkCrafting();
                            Vars.state.set(State.paused);
                            paused[0] = Time.time;
                            paused[1] = Vars.state.tick;
                        });
                    }else if(frames == 240){
                        checkWorld(50, false);
                        a.run(() -> {
                            checkWorld(30, true);
                            check(delayed[0] == 1 && Time.time > paused[0] + 60f, "The world clock did not run its own delayed task.");
                        });
                        b.run(() -> {
                            checkWorld(60, false);
                            check(Time.time == paused[0] && Vars.state.tick == paused[1], "A paused world advanced.");
                        });
                        check(Context.current() == null, "Simulation did not return to the host.");
                        Log.info("seam-simulation-probe: passed");
                        Core.app.exit();
                    }
                }catch(Throwable error){
                    Log.err(error);
                    System.exit(1);
                }
            }
        });
    }

    private void generate(int width, int height, boolean waves){
        Rules rules = new Rules();
        rules.waves = waves;
        rules.initialWaveSpacing = 30f;
        rules.waveSpacing = 60f;
        rules.dropZoneRadius = 8f;
        if(waves){
            rules.spawns.add(new SpawnGroup(UnitTypes.flare));
            rules.weather.add(new WeatherEntry(Weathers.rain){{
                always = true;
            }});
        }
        Vars.state.rules = rules;
        Vars.world.loadGenerator(width, height, tiles -> {
            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    tiles.set(x, y, new Tile(x, y, Blocks.stone, waves && x == width - 3 && y == height - 3 ? Blocks.spawn : Blocks.air, Blocks.air));
                }
            }
        });
        Vars.world.tile(3, 3).setBlock(Blocks.coreShard, Team.sharded);
        Vars.world.tile(8, 3).setBlock(Blocks.graphitePress, Team.sharded);
        Vars.world.build(8, 3).items.add(Items.coal, 10);
        Vars.world.tile(12, 3).setBlock(Blocks.worldProcessor, Team.sharded);
        ((LogicBuild)Vars.world.build(12, 3)).updateCode("jump 2 notEqual @mapw " + width + "\nsetflag \"seam\" true\nend");
    }

    private void checkCrafting(){
        check(Vars.world.build(8, 3).items.get(Items.graphite) > 0, "Native crafting did not run in " + Vars.world.width() + "-tile world.");
    }

    private void checkWorld(int width, boolean waves){
        check(Vars.world.width() == width, "Unexpected world.");
        check(Vars.state.rules.objectiveFlags.contains("seam"), "A world processor did not read its own world.");
        check(waves == Vars.state.wave > 2, "Waves crossed worlds.");
        check(waves == Groups.unit.contains(u -> u.type == UnitTypes.flare), "Wave units crossed worlds.");
        check(Groups.weather.size() == (waves ? 1 : 0), "Weather crossed worlds.");
    }

    private void verifyThreadBinding() throws InterruptedException{
        Throwable[] failure = {null};
        Thread worker = new Thread(() -> {
            try{
                new Context();
                failure[0] = new AssertionError("A worker created the first context.");
            }catch(Throwable error){
                failure[0] = error;
            }
        });
        worker.start();
        worker.join(5000);
        check(!worker.isAlive() && failure[0] instanceof IllegalStateException, "First worker access did not respect the native application thread.");
    }

    private void verifyFailedRestore(boolean failCapture){
        RestoreKey key = new RestoreKey(), localKey = new RestoreKey();
        Events.run(key, () -> {});
        Context child = new Context(), other = new Context();
        RuntimeException first = new RuntimeException(failCapture ? "capture" : "task"), restore = new RuntimeException("restore");
        try{
            child.run(() -> {
                key.failure = restore;
                if(failCapture){
                    Events.run(localKey, () -> {});
                    localKey.failure = first;
                }else{
                    throw first;
                }
            });
            throw new AssertionError("Restoration failure was ignored.");
        }catch(RuntimeException actual){
            check(actual == first && actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == restore, "Restoration failure replaced the original exception.");
        }
        key.failure = null;
        try{
            other.run(() -> {});
            throw new AssertionError("Context entry continued after failed host restoration.");
        }catch(IllegalStateException expected){
            check(expected.getMessage().contains("restoration"), "Unexpected rejection after failed restoration.");
        }
    }

    private void verifyNestedRestore(){
        World host = Vars.world;
        for(boolean once : new boolean[]{false, true}){
            Context a = new Context(), b = new Context();
            RestoreKey key = new RestoreKey();
            a.run(() -> Events.run(key, () -> {}));
            RuntimeException failure = new RuntimeException("nested restore");
            try{
                a.run(() -> b.run(() -> {
                    key.once = once;
                    key.failure = failure;
                }));
                throw new AssertionError("Nested restoration failure was ignored.");
            }catch(RuntimeException actual){
                check(actual == failure, "Nested restoration replaced the original exception.");
            }
            key.failure = null;
            try{
                a.run(() -> {});
                throw new AssertionError("A partially restored ancestor remained usable.");
            }catch(IllegalStateException expected){
                check(expected.getMessage().contains("invalid"), "Unexpected invalid-ancestor rejection.");
            }
            b.run(() -> check(Context.current() == b, "Healthy context was rejected after host recovery."));
            check(Context.current() == null && Vars.world == host, "Nested failure did not restore the host.");
        }
    }

    private void verifyCaughtFailures(){
        GameState host = Vars.state;
        for(boolean exposed : new boolean[]{false, true}){
            Context a = new Context(), b = new Context(), healthy = new Context();
            GameState first = a.get(() -> Vars.state), second = b.get(() -> Vars.state);
            RestoreKey key = new RestoreKey();
            a.run(() -> Events.run(key, () -> {}));
            RuntimeException failure = new RuntimeException("nested failure");
            try{
                a.get(() -> {
                    try{
                        b.run(() -> {
                            if(exposed){
                                try{
                                    a.run(() -> key.failure = failure);
                                    throw new AssertionError("Invalid capture succeeded.");
                                }catch(RuntimeException actual){
                                    check(actual == failure, "Capture error was replaced.");
                                }
                            }else{
                                key.failure = failure;
                            }
                        });
                        throw new AssertionError("Failed restoration succeeded.");
                    }catch(RuntimeException actual){
                        check(exposed ? actual instanceof IllegalStateException : actual == failure, "Unexpected restoration error.");
                        check(Context.current() == a && Vars.state == (exposed ? second : first), "Unexpected live state after restoration failure.");
                        Vars.state.wave = 99;
                    }
                    return "invalid success";
                });
                throw new AssertionError("Invalid outer boundary returned success.");
            }catch(IllegalStateException expected){
                check(expected.getMessage().contains("invalid"), "Unexpected boundary rejection.");
            }
            key.failure = null;
            rejectInvalid(a);
            if(exposed){
                rejectInvalid(b);
            }else{
                b.run(() -> check(Vars.state.wave != 99, "Unexposed neighbor was changed."));
            }
            healthy.run(() -> check(Context.current() == healthy, "Healthy context was rejected."));
            check(Context.current() == null && Vars.state == host, "Host did not recover from a caught failure.");
        }
    }

    private void rejectInvalid(Context context){
        try{
            context.run(() -> {});
            throw new AssertionError("Invalid context remained usable.");
        }catch(IllegalStateException expected){
            check(expected.getMessage().contains("invalid"), "Unexpected context rejection.");
        }
    }

    private void verifyEntryCapture(){
        World host = Vars.world;
        Context a = new Context(), b = new Context();
        RestoreKey key = new RestoreKey();
        a.run(() -> Events.run(key, () -> {}));
        RuntimeException failure = new RuntimeException("entry capture");
        a.run(() -> {
            World first = Vars.world;
            EntityGroup<?> units = Groups.unit;
            key.once = true;
            key.failure = failure;
            try{
                b.run(() -> { throw new AssertionError("Entered after failed capture."); });
                throw new AssertionError("Entry capture error was ignored.");
            }catch(RuntimeException actual){
                check(actual == failure, "Entry capture error was replaced.");
            }
            check(Context.current() == a && Vars.world == first && Groups.unit == units, "Failed entry changed live data.");
            b.run(() -> check(Context.current() == b, "Entry could not be retried."));
        });
        check(Context.current() == null && Vars.world == host, "Entry-capture failure changed the host.");
    }

    private void verify(){
        check(Vars.headless, "Expected the official headless application.");
        World hostWorld = Vars.world;
        GameState hostState = Vars.state;
        EntityGroup<?> hostGroup = Groups.effect;
        int hostChanges = hostWorld.tileChanges;
        int[] hostEvents = {0};
        Cons<Marker> listener = e -> hostEvents[0]++;
        Events.on(Marker.class, listener);
        Context a = new Context(), b = new Context();
        World first = a.get(() -> Vars.world), second = b.get(() -> Vars.world);
        int firstChanges = first.tileChanges, secondChanges = second.tileChanges;
        a.run(() -> {
            check(Context.current() == a && Vars.state != hostState, "Native data was not scoped.");
            Events.fire(new TileChangeEvent().set(new Tile(0, 0)));
            check(first.tileChanges == firstChanges + 1 && second.tileChanges == secondChanges, "Native World listeners crossed contexts.");
            EffectState effect = EffectState.create();
            effect.data = first;
            effect.add();
            effect.remove();
            b.run(() -> {
                check(Groups.effect != hostGroup && Groups.effect.isEmpty(), "Native entity groups crossed contexts.");
                Groups.updatePooling();
            });
            check(effect.data == first, "Another context processed the native pooling queue.");
            Groups.updatePooling();
            check(effect.data == null, "The owning context did not process its native pooling queue.");
        });
        RuntimeException expected = new RuntimeException("expected nested failure");
        try{
            a.run(() -> b.run(() -> {
                a.run(() -> check(Vars.world == first, "Nested entry did not restore the original world."));
                check(Vars.world == second, "Nested return did not restore the intermediate world.");
                throw expected;
            }));
            throw new AssertionError("Nested scope swallowed the exception.");
        }catch(RuntimeException actual){
            check(actual == expected, "The original exception was replaced.");
        }
        check(Context.current() == null && Vars.world == hostWorld && Vars.state == hostState && Groups.effect == hostGroup, "Host data was not restored.");
        check(hostWorld.tileChanges == hostChanges, "Local construction or tile events changed the host world.");
        Events.fire(new Marker());
        check(hostEvents[0] == 1 && Events.remove(Marker.class, listener), "Host listener registration was not restored.");
        verifyEntryCapture();
    }

    private void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }

    private static class Marker{}

    private static class RestoreKey{
        RuntimeException failure;
        boolean once;

        @Override
        public int hashCode(){
            RuntimeException error = failure;
            if(once) failure = null;
            if(error != null) throw error;
            return 42;
        }
    }
}
