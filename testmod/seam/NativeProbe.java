package seam;

import arc.*;
import arc.func.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

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
                    default -> throw new IllegalArgumentException("Unknown native probe: " + mode);
                }
                Core.app.exit();
            }catch(Throwable error){
                Log.err(error);
                System.exit(1);
            }
        });
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
            Events.fire(new TileChangeEvent());
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
