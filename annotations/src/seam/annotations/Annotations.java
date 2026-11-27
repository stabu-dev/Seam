package seam.annotations;

import java.lang.annotation.*;

public final class Annotations{
    private Annotations(){}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface WorldStateSpec{
        String className() default "WorldStateGen";
        Class<?>[] allPublicStatic() default {};
        FieldGroup[] fields() default {};
        Class<?>[] payloadHosts() default {};
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface FieldGroup{
        Class<?> type();
        String[] names();
        String[] initial() default {};
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetFacade{
        Class<?> target();
        String className() default "LocalNetBase";
        String[] offlineFalse() default {};
        String[] offlineNull() default {};
        String[] offlineEmpty() default {};
        String[] offlineDrop() default {};
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface SceneFacade{
        Class<?> scene();
        Class<?> input();
        String className() default "WorldSceneBase";
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface EngineSpec{
        String className() default "Engine";
        ClassAccess[] classes() default {};
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface ClassAccess{
        Class<?> type() default Void.class;
        String typeName() default "";
        String prefix();
        String[] fields() default {};
        String[] writable() default {};
        String[] setters() default {};
        String[] methods() default {};
        boolean constructor() default false;
    }
}
