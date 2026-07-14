# cloud-itonami-isco-3118

Open Occupation Blueprint for **ISCO-08 3118**: Draughtspersons (Technical Drafting).

This repository designs a forkable OSS platform for a technical draughtsperson: a technical drafting support robot prepares technical drawings, revisions, and specification notes under a governor-gated actor, so the practice keeps its own project records and maintains professional control over final design decisions (finalization and construction-ready certification remain the licensed designer's or engineer's exclusive responsibility).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the technical domain work**. Here a technical drafting robot prepares technical drawings, drawing revisions, specification notes, and flags specification discrepancies under an actor that proposes actions and an independent **Technical Drafting Governor** that gates them. The governor never
dispatches the designer's or engineer's professional authority; `:high`/`:safety-critical` actions (such as
finalizing or certifying drawings for construction) remain the licensed designer's or engineer's exclusive responsibility and can only be proposed, never automated.

## Core Contract

```text
design brief + specifications + technical requirements
        |
        v
Technical Drafting Advisor -> Technical Drafting Governor -> technical drawing / revision, or human sign-off
        |
        v
robot actions (gated) + drafting records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive design data without governor approval and
audit evidence. No proposal can claim to finalize or certify a drawing for construction — those remain the licensed designer's or engineer's exclusive professional and legal responsibility.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3118`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :audit-ledger

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/technical_drafting/store.cljc` — `Store` protocol + `MemStore`:
  registered projects/clients, committed drafting records, an append-only audit ledger.
- `src/technical_drafting/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a drafting operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a final stamp, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/technical_drafting/governor.cljc` — `TechnicalDraftingGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered project, a proposal whose `:effect` isn't `:propose`,
  any attempt to finalize or certify a drawing for construction)
  always route to `:hold`. Escalation invariants (specification discrepancy flags
  or low advisor confidence) always route to
  `:request-approval` — an `interrupt-before` node that the graph
  checkpoints and only resumes on explicit human approval
  (`actor/approve!`), matching the README's robotics-premise statement
  that finalizing and construction-ready certification always remain the
  licensed designer's or engineer's sole responsibility.
- `src/technical_drafting/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

Proposed operations (all `:effect :propose`):
- `:draft-technical-drawing` — technical drawing/CAD draft preparation
- `:revise-drawing` — drawing revision per instructions, proposed for review
- `:flag-specification-discrepancy` — surface a discrepancy between drawing and specification, ALWAYS escalates
- `:request-designer-review` — propose scheduling a designer/engineer review session

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
