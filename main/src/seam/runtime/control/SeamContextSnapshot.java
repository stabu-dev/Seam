package seam.runtime.control;

import arc.struct.*;

public final class SeamContextSnapshot{
    private final Seq<SeamContextSlot.Captured> captured = new Seq<>();

    SeamContextSnapshot(Seq<SeamContextSlot<?>> slots){
        if(slots == null){
            throw new NullPointerException("slots");
        }

        for(SeamContextSlot<?> slot : slots){
            captured.add(slot.capture());
        }
    }

    public int size(){
        return captured.size;
    }

    public boolean empty(){
        return captured.isEmpty();
    }

    public void restore(){
        for(int i = captured.size - 1; i >= 0; i--){
            captured.get(i).restore();
        }
    }
}