(ns technical-drafting.governor
  "TechnicalDraftingGovernor — the independent safety/traceability layer for
  the ISCO-08 3118 technical draughtsperson actor. Wired as its own
  `:govern` node in `technical-drafting.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of project provenance, design
  certification, or professional responsibility, so this MUST be a separate
  system able to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. `technical-drafting.phase/of-verdict`
  routes the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  This namespace holds the ORDER of the questions and nothing else. Each
  question is a named predicate in `technical-drafting.facts`, over a
  vocabulary declared in `technical-drafting.operation`. That split is the
  repair: the checks used to be an inline `cond->` naming two forbidden ops by
  keyword equality, which made the governor a denylist over an undeclared
  vocabulary. Measured on the pre-change tree against registered project `P-1`,
  `:seal-and-stamp`, `:approve-for-construction`, `:delete-project-archive` and
  `{:op nil}` were all admitted as CLEAN verdicts — including
  `:approve-for-construction`, which is the same authority boundary the
  governor's own docstring declared as a permanent HARD block, spelled
  differently. See `technical-drafting.operation` and
  `technical-drafting.facts` for the full measurements.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. project provenance  — the request must NAME a project (`:no-project-id`)
       and that project must be registered (`:no-project`).
    2. declared vocabulary — the op must be in `operation/supported`
       (`:undeclared-op`), and must not be in `operation/reserved`
       (`:reserved-op`, an authority boundary).
    3. no-actuation        — proposal `:effect` must be `:propose`.
    4. usable confidence   — `:confidence` must be a number in [0,1].
    5. drawing provenance  — a citing op must name a registered, current
       drawing OF THIS PROJECT; a non-citing op must not carry one.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    6. an op declared `:escalates?` (`:flag-specification-discrepancy`).
    7. a usable but low confidence (< `facts/confidence-floor`).
    8. a revision of a drawing that is issued for construction — people are
       building from it, so the work is legitimate but the write is not
       automatic."
  (:require [technical-drafting.facts :as facts]
            [technical-drafting.operation :as op]))

(def confidence-floor
  "Re-exported from `technical-drafting.facts` so existing callers and tests
  that read the floor off this namespace keep working. The value lives with
  the predicate that uses it."
  facts/confidence-floor)

(defn hard-violations
  "Every hard violation for this (request, proposal, store), in the order the
  questions are asked. Returns a vector — an empty one means no hard block, and
  the vector is what a reviewer is shown, so each entry names its own rule."
  [store request proposal]
  (let [o (:op proposal)]
    (into []
          (concat
           (facts/provenance-violations store request)
           (facts/vocabulary-violations proposal)
           (facts/actuation-violations proposal)
           (facts/confidence-violations proposal)
           (facts/citation-violations proposal)
           ;; Only asked for ops that bind to a drawing by declaration.
           ;; Undeclared and reserved ops are already hard-blocked above; asking
           ;; a drawing question about them would report a second, less
           ;; specific reason for a refusal that already has its reason.
           (when (op/cites-drawing? o)
             (facts/revision-violations store request proposal))))))

(defn escalation-reasons
  "Why this proposal needs a human, given that it has no hard violation.
  Returns a vector of keywords — plural, because a revision of an
  issued-for-construction drawing submitted at low confidence needs a human for
  two independent reasons and a reviewer should see both."
  [store proposal]
  (into []
        (concat
         (when (op/escalates? (:op proposal)) [:escalating-op])
         (when (facts/low-confidence? (:confidence proposal)) [:low-confidence])
         (when (facts/issued-for-construction? store proposal) [:issued-for-construction]))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `technical-drafting.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool
    :escalation-reasons [...]}`.

  `:confidence` is echoed back unchanged — including an unusable value — so a
  reviewer reading the verdict sees what the advisor actually reported rather
  than a value this namespace substituted for it."
  ;; `context` is unused today but is part of the node's contract with the
  ;; StateGraph, which passes it; naming it `_context` keeps the arity honest
  ;; without claiming it is read.
  [request _context proposal store]
  (let [hard (hard-violations store request proposal)
        hard? (boolean (seq hard))
        reasons (if hard? [] (escalation-reasons store proposal))]
    {:ok? (and (not hard?) (empty? reasons))
     :violations hard
     :confidence (:confidence proposal)
     :hard? hard?
     :escalate? (and (not hard?) (boolean (seq reasons)))
     :escalation-reasons reasons}))
