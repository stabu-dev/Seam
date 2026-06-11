package seam.core;

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
    updateDelayed,
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