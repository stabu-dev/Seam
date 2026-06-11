package seam.world.tiles;

import mindustry.game.*;

public final class SeamTileSnapshot{
    public final boolean success;
    public final String message;

    public final SeamTileRef ref;

    public final String blockName;
    public final String floorName;

    public final boolean hasBuilding;
    public final String buildingType;
    public final Team team;
    public final int rotation;

    public final boolean hasItems;
    public final int itemTotal;

    public final boolean hasLiquids;
    public final String currentLiquidName;
    public final float currentLiquidAmount;

    public final boolean hasPower;
    public final boolean hasPowerGraph;
    public final int powerLinkCount;

    private SeamTileSnapshot(
    boolean success,
    String message,
    SeamTileRef ref,
    String blockName,
    String floorName,
    boolean hasBuilding,
    String buildingType,
    Team team,
    int rotation,
    boolean hasItems,
    int itemTotal,
    boolean hasLiquids,
    String currentLiquidName,
    float currentLiquidAmount,
    boolean hasPower,
    boolean hasPowerGraph,
    int powerLinkCount
    ){
        this.success = success;
        this.message = message;
        this.ref = ref;
        this.blockName = blockName;
        this.floorName = floorName;
        this.hasBuilding = hasBuilding;
        this.buildingType = buildingType;
        this.team = team;
        this.rotation = rotation;
        this.hasItems = hasItems;
        this.itemTotal = itemTotal;
        this.hasLiquids = hasLiquids;
        this.currentLiquidName = currentLiquidName;
        this.currentLiquidAmount = currentLiquidAmount;
        this.hasPower = hasPower;
        this.hasPowerGraph = hasPowerGraph;
        this.powerLinkCount = powerLinkCount;
    }

    public static SeamTileSnapshot failure(SeamTileRef ref, String message){
        return new SeamTileSnapshot(
        false,
        message,
        ref,
        null,
        null,
        false,
        null,
        null,
        0,
        false,
        0,
        false,
        null,
        0f,
        false,
        false,
        0
        );
    }

    public static SeamTileSnapshot success(
    SeamTileRef ref,
    String blockName,
    String floorName,
    boolean hasBuilding,
    String buildingType,
    Team team,
    int rotation,
    boolean hasItems,
    int itemTotal,
    boolean hasLiquids,
    String currentLiquidName,
    float currentLiquidAmount,
    boolean hasPower,
    boolean hasPowerGraph,
    int powerLinkCount
    ){
        return new SeamTileSnapshot(
        true,
        "ok",
        ref,
        blockName,
        floorName,
        hasBuilding,
        buildingType,
        team,
        rotation,
        hasItems,
        itemTotal,
        hasLiquids,
        currentLiquidName,
        currentLiquidAmount,
        hasPower,
        hasPowerGraph,
        powerLinkCount
        );
    }

    @Override
    public String toString(){
        return "SeamTileSnapshot{" +
        "success=" + success +
        ", message='" + message + '\'' +
        ", ref=" + ref +
        ", blockName='" + blockName + '\'' +
        ", floorName='" + floorName + '\'' +
        ", hasBuilding=" + hasBuilding +
        ", buildingType='" + buildingType + '\'' +
        ", team=" + team +
        ", rotation=" + rotation +
        ", hasItems=" + hasItems +
        ", itemTotal=" + itemTotal +
        ", hasLiquids=" + hasLiquids +
        ", currentLiquidName='" + currentLiquidName + '\'' +
        ", currentLiquidAmount=" + currentLiquidAmount +
        ", hasPower=" + hasPower +
        ", hasPowerGraph=" + hasPowerGraph +
        ", powerLinkCount=" + powerLinkCount +
        '}';
    }
}