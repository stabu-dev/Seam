package seam.client;

import arc.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.input.*;
import seam.core.*;
import seam.core.Binding;

/** Per-world client player and {@link InputHandler} installed in {@link Vars#player} and {@link Vars#control}. */
public final class WorldPlayer{
    private static Binding<Player> players;
    private static Binding<InputHandler> inputs;

    private WorldPlayer(){}

    /** Gives {@code world} its own {@link Player} and isolated {@link InputHandler}. Host thread only. */
    public static Player attach(Context world){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has a player.");
        if(Vars.control == null || Vars.control.input == null) throw new IllegalStateException("World players need initialized client controls.");
        if(players == null) players = new Binding<>(() -> Vars.player, player -> Vars.player = player);
        boolean createdInput = ensureInput(world);
        try{
            Player player = world.get(Player::create);
            players.own(world, player);
            return player;
        }catch(Throwable error){
            if(createdInput) Scopes.cleanup(error, () -> releaseInput(world));
            throw error;
        }
    }

    /** @return whether {@code world} has an attached player */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return players != null && players.isOwned(world) && inputs != null && inputs.isOwned(world);
    }

    /** Isolates {@link Vars#control}'s {@link InputHandler} for {@code world} if not already owned; returns true when newly created. */
    static boolean ensureInput(Context world){
        if(Vars.control == null || Vars.control.input == null) return false;
        if(inputs == null) inputs = new Binding<>(() -> Vars.control.input, value -> Vars.control.input = value);
        if(inputs.isOwned(world)) return false;
        InputHandler handler = world.get(WorldPlayer::createInput);
        //stay locked until this world's input handler is actually registered as an active Core.input processor
        handler.addLock(() -> Core.input == null || !Core.input.getInputProcessors().contains(handler, true));
        inputs.own(world, handler);
        return true;
    }

    private static InputHandler createInput(){
        if(Vars.mobile){
            return new MobileInput(){
                @Override public Unit selectedUnit(){ return locked() ? null : super.selectedUnit(); }
                @Override public Building selectedControlBuild(){ return locked() ? null : super.selectedControlBuild(); }
            };
        }
        return new DesktopInput(){
            @Override public Unit selectedUnit(){ return locked() ? null : super.selectedUnit(); }
            @Override public Building selectedControlBuild(){ return locked() ? null : super.selectedControlBuild(); }
        };
    }

    static void releaseInput(Context world){
        if(inputs != null) inputs.inherit(world);
    }
}
