package seam;

public enum SeamPhase{
    bootstrap,

    load,
    save,

    updatePre,
    updateMutations,
    updateTeams,
    updateGroups,
    updateBuildings,
    updatePower,
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