package seam.core;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;

public class SubWorldEvents{
    private final ObjectMap<Object, Seq<Cons<?>>> localEvents = new ObjectMap<>();
    private final ObjectMap<Object, Seq<Cons<?>>> hostEvents = new ObjectMap<>();
    private boolean active = false;

    public void begin(){
        if(active) throw new IllegalStateException("Already active");
        
        ObjectMap<Object, Seq<Cons<?>>> globalEvents = Reflect.get(Events.class, "events");
        
        hostEvents.clear();
        hostEvents.putAll(globalEvents);
        
        globalEvents.clear();
        globalEvents.putAll(localEvents);
        
        active = true;
    }

    public void end(){
        if(!active) throw new IllegalStateException("Not active");
        
        ObjectMap<Object, Seq<Cons<?>>> globalEvents = Reflect.get(Events.class, "events");
        
        localEvents.clear();
        localEvents.putAll(globalEvents);
        
        globalEvents.clear();
        globalEvents.putAll(hostEvents);
        
        active = false;
    }
}
