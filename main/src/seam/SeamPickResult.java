package seam;

public final class SeamPickResult{
    public final boolean success;
    public final String message;

    public final SeamView view;
    public final SeamTileRef tileRef;
    public final SeamTileSnapshot snapshot;

    public final float hostWorldX;
    public final float hostWorldY;

    public final float runtimeWorldX;
    public final float runtimeWorldY;

    public final int tileX;
    public final int tileY;

    private SeamPickResult(
    boolean success,
    String message,
    SeamView view,
    SeamTileRef tileRef,
    SeamTileSnapshot snapshot,
    float hostWorldX,
    float hostWorldY,
    float runtimeWorldX,
    float runtimeWorldY,
    int tileX,
    int tileY
    ){
        this.success = success;
        this.message = message;
        this.view = view;
        this.tileRef = tileRef;
        this.snapshot = snapshot;
        this.hostWorldX = hostWorldX;
        this.hostWorldY = hostWorldY;
        this.runtimeWorldX = runtimeWorldX;
        this.runtimeWorldY = runtimeWorldY;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public static SeamPickResult failure(String message, float hostWorldX, float hostWorldY){
        return new SeamPickResult(
        false,
        message,
        null,
        null,
        null,
        hostWorldX,
        hostWorldY,
        0f,
        0f,
        -1,
        -1
        );
    }

    public static SeamPickResult success(
    SeamView view,
    SeamTileRef tileRef,
    SeamTileSnapshot snapshot,
    float hostWorldX,
    float hostWorldY,
    float runtimeWorldX,
    float runtimeWorldY
    ){
        return new SeamPickResult(
        true,
        "ok",
        view,
        tileRef,
        snapshot,
        hostWorldX,
        hostWorldY,
        runtimeWorldX,
        runtimeWorldY,
        tileRef.x,
        tileRef.y
        );
    }

    @Override
    public String toString(){
        return "SeamPickResult{" +
        "success=" + success +
        ", message='" + message + '\'' +
        ", view=" + view +
        ", tileRef=" + tileRef +
        ", snapshot=" + snapshot +
        ", hostWorld=" + hostWorldX + "," + hostWorldY +
        ", runtimeWorld=" + runtimeWorldX + "," + runtimeWorldY +
        ", tile=" + tileX + "," + tileY +
        '}';
    }
}