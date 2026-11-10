# D — Exact Codex configuration

**Default: GPT-6.1 Sol, High reasoning, Standard speed, local Codex desktop coding chat, using this existing project folder.** Keep this configuration for implementation, UI, routine debugging, tests, and deployment. One model is sufficient for the project.

This is an engineering recommendation: High gives complex implementation work room to reason while avoiding the routine time and usage cost of Extra High. Do not use Ultra or launch many parallel coding agents for this small, deadline-bound repository. Strong specifications and executable checks matter more than changing models repeatedly.

Official [Codex model documentation](https://learn.chatgpt.com/docs/models) identifies GPT-6.1 Sol (`gpt-6.1-sol`) and GPT-6 Astra (`gpt-6-astra`) in Codex surfaces and documents reasoning settings. Availability depends on plan, client, and rollout. API model existence alone is not evidence a particular account can select it. The actual model picker was not inspected in this task: select Sol if it appears; if it does not, report the visible options rather than pretending the configuration is active.

Use **GPT-6 Astra / Extra High** only for the independent final review, or a payment/concurrency bug that remains unresolved after a focused failing test and one serious Sol debugging attempt. Give it a concrete diff, reproducible failure, and relevant contracts. Return to Sol for normal implementation. A review can also be performed by Sol in a fresh chat; independence of evidence is more important than the model label.

| Work | Setting |
|---|---|
| All phases, including payment implementation and UI | Sol / High |
| Routine copy/style cleanup if usage becomes tight | Sol / Medium is acceptable, but avoid switching for tiny tasks |
| Final skeptical payment/security review | Astra / Extra High if selectable; otherwise Sol / High in a fresh chat |

## Plus usage and workflow

An account usage snapshot during this task reported **Plus**, ordinary usage available, **16% of the current five-hour allowance and 11% of the weekly allowance used**, no paid credit balance, and two available reset credits. No reset was applied. This is a point-in-time snapshot, not a guarantee the remaining build fits. Check the app's current meter before a long run; do not estimate Plus capacity by multiplying API token prices. Official [usage documentation](https://learn.chatgpt.com/docs/pricing) explains plan-dependent allowances and shared usage constraints.

Keep one implementation chat with durable PROGRESS/VERIFICATION files. Resume from those files after context changes. Use bounded phase reports, targeted tests, and small relevant logs. Avoid repeatedly pasting the entire repository, rerunning unchanged full suites, or asking for full rewrites. Preserve budget for a real final review and fixing its findings.

Desktop local mode is preferred because this workspace, browser inspection, secure local configuration, and interactive account setup are close together. CLI is an equivalent implementation surface if desktop execution is constrained; use the same folder and model setting. IDE adds little for an owner doing no manual coding. Cloud introduces environment/secrets/browser handoff work and is not the default for this deadline. Do not change from local to cloud midway without a concrete need.

The implementation chat is explicitly authorized by the master prompt to write and run the application. This architecture-only chat has not done so.
