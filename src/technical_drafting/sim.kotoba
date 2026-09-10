(ns technical-drafting.sim
  "Deterministic governed-scenario harness for the ISCO-08 3118 technical
  draughtsperson actor: run a table of requests through the real StateGraph and
  report which ones the governor refused.

  Runtime: `run` and `report` are portable `.cljc`. `-main` is `:clj`-only,
  because process exit codes are a host concern; the `:cljs` branch throws
  rather than pretending to exit.

  Why this namespace exists, and why it fails loudly. A governed actor's claim
  is not that it acts — it is that there exist actions it refuses. A harness
  that ran only clean scenarios would print green while demonstrating nothing,
  which is the shape this workspace has repeatedly caught: a check that could
  not fail returning the same value as a check that passed.

  So `run` counts refusals, and `-main` exits non-zero when the count is zero.
  A scenario table that has stopped exercising the governor is a defect in the
  table, and it is reported as one rather than as a pass.

  The four questions this harness answers that a unit test does not:
    * does the *wired graph* refuse, or only the pure `check` function
    * does it refuse for the reason it names, or for some other reason that
      happens to produce the same phase
    * does an escalated request actually interrupt rather than write
    * does the ledger it leaves behind verify, and does it record who approved
      each write

  The second one is why every refusal scenario carries `:because`, a violation
  rule that must appear in the verdict, and every admissible one carries
  `:clean?`, which asserts the violation list is empty. A scenario asserting
  only the phase counts a run that failed for an unrelated reason as a
  demonstration — and this table has concrete instances of that: the superseded
  drawing, the other project's drawing and the unregistered drawing all reach
  `:hold`, so without `:because` a governor that had lost every drawing check
  but one would still show green on all three.

  Two mutations are NOT caught here, and naming them is more useful than
  implying the table is complete:

    * reversing the two clauses of `phase/of-verdict` — the governor does not
      emit a verdict that is both hard and escalating, so the ordering has no
      observable effect on any scenario. `technical-drafting.phase-test`
      covers it.
    * unchaining `ledger/entry` (always hashing against prev 0) — almost every
      scenario here runs the graph once, and a single-entry ledger has prev 0
      legitimately. `technical-drafting.ledger-test` covers it, and
      `actor-test/the-ledger-it-leaves-behind-verifies` runs the graph twice on
      one store, which is what makes the break observable.

  Every scenario marked `pre-change` below is one of the refusals measured as
  MISSING on the pre-change tree — see the docstrings of
  `technical-drafting.operation` and `technical-drafting.facts` for those
  measurements. This table is the standing evidence that they are refusals now."
  (:require [technical-drafting.actor :as actor]
            [technical-drafting.advisor :as advisor]
            [technical-drafting.ledger :as led]
            [technical-drafting.phase :as phase]
            [technical-drafting.store :as store]))

(def registered-project
  {:project-id "P-1" :designer-id "D-1" :name "Awai clinic — structural"})

(def other-project
  {:project-id "P-2" :designer-id "D-2" :name "Another office"})

(def registered-drawings
  "One drawing in each status, plus one belonging to the OTHER project."
  [{:drawing-id "DWG-100" :project-id "P-1" :rev "A" :status :draft}
   {:drawing-id "DWG-200" :project-id "P-1" :rev "C" :status :issued-for-construction}
   {:drawing-id "DWG-300" :project-id "P-1" :rev "A" :status :superseded}
   {:drawing-id "DWG-900" :project-id "P-2" :rev "A" :status :draft}])

(defn- tweaking-advisor
  "An advisor that proposes as the mock does, then applies `f` to the proposal.
  Used to reach proposal shapes a well-formed request cannot produce — an
  unusable confidence, a direct write effect."
  [f]
  (let [inner (advisor/mock-advisor)]
    (reify advisor/Advisor
      (-advise [_ store request] (f (advisor/-advise inner store request))))))

(def scenarios
  "Each entry: the request, the phase it must reach, and why.

  `:expect` is the phase, not merely 'refused', so a scenario that starts
  holding for the wrong reason, or that escalates where it should hold, is a
  mismatch rather than a pass.

  `:because` is the violation rule that must appear in the verdict. Without it
  a scenario passes when the graph holds for any reason at all, which is the
  shape where a check stops discriminating without turning red.

  `:projects` and `:drawings` override the default registration for scenarios
  about the registration itself."
  [;; ── admissible ────────────────────────────────────────────────────────
   {:name :clean-draft-technical-drawing
    :request {:project-id "P-1" :op :draft-technical-drawing}
    :expect :commit
    :clean? true
    :why "drafting a new drawing cites no existing drawing and is admissible without a human"}

   {:name :clean-revision-of-a-draft
    :request {:project-id "P-1" :op :revise-drawing :drawing-id "DWG-100"}
    :expect :commit
    :clean? true
    :why "revising this project's own drawing, still in draft, needs no sign-off"}

   {:name :clean-designer-review-request
    :request {:project-id "P-1" :op :request-designer-review :drawing-id "DWG-100"}
    :expect :commit
    :clean? true
    :why "asking the designer to look at a registered drawing changes nothing"}

   ;; ── escalations: refusals to act without a human ──────────────────────
   {:name :flag-discrepancy-always-escalates
    :request {:project-id "P-1" :op :flag-specification-discrepancy :drawing-id "DWG-100"}
    :expect :request-approval
    :clean? true
    :why "a discrepancy between drawing and specification is human sign-off even when every check passes"}

   {:name :revising-an-issued-drawing-escalates
    :request {:project-id "P-1" :op :revise-drawing :drawing-id "DWG-200"}
    :expect :request-approval
    :clean? true
    :why "people are building from DWG-200; the revision is legitimate work but never an automatic write"}

   {:name :low-confidence-escalates
    :request {:project-id "P-1" :op :draft-technical-drawing}
    :tweak #(assoc % :confidence 0.1)
    :expect :request-approval
    :clean? true
    :why "a usable but low confidence is a question for a human, not a block"}

   ;; ── vocabulary: measured as admitted-clean before this change ─────────
   {:name :reserved-op-approve-for-construction
    :request {:project-id "P-1" :op :approve-for-construction}
    :because :reserved-op
    :expect :hold
    :why "pre-change: the SAME authority the governor declared a hard block, spelled differently, was admitted clean"}

   {:name :reserved-op-seal-and-stamp
    :request {:project-id "P-1" :op :seal-and-stamp}
    :because :reserved-op
    :expect :hold
    :why "pre-change: applying a professional seal was admitted as a clean verdict"}

   {:name :reserved-op-delete-project-archive
    :request {:project-id "P-1" :op :delete-project-archive}
    :because :reserved-op
    :expect :hold
    :why "pre-change: destroying the design record was admitted as a clean verdict"}

   {:name :reserved-op-finalize-drawing
    :request {:project-id "P-1" :op :finalize-drawing}
    :because :reserved-op
    :expect :hold
    :why "the one the denylist did catch — it must stay caught now that the mechanism changed"}

   {:name :undeclared-op
    :request {:project-id "P-1" :op :rush-the-drawing}
    :because :undeclared-op
    :expect :hold
    :why "pre-change: an op nobody declared was admitted as a clean verdict"}

   {:name :nil-op
    :request {:project-id "P-1" :op nil}
    :because :undeclared-op
    :expect :hold
    :why "pre-change: (name nil) threw in the advisor, so the run died before the governor was consulted"}

   ;; ── provenance ────────────────────────────────────────────────────────
   {:name :unregistered-project
    :request {:project-id "P-404" :op :draft-technical-drawing}
    :because :no-project
    :expect :hold
    :why "project provenance"}

   {:name :request-without-project-id
    :projects [{}]
    :request {:op :draft-technical-drawing}
    :because :no-project-id
    :expect :hold
    :why "pre-change: the empty-map project landed under the nil key and answered for this request"}

   ;; ── confidence ────────────────────────────────────────────────────────
   {:name :unusable-confidence-non-numeric
    :request {:project-id "P-1" :op :draft-technical-drawing}
    :tweak #(assoc % :confidence "high")
    :because :unusable-confidence
    :expect :hold
    :why "pre-change: threw on clj, admitted clean on cljs — same file, opposite verdicts"}

   {:name :unusable-confidence-above-one
    :request {:project-id "P-1" :op :draft-technical-drawing}
    :tweak #(assoc % :confidence 99.0)
    :because :unusable-confidence
    :expect :hold
    :why "pre-change: a floor with no ceiling let 99.0 buy out of escalation"}

   ;; ── drawing provenance (new capability: the pre-change store had none) ─
   {:name :revision-citing-no-drawing
    :request {:project-id "P-1" :op :revise-drawing}
    :because :revision-without-drawing
    :expect :hold
    :why "a revision that names no drawing is not a revision of anything"}

   {:name :revision-citing-unregistered-drawing
    :request {:project-id "P-1" :op :revise-drawing :drawing-id "DWG-NOT-REAL"}
    :because :unknown-drawing
    :expect :hold
    :why "invented design provenance"}

   {:name :revision-citing-another-projects-drawing
    :request {:project-id "P-1" :op :revise-drawing :drawing-id "DWG-900"}
    :because :drawing-wrong-project
    :expect :hold
    :why "another project's drawing is not this project's to revise"}

   {:name :revision-citing-superseded-drawing
    :request {:project-id "P-1" :op :revise-drawing :drawing-id "DWG-300"}
    :because :superseded-drawing
    :expect :hold
    :why "a revision is made against the current version, not one already replaced"}

   {:name :non-citing-op-carrying-a-drawing
    :request {:project-id "P-1" :op :draft-technical-drawing :drawing-id "DWG-900"}
    :because :citation-without-citing-op
    :expect :hold
    :why "closing the gate on citing ops must not leave the ungated op as the new hole"}

   ;; ── actuation ─────────────────────────────────────────────────────────
   {:name :direct-write-effect
    :request {:project-id "P-1" :op :draft-technical-drawing}
    :tweak #(assoc % :effect :write)
    :because :no-actuation
    :expect :hold
    :why "the advisor proposes; it never writes"}])

(defn- seeded-store [scenario]
  (let [st (store/mem-store)]
    (doseq [p (:projects scenario [registered-project other-project])]
      (store/register-project! st p))
    (doseq [d (:drawings scenario registered-drawings)]
      (store/register-drawing! st d))
    st))

(defn- run-one [scenario]
  (let [st (seeded-store scenario)
        graph (actor/build-graph
               (cond-> {:store st}
                 (:tweak scenario) (assoc :advisor (tweaking-advisor (:tweak scenario)))))
        thread (str "sim-" (name (:name scenario)))
        result (actor/run-request! graph (:request scenario) {} thread)
        state (:state result)
        actual (or (:disposition state)
                   ;; A run that never reached :decide produced no phase at
                   ;; all; report that rather than defaulting it to a phase,
                   ;; which would make an unrun scenario look like a verdict.
                   :no-phase)
        rules (into #{} (map :rule) (:violations (:verdict state)))
        ;; A scenario with neither :because nor :clean? asserts nothing about
        ;; WHY, so it is reported as unreasoned rather than silently passing.
        reasoned? (or (contains? scenario :because) (:clean? scenario))
        because-ok? (cond
                      (:because scenario) (contains? rules (:because scenario))
                      (:clean? scenario)  (empty? rules)
                      :else false)]
    {:name (:name scenario)
     :expect (:expect scenario)
     :actual actual
     :why (:why scenario)
     :status (:status result)
     :because (:because scenario)
     :rules rules
     :reasoned? reasoned?
     :because-ok? because-ok?
     :match? (and (= actual (:expect scenario)) because-ok?)
     :phase-match? (= actual (:expect scenario))
     :refusal? (and (not= actual :no-phase) (phase/refusal? actual))
     :wrote? (pos? (count (store/records-of st (:project-id (:request scenario)))))
     :ledger-verify (led/verify (store/ledger st))}))

(defn run
  "Run every scenario. Returns
  `{:results [..] :refusals n :mismatches [..] :ledger-breaks [..] :ok? bool}`.

  `:ok?` requires five things: every scenario reached its expected phase FOR
  THE REASON IT NAMES, every scenario names a reason at all, no refusal wrote a
  record anyway, every ledger left behind verifies, and at least one refusal was
  demonstrated."
  []
  (let [results (mapv run-one scenarios)
        refusals (count (filter :refusal? results))
        mismatches (filterv (complement :match?) results)
        unreasoned (filterv (complement :reasoned?) results)
        ;; A refusal that still wrote a record is the worst outcome available
        ;; and would otherwise hide inside a matching phase.
        wrote-anyway (filterv #(and (:refusal? %) (:wrote? %)) results)
        ledger-breaks (filterv #(not (:ok? (:ledger-verify %))) results)]
    {:results results
     :refusals refusals
     :mismatches mismatches
     :unreasoned unreasoned
     :wrote-anyway wrote-anyway
     :ledger-breaks ledger-breaks
     :ok? (and (empty? mismatches)
               (empty? unreasoned)
               (empty? wrote-anyway)
               (empty? ledger-breaks)
               (pos? refusals))}))

(defn report
  "Human-readable run report. Pure: takes the result of `run`."
  [{:keys [results refusals mismatches unreasoned wrote-anyway ledger-breaks ok?]}]
  (str
   "technical-drafting.sim — governed scenario run\n"
   (apply str
          (for [r results]
            (str "  " (if (:match? r) "ok  " "BAD ")
                 (name (:name r))
                 " expect=" (name (:expect r))
                 " actual=" (name (:actual r))
                 (when (:refusal? r) " [refused]")
                 (cond
                   (not (:reasoned? r)) " NO-REASON-DECLARED"
                   (:because-ok? r) (if (:because r)
                                      (str " because=" (name (:because r)))
                                      " clean")
                   :else (str " WRONG-REASON want=" (pr-str (:because r))
                              " got=" (pr-str (:rules r))))
                 "\n")))
   "  scenarios=" (count results)
   " refusals=" refusals
   " mismatches=" (count mismatches)
   " unreasoned=" (count unreasoned)
   " wrote-anyway=" (count wrote-anyway)
   " ledger-breaks=" (count ledger-breaks)
   "\n"
   (cond
     (zero? refusals)
     "  REFUSING TO REPORT A PASS: the scenario table demonstrated no refusal.\n"
     ok? "  PASS\n"
     :else "  FAIL\n")))

#?(:clj
   (defn -main [& _]
     (let [r (run)]
       (print (report r))
       (flush)
       (System/exit (if (:ok? r) 0 1))))
   :cljs
   (defn -main [& _]
     (throw (ex-info "technical-drafting.sim/-main is :clj-only (process exit codes are a host concern); call `run` and inspect the result instead" {}))))
