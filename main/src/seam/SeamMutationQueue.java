package seam;

import arc.struct.*;

public final class SeamMutationQueue{
    private final Seq<SeamMutation> pending = new Seq<>();
    private final Seq<SeamMutation> draining = new Seq<>();
    private final Seq<SeamMutationResult> lastResults = new Seq<>();

    public int size(){
        return pending.size;
    }

    public boolean empty(){
        return pending.isEmpty();
    }

    public Seq<SeamMutationResult> lastResults(){
        return lastResults.copy();
    }

    public SeamMutation enqueue(SeamMutation mutation){
        if(mutation == null){
            throw new NullPointerException("mutation");
        }

        pending.add(mutation);
        return mutation;
    }

    public Seq<SeamMutationResult> drain(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        draining.clear();

        for(SeamMutation mutation : pending){
            draining.add(mutation);
        }

        pending.clear();
        lastResults.clear();

        for(SeamMutation mutation : draining){
            SeamMutationResult result;

            if(mutation.runtimeId != runtime.id){
                result = SeamMutationResult.failure(
                mutation,
                "mutation runtime id does not match target runtime",
                null,
                null
                );
            }else{
                try{
                    result = mutation.apply(runtime);
                }catch(Throwable throwable){
                    result = SeamMutationResult.failure(
                    mutation,
                    throwable.getClass().getSimpleName() + ": " + throwable.getMessage(),
                    null,
                    throwable
                    );
                }
            }

            lastResults.add(result);
        }

        draining.clear();

        return lastResults.copy();
    }

    public void clear(){
        pending.clear();
        draining.clear();
        lastResults.clear();
    }
}