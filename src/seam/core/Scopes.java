package seam.core;

import arc.func.*;
import arc.util.*;
import mindustry.game.*;

/** Scope guards, cleanup helpers, and internal hooks shared with optional Seam packages. */
public final class Scopes{
    private Scopes(){}

    /** Throws unless called on the main application thread. */
    public static void checkThread(){
        Context.checkThread();
    }

    /** Throws unless called in host scope with no active context, exchange in progress, or prior restore failure. */
    public static void checkHost(){
        Context.checkHost();
    }

    /** Throws if called off the main thread or while world or binding state is inconsistent. */
    public static void checkState(){
        Context.checkState();
    }

    /** Throws unless state is consistent and {@code world} is non-null and not currently entered. */
    public static void checkInactive(Context world){
        Context.checkInactive(world);
    }

    /** Throws if called off the main thread or if {@code world} is null, disposed, closing, failed, or invalid. */
    public static void checkUsable(Context world){
        if(world == null) throw new IllegalArgumentException("A world is required.");
        world.checkUsable();
    }

    /** @return whether {@code world} is still usable and not queued for teardown */
    public static boolean isReady(Context world){
        if(world == null) throw new IllegalArgumentException("A world is required.");
        return world.isReady();
    }

    /** Runs {@code task}, attaching any thrown error as suppressed on {@code failure} (or returning it if null). */
    public static Throwable cleanup(@Nullable Throwable failure, Runnable task){
        return Context.cleanup(failure, task);
    }

    /** Installs the step guard for campaign worlds (used by {@code seam.campaign}). */
    public static void setCampaignGuard(Cons<Context> guard){
        Context.checkThread();
        Campaigns.setCampaignGuard(guard);
    }

    /** Installs the view presence check for displayed campaign worlds (used by {@code seam.client}). */
    public static void setViewCheck(Boolf<Context> check){
        Context.checkThread();
        Campaigns.setViewCheck(check);
    }

    /** Installs the resolver mapping contexts to isolated campaign identities (used by {@code seam.campaign}). */
    public static void setCampaignResolver(Func<Context, Object> resolver){
        Context.checkThread();
        Campaigns.setCampaignResolver(resolver);
    }

    /** Creates an isolated {@link Universe} clock with shared-campaign sector awareness. */
    public static Universe newCampaignUniverse(){
        return Campaigns.newIsolatedUniverse();
    }

    /** Resets a world's captured {@link Saves} cursor state. Caller must keep host scope. */
    public static void resetSavesCursor(Context world){
        Context.checkHost();
        Context.checkInactive(world);
        world.resetSavesCursor();
    }
}
