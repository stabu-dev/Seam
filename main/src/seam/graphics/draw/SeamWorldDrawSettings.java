package seam.graphics.draw;

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
    public boolean drawWeather = true;
    public boolean drawDarkness = true;

    public boolean drawLights = false;
    public boolean drawBloom = true;
    public boolean drawAnimatedShields = true;
    public boolean drawAnimatedBuildBeams = true;

    public boolean respectVanillaStatusToggle = true;
    public boolean updateVisibilityFlags = true;

    public float screenShaderRange = 1f;

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
        copy.drawWeather = drawWeather;
        copy.drawDarkness = drawDarkness;

        copy.drawLights = drawLights;
        copy.drawBloom = drawBloom;
        copy.drawAnimatedShields = drawAnimatedShields;
        copy.drawAnimatedBuildBeams = drawAnimatedBuildBeams;

        copy.respectVanillaStatusToggle = respectVanillaStatusToggle;
        copy.updateVisibilityFlags = updateVisibilityFlags;

        copy.screenShaderRange = screenShaderRange;

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
        ", drawWeather=" + drawWeather +
        ", drawDarkness=" + drawDarkness +
        ", drawLights=" + drawLights +
        ", drawBloom=" + drawBloom +
        ", drawAnimatedShields=" + drawAnimatedShields +
        ", drawAnimatedBuildBeams=" + drawAnimatedBuildBeams +
        ", respectVanillaStatusToggle=" + respectVanillaStatusToggle +
        ", updateVisibilityFlags=" + updateVisibilityFlags +
        ", screenShaderRange=" + screenShaderRange +
        '}';
    }
}