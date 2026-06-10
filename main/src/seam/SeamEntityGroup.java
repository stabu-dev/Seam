package seam;

import mindustry.entities.*;
import mindustry.gen.*;

public class SeamEntityGroup<T extends Entityc> extends EntityGroup<T>{
    public SeamEntityGroup(Class<T> type, boolean spatial, boolean mapping){
        super(type, spatial, mapping, null);
    }

    @Override
    public void removeIndex(T type, int position){
        remove(type);
    }
}