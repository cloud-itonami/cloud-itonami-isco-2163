# cloud-itonami-isco-2163

Open Occupation Blueprint for **ISCO-08 2163**: Product and Garment Designers.

This repository designs a forkable OSS platform for an independent product and garment designer: a design-support robot prepares design concepts, material/pattern specifications, and safety compliance checks under a governor-gated actor, so the practice keeps its own project records and maintains professional control over final production decisions (production sign-offs and safety certifications remain the licensed designer's exclusive responsibility).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a design-support robot prepares design concepts, specifications, safety compliance assessments, and client engagement materials under an actor that proposes actions and an independent **Design Governor** that gates them. The governor never
dispatches the designer's professional seal; `:high`/`:safety-critical` actions (such as
issuing production sign-offs, or certifying safety/regulatory compliance) remain the licensed
designer's exclusive responsibility and can only be proposed, never automated.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
design brief + style reference + safety regulations + client requirements
        |
        v
Design Advisor -> Design Governor -> draft design / prepare spec, or human sign-off
        |
        v
robot actions (gated) + design records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive design data without governor approval and
audit evidence. No proposal can claim to issue a final production sign-off or
certify safety/regulatory compliance — those remain the licensed human designer's exclusive
professional and legal responsibility.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2163`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section, alongside `cloud-itonami-isco-2161`, `-8160`, `-2166`, `-2641`,
`-2651`, `-2652`, `-2654`, `-1219`, `-1223`, `-1330`, `-1341`, `-1349`,
`-1412`, `-1439`, `-2144` and `-2320`): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/design/store.cljc` — `Store` protocol + `MemStore`:
  registered projects/clients, committed design records, an append-only audit ledger.
- `src/design/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a design operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a final production sign-off, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/design/governor.cljc` — `DesignGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered project, a proposal whose `:effect` isn't `:propose`,
  any attempt to issue a production sign-off or certify compliance)
  always route to `:hold`. Escalation invariants (safety/regulatory
  compliance flags, material safety concerns, or low advisor confidence) always route to
  `:request-approval` — an `interrupt-before` node that the graph
  checkpoints and only resumes on explicit human approval
  (`actor/approve!`), matching the README's robotics-premise statement
  that production sign-offs and compliance certification always remain the
  licensed designer's sole responsibility.
- `src/design/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
