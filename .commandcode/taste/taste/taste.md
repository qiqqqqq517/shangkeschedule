# Taste
- Communicates in Chinese and expects Chinese-language responses/artifacts. Confidence: 0.7
- Prefers autonomous, continuously-running agent workflows (self-checking and self-repairing loops) that keep going on their own and only stop when manually interrupted, rather than one-shot task execution. Confidence: 0.6
- In automation loops, wants zero idle time between iterations — no sleeping, wait/poll intervals, or backoff pacing between rounds; a green/clean result is not a reason to pause or slow down, and any per-command retry wait is bounded recovery, not an inter-round pause. Confidence: 0.7
- Wants exhaustive, file-by-file inspection with cross-checks rather than sampling — go through all the code, and fix anything imperfect however minor, instead of only finding the problem. Confidence: 0.6
- Explicitly does not want effort/token economizing on inspection work — prefers maximum thoroughness over cost. Confidence: 0.55
- When given a governing protocol/spec document, expects strict literal adherence — no improvised variation, and decisions that would require changing the spec itself are escalated to the user rather than self-authorized. Confidence: 0.55
- When a new user instruction arrives during a long autonomous run, wants the current activity yielded/dropped immediately to handle it. Confidence: 0.6
- For items that cannot be fixed, wants them logged to a pending/backlog file and the run to continue on other items rather than stalling. Confidence: 0.55
- Wants verified fixes auto-finalized end-to-end (version bump, record/changelog entry, commit, push to remotes) as part of the autonomous loop, without pausing to ask for approval first. Confidence: 0.55
