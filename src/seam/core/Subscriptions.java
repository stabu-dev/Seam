package seam.core;

import arc.func.*;
import arc.struct.*;
import seam.gen.*;

/** Snapshot of selected native event registrations for attaching or detaching in another context. */
public final class Subscriptions{
    /** Number of captured event listener entries in this snapshot. */
    public final int size;
    private final Seq<Entry> entries;

    private Subscriptions(Seq<Entry> entries){
        this.entries = entries;
        size = entries.size;
    }

    /** Captures a frozen snapshot of the installed registry matching {@code select}; keys are event classes or triggers. */
    public static Subscriptions capture(Boolf2<Object, Cons<?>> select){
        Context.checkState();
        if(select == null) throw new IllegalArgumentException("A listener selector is required.");
        var source = new Seq<Entry>();
        //iterate a copy so Arc's cached ObjectMap iterators on Events.events cannot pin child listeners after exit
        for(var entry : Engine.eventsEvents().copy()){
            for(Cons<?> listener : entry.value) source.add(new Entry(entry.key, listener));
        }
        Seq<Entry> chosen = source.select(entry -> select.get(entry.type, entry.listener));
        Context.checkState();
        return new Subscriptions(chosen);
    }

    /** Appends these captured listeners to the current world's event registry. */
    public void attach(){
        Context.checkState();
        var registry = Engine.eventsEvents();
        for(Entry entry : entries) registry.get(entry.type, () -> new Seq<>(Cons.class)).add(entry.listener);
    }

    /** Removes these captured listeners from the current world's event registry. */
    public void detach(){
        Context.checkState();
        var registry = Engine.eventsEvents();
        for(Entry entry : entries){
            var listeners = registry.get(entry.type);
            if(listeners != null) listeners.remove(entry.listener);
        }
    }

    /** @return listeners registered for {@code type} in the installed world, created empty if absent */
    public static Seq<Cons<?>> listeners(Object type){
        Context.checkState();
        return Engine.eventsEvents().get(type, Seq::new);
    }

    /** Replays listeners of {@code type} registered at index {@code from} or later with {@code event}. */
    @SuppressWarnings("unchecked")
    public static <T> void replay(Class<T> type, int from, T event){
        Seq<Cons<?>> all = listeners(type);
        for(int i = from; i < all.size; i++) ((Cons<T>)all.get(i)).get(event);
    }

    private record Entry(Object type, Cons<?> listener){}
}
