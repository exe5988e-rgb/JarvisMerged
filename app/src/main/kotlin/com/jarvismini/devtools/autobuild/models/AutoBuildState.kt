package com.jarvismini.devtools.autobuild.models

enum class AutoBuildState {
    // ── Existing autobuild states ─────────────────────────────────────────
    IDLE,
    WAITING_FOR_RESPONSE,
    DOWNLOAD_AI_OUTPUT,
    COPY_TO_AUTOMATION_DIR,
    TRIGGER_BUILD,
    WAITING_FOR_BUILD,
    BUILD_SUCCEEDED,
    ATTACHING_ERROR_REPORT,
    TIMEOUT_ERROR,

    // ── Agent loop states (Session 20) ────────────────────────────────────
    AGENT_IDLE,
    AGENT_SENDING_DUMP,
    AGENT_WAITING_FOR_RESPONSE,
    AGENT_DOWNLOAD_OUTPUT,
    AGENT_STAGING_OUTPUT,
    AGENT_LOOP_DONE,
}
