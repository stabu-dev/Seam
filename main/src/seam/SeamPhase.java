package seam;

public enum SeamPhase{
    bootstrap,

    load,
    save,

    updatePre,
    updateMutations,
    updateTeams,
    updateBuildings,
    updatePower,
    updatePuddles,
    updateFires,
    updateWeather,
    updateBullets,
    updateUnits,
    updateSync,
    updateDraw,
    updatePost,

    buildPlace,
    buildRemove,
    configure,

    renderPrepare,
    renderWorld,
    renderEntities,
    renderDebug,

    input,
    networkRead,
    networkWrite,

    validate,
    manual
}