package seam.campaign;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.type.*;
import seam.core.*;
import seam.gen.*;

/** Per-world research unlock flags and requirement payments. Content definitions stay shared. */
public final class CampaignResearch{
    private static Research research;
    private static Binding<Settings> settings;
    private static Binding<State> values;

    private CampaignResearch(){}

    /** Gives {@code world} isolated tech-tree unlock and payment state backed by {@code profile}. Host thread only. */
    public static void attach(Context world, Settings profile){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        if(attached(world)) throw new IllegalStateException("The world already has campaign research.");
        if(profile == null) throw new IllegalArgumentException("A campaign profile is required.");
        if(research == null){
            research = new Research();
            settings = new Binding<>(() -> Core.settings, value -> Core.settings = value);
            values = new Binding<>(research::capture, research::install);
        }
        State state = research.read(profile);
        settings.own(world, profile);
        try{
            values.own(world, state);
        }catch(Throwable error){
            Scopes.cleanup(error, () -> settings.inherit(world));
            throw error;
        }
    }

    /** Shares {@code source}'s isolated research state with {@code world} (null detaches back to host). Host thread only. */
    public static void share(Context world, @Nullable Context source){
        Scopes.checkHost();
        Scopes.checkInactive(world);
        Scopes.checkUsable(world);
        if(source == null){
            if(CampaignProfile.attached(world)){
                throw new IllegalStateException("Detach the campaign profile before detaching campaign research.");
            }
            detach(world);
            return;
        }
        Scopes.checkUsable(source);
        if(!attached(source)) throw new IllegalStateException("The source world has no attached campaign research.");
        if(world == source) return;
        if(attached(world)) throw new IllegalStateException("The world already has campaign research.");
        shareState(world, source);
    }

    /** @return whether {@code world} has isolated research state */
    public static boolean attached(Context world){
        Scopes.checkThread();
        return settings != null && settings.isOwned(world);
    }

    static void shareState(Context world, Context source){
        settings.share(world, source);
        try{
            values.share(world, source);
        }catch(Throwable error){
            Scopes.cleanup(error, () -> settings.inherit(world));
            throw error;
        }
    }

    static void detach(Context world){
        if(settings != null) settings.inherit(world);
        if(values != null) values.inherit(world);
    }

    private static final class Research{
        final Seq<UnlockableContent> content = new Seq<>();
        final Seq<ItemStack> payments = new Seq<>();
        final Seq<String> keys = new Seq<>();
        State installed;

        Research(){
            Vars.content.each(entry -> {
                if(entry instanceof UnlockableContent unlockable) content.add(unlockable);
            });
            for(var node : TechTree.all){
                for(ItemStack stack : node.finishedRequirements){
                    payments.add(stack);
                    keys.add("req-" + node.content.name + "-" + stack.item.name);
                }
            }
        }

        State read(Settings profile){
            var state = new State(new boolean[content.size], new int[payments.size]);
            for(int i = 0; i < content.size; i++) state.unlocked[i] = profile.getBool(content.get(i).name + "-unlocked", false);
            for(int i = 0; i < payments.size; i++) state.paid[i] = profile.getInt(keys.get(i), 0);
            return state;
        }

        State capture(){
            if(installed == null) installed = new State(new boolean[content.size], new int[payments.size]);
            for(int i = 0; i < content.size; i++) installed.unlocked[i] = Engine.unlockableUnlocked(content.get(i));
            for(int i = 0; i < payments.size; i++) installed.paid[i] = payments.get(i).amount;
            return installed;
        }

        void install(State state){
            for(int i = 0; i < content.size; i++) Engine.unlockableSetUnlocked(content.get(i), state.unlocked[i]);
            for(int i = 0; i < payments.size; i++) payments.get(i).amount = state.paid[i];
            installed = state;
        }
    }

    private record State(boolean[] unlocked, int[] paid){}
}
