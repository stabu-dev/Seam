package seam;

@FunctionalInterface
public interface SeamRuntimeCallable<T>{
    T call(SeamRuntime runtime);
}