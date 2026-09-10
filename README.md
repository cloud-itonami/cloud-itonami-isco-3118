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

- `src/technical_drafting/store.kotoba` — `Store` protocol + `MemStore`:
  registered projects, registered **drawings** (`:draft` /
  `:issued-for-construction` / `:superseded`), committed drafting records, and a
  hash-chained append-only audit ledger.
- `src/technical_drafting/advisor.kotoba` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a drafting operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a final stamp, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/technical_drafting/operation.kotoba` — the **closed vocabulary**. `supported`
  is the allowlist of what the actor may propose; `reserved` names authority it
  does not hold (sealing, certifying or approving a drawing for construction,
  destroying the record) with a stated reason for each. An op in neither map is
  refused as `:undeclared-op`.
- `src/technical_drafting/facts.kotoba` — one named, pure predicate per question the
  governor asks, each testable without building a graph.
- `src/technical_drafting/governor.kotoba` — `TechnicalDraftingGovernor/check`: a pure
  function, wired as its own `:govern` node. It holds the ORDER of the questions;
  each question lives in `facts`, over the vocabulary in `operation`. Hard
  invariants (a request naming no project or an unregistered one, an undeclared
  or reserved op, an `:effect` that isn't `:propose`, a `:confidence` outside
  `[0,1]`, a citing op naming a drawing that is unregistered, another project's,
  or already superseded) always route to `:hold`. Escalation invariants
  (specification discrepancy flags, low advisor confidence, or revising a drawing
  that is already issued for construction) always route to `:request-approval` —
  an `interrupt-before` node that the graph checkpoints and only resumes on
  explicit human approval (`actor/approve!`).
- `src/technical_drafting/phase.kotoba` — the verdict → phase mapping as a named pure
  function. `:hard?` is checked before `:escalate?`: a proposal that is both must
  hold, because escalating it would ask a human to approve something they cannot
  authorise.
- `src/technical_drafting/ledger.kotoba` — hash-chained audit entries, `verify`, and
  the `:approved-by` field that distinguishes a human-approved write from an
  automatic one.
- `src/technical_drafting/sim.kotoba` — the governed-scenario gate (see below).
- `src/technical_drafting/actor.kotoba` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

Proposed operations (all `:effect :propose`):
- `:draft-technical-drawing` — technical drawing/CAD draft preparation
- `:revise-drawing` — drawing revision per instructions, proposed for review
- `:flag-specification-discrepancy` — surface a discrepancy between drawing and specification, ALWAYS escalates
- `:request-designer-review` — propose scheduling a designer/engineer review session

Reserved operations — **permanently blocked, never escalated**, because a human
approving them would be approving *this actor* doing something that is not theirs
to delegate: `:finalize-drawing`, `:certify-construction-ready`,
`:approve-for-construction`, `:seal-and-stamp`, `:delete-project-archive`,
`:disable-audit-ledger`.

## Checks

```bash
clojure -M:test    # unit + integration
clojure -M:sim     # governed-scenario gate
clojure -M:lint
```

`clojure -M:sim` runs a table of requests through the **real** StateGraph and
reports which the governor refused, asserting for each one the phase it reached
*and the violation rule it names* — a scenario that starts holding for the wrong
reason is a mismatch, not a pass. It exits non-zero when the table demonstrates
**no refusal at all**: a governed actor's claim is not that it acts, it is that
there exist actions it refuses, so a harness that could only pass would be
evidence of nothing.

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
