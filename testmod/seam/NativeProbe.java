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
                verify();
                Log.info("seam-context-probe: passed");
                Core.app.exit();
            }catch(Throwable error){
                Log.err(error);
                System.exit(1);
            }
        });
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
}
