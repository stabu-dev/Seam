package seam;

public final class SeamWorldDrawSettings{
    public boolean enabled = true;

    public boolean drawFloors = true;
    public boolean drawBlocks = true;

    public boolean drawStaticShadows = true;
    public boolean drawCustomShadows = true;

    public boolean drawCracks = true;
    public boolean drawTeamOverlays = true;
    public boolean drawStatus = true;

    public boolean drawDrawEntities = true;

    public boolean drawLights = false;

    public boolean respectVanillaStatusToggle = true;
    public boolean updateVisibilityFlags = true;

    public SeamWorldDrawSettings copy(){
        SeamWorldDrawSettings copy = new SeamWorldDrawSettings();

        copy.enabled = enabled;

        copy.drawFloors = drawFloors;
        copy.drawBlocks = drawBlocks;

        copy.drawStaticShadows = drawStaticShadows;
        copy.drawCustomShadows = drawCustomShadows;

        copy.drawCracks = drawCracks;
        copy.drawTeamOverlays = drawTeamOverlays;
        copy.drawStatus = drawStatus;

        copy.drawDrawEntities = drawDrawEntities;

        copy.drawLights = drawLights;

        copy.respectVanillaStatusToggle = respectVanillaStatusToggle;
        copy.updateVisibilityFlags = updateVisibilityFlags;

        return copy;
    }

    @Override
    public String toString(){
        return "SeamWorldDrawSettings{" +
        "enabled=" + enabled +
        ", drawFloors=" + drawFloors +
        ", drawBlocks=" + drawBlocks +
        ", drawStaticShadows=" + drawStaticShadows +
        ", drawCustomShadows=" + drawCustomShadows +
        ", drawCracks=" + drawCracks +
        ", drawTeamOverlays=" + drawTeamOverlays +
        ", drawStatus=" + drawStatus +
        ", drawDrawEntities=" + drawDrawEntities +
        ", drawLights=" + drawLights +
        ", respectVanillaStatusToggle=" + respectVanillaStatusToggle +
        ", updateVisibilityFlags=" + updateVisibilityFlags +
        '}';
    }
}