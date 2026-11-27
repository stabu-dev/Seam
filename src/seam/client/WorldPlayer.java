package seam.client;

import arc.struct.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.type.*;
import seam.core.*;

/** Per-world client player and control-group metadata installed in {@link Vars#player} and {@link Vars#control}. */
public final class WorldPlayer{
    private static Binding<Player> players;
    private static Binding<InputState> input;

    private WorldPlayer(){}

    /** Gives {@code world} its own {@link Player} and unit control groups. Host thread only. */
    public static Player attach(Context world){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has a player.");
        if(Vars.control == null || Vars.control.input == null) throw new IllegalStateException("World players need initialized client controls.");
        if(players == null){
            players = new Binding<>(() -> Vars.player, player -> Vars.player = player);
            input = new Binding<>(() -> new InputState(Vars.control.input.controlledType, Vars.control.input.controlGroups), value -> {
                Vars.control.input.controlledType = value.controlled;
                Vars.control.input.controlGroups = value.groups;
            });
        }
        Player player = world.get(Player::create);
        var state = new InputState(null, new IntSeq[Vars.control.input.controlGroups.length]);
        players.own(world, player);
        try{
            input.own(world, state);
        }catch(Throwable error){
            Scopes.cleanup(error, () -> players.inherit(world));
            throw error;
        }
        return player;
    }

    /** @return whether {@code world} has an attached player */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return players != null && players.isOwned(world) && input.isOwned(world);
    }

    private record InputState(UnitType controlled, IntSeq[] groups){}
}
