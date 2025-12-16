#!/usr/bin/env bash
set -e

BASE="modules/automation/src/main/kotlin/com/jarvismini/automation"

echo "🔧 Creating automation stubs..."

mkdir -p "$BASE/decision"
mkdir -p "$BASE/input"
mkdir -p "$BASE/orchestrator"

# ---------------------------
# ReplyDecision
# ---------------------------
cat > "$BASE/decision/ReplyDecision.kt" <<'EOF'
package com.jarvismini.automation.decision

sealed class ReplyDecision

object NoReplyDecision : ReplyDecision()

data class AutoReplyDecision(
    val message: String,
    val reason: String? = null
) : ReplyDecision()
EOF

# ---------------------------
# AutoReplyInput
# ---------------------------
cat > "$BASE/input/AutoReplyInput.kt" <<'EOF'
package com.jarvismini.automation.input

data class AutoReplyInput(
    val messageText: String,
    val isOwner: Boolean
)
EOF

# ---------------------------
# AutoReplyOrchestrator
# ---------------------------
cat > "$BASE/orchestrator/AutoReplyOrchestrator.kt" <<'EOF'
package com.jarvismini.automation.orchestrator

import com.jarvismini.automation.decision.*
import com.jarvismini.automation.input.AutoReplyInput

object AutoReplyOrchestrator {

    fun process(input: AutoReplyInput): ReplyDecision {
        return NoReplyDecision
    }

    fun sendResponse(message: String) {
        // stub
    }
}
EOF

# ---------------------------
# JarvisState (if missing)
# ---------------------------
CORE_BASE="modules/core/src/main/kotlin/com/jarvismini/core"
mkdir -p "$CORE_BASE"

if [ ! -f "$CORE_BASE/JarvisState.kt" ]; then
cat > "$CORE_BASE/JarvisState.kt" <<'EOF'
package com.jarvismini.core

object JarvisState {
    var currentMode: JarvisMode = JarvisMode.NORMAL
}
EOF
fi

echo "✅ Automation stubs created successfully"
