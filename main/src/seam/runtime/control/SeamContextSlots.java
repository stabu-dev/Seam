package seam.runtime.control;

import arc.struct.*;
import mindustry.*;
import mindustry.async.*;
import mindustry.gen.*;
import seam.runtime.*;

public final class SeamContextSlots{
    private SeamContextSlots(){
    }

    public static void validateStructure(){
        require(Groups.all != null, "Groups.all is null.");
        require(Groups.player != null, "Groups.player is null.");
        require(Groups.bullet != null, "Groups.bullet is null.");
        require(Groups.unit != null, "Groups.unit is null.");
        require(Groups.build != null, "Groups.build is null.");
        require(Groups.sync != null, "Groups.sync is null.");
        require(Groups.draw != null, "Groups.draw is null.");
        require(Groups.fire != null, "Groups.fire is null.");
        require(Groups.puddle != null, "Groups.puddle is null.");
        require(Groups.weather != null, "Groups.weather is null.");
        require(Groups.label != null, "Groups.label is null.");
        require(Groups.powerGraph != null, "Groups.powerGraph is null.");

        if(Vars.asyncCore != null){
            require(Vars.asyncCore.processes != null, "Vars.asyncCore.processes is null.");
        }
    }

    public static void validateCurrentValues(){
        require(Vars.world != null, "Vars.world is null.");
        require(Vars.state != null, "Vars.state is null.");
        require(Vars.collisions != null, "Vars.collisions is null.");
        require(Vars.waves != null, "Vars.waves is null.");

        validateStructure();
    }

    public static void installRuntime(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        runtime.requireWorldReady();

        Vars.world = runtime.world;
        Vars.state = runtime.state;
        Vars.collisions = runtime.collisions;
        Vars.waves = runtime.waves;
        Vars.player = runtime.player;

        Groups.all = runtime.groups.all;
        Groups.player = runtime.groups.player;
        Groups.bullet = runtime.groups.bullet;
        Groups.unit = runtime.groups.unit;
        Groups.build = runtime.groups.build;
        Groups.sync = runtime.groups.sync;
        Groups.draw = runtime.groups.draw;
        Groups.fire = runtime.groups.fire;
        Groups.puddle = runtime.groups.puddle;
        Groups.weather = runtime.groups.weather;
        Groups.label = runtime.groups.label;
        Groups.powerGraph = runtime.groups.powerGraph;

        runtime.ensureAsyncProcessesForInstalledContext();
        restoreAsync(runtime.avoidance, runtime.asyncProcesses);
    }

    public static void restoreAsync(AvoidanceProcess avoidance, Seq<AsyncProcess> processes){
        Vars.avoidance = avoidance;

        if(Vars.asyncCore == null || processes == null){
            return;
        }

        Vars.asyncCore.end();
        Vars.asyncCore.processes.clear();
        Vars.asyncCore.processes.addAll(processes);
    }

    public static void validateRuntimeInstalled(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        require(Vars.world == runtime.world, "Vars.world is not runtime world.");
        require(Vars.state == runtime.state, "Vars.state is not runtime state.");
        require(Vars.collisions == runtime.collisions, "Vars.collisions is not runtime collisions.");
        require(Vars.waves == runtime.waves, "Vars.waves is not runtime waves.");
        require(Vars.player == runtime.player, "Vars.player is not runtime player.");

        require(Groups.all == runtime.groups.all, "Groups.all is not runtime group.");
        require(Groups.player == runtime.groups.player, "Groups.player is not runtime group.");
        require(Groups.bullet == runtime.groups.bullet, "Groups.bullet is not runtime group.");
        require(Groups.unit == runtime.groups.unit, "Groups.unit is not runtime group.");
        require(Groups.build == runtime.groups.build, "Groups.build is not runtime group.");
        require(Groups.sync == runtime.groups.sync, "Groups.sync is not runtime group.");
        require(Groups.draw == runtime.groups.draw, "Groups.draw is not runtime group.");
        require(Groups.fire == runtime.groups.fire, "Groups.fire is not runtime group.");
        require(Groups.puddle == runtime.groups.puddle, "Groups.puddle is not runtime group.");
        require(Groups.weather == runtime.groups.weather, "Groups.weather is not runtime group.");
        require(Groups.label == runtime.groups.label, "Groups.label is not runtime group.");
        require(Groups.powerGraph == runtime.groups.powerGraph, "Groups.powerGraph is not runtime group.");

        require(runtime.physics != null, "Runtime physics process is null.");
        require(runtime.avoidance != null, "Runtime avoidance process is null.");
        require(Vars.avoidance == runtime.avoidance, "Vars.avoidance is not runtime avoidance process.");

        if(Vars.asyncCore != null){
            require(containsIdentity(Vars.asyncCore.processes, runtime.physics), "AsyncCore does not contain runtime physics process.");
            require(containsIdentity(Vars.asyncCore.processes, runtime.avoidance), "AsyncCore does not contain runtime avoidance process.");
        }
    }

    private static boolean containsIdentity(Seq<?> seq, Object value){
        for(Object item : seq){
            if(item == value){
                return true;
            }
        }

        return false;
    }

    private static void require(boolean condition, String message){
        if(!condition){
            throw new IllegalStateException("[Seam validation] " + message);
        }
    }
}