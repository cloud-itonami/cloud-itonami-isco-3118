(ns technical-drafting.governor
  "TechnicalDraftingGovernor — the independent safety/traceability layer for
  the ISCO-08 3118 technical draughtsperson actor. Wired as its own
  `:govern` node in `technical-drafting.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of project provenance, design
  certification, or professional responsibility, so this MUST be a separate
  system able to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. project provenance  — the request's project must be registered.
    2. no-actuation        — proposal :effect must be :propose.
    3. no-finalization/certification — any attempt to finalize/certify a drawing
       as construction-ready or ready for production is a permanent block (that
       is the licensed designer's or engineer's exclusive professional responsibility).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. :op :flag-specification-discrepancy (always escalates).
    5. low confidence (< `confidence-floor`)."
  (:require [technical-drafting.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:flag-specification-discrepancy})

(defn- hard-violations [{:keys [proposal]} project-record]
  (cond-> []
    (nil? project-record)
    (conj {:rule :no-project :detail "未登録 project"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (or (= :finalize-drawing (:op proposal))
        (= :certify-construction-ready (:op proposal)))
    (conj {:rule :no-finalization :detail "finalizing or certifying drawings for construction is licensed designer's or engineer's exclusive responsibility"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `technical-drafting.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [project-record (store/project store (:project-id request))
        hard (hard-violations {:proposal proposal} project-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))
