(ns technical-drafting.governor-test
  "The governor's verdicts, asserted on the pure `check` function.

  Four tests in this namespace were rewritten rather than adjusted when the
  vocabulary was closed and drawing provenance was added. Each is marked below
  with what it used to assert and why that assertion was wrong, because a test
  that is quietly edited to match new behaviour is indistinguishable from one
  that was encoding a defect and got covered up."
  (:require [clojure.test :refer [deftest is testing]]
            [technical-drafting.store :as store]
            [technical-drafting.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :designer-id "designer-1" :name "Building Site Survey"})
    (store/register-drawing! st {:drawing-id "DWG-1" :project-id "proj-1" :rev "A" :status :draft})
    (store/register-drawing! st {:drawing-id "DWG-ISSUED" :project-id "proj-1" :rev "C" :status :issued-for-construction})
    st))

(deftest ok-on-clean-technical-drawing
  (let [st (fresh-store)
        proposal {:op :draft-technical-drawing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-on-drawing-revision
  ;; REWRITTEN. This used to submit `{:op :revise-drawing}` with no
  ;; `:drawing-id` and assert `:ok? true` -- a revision of nothing at all was
  ;; the repo's pinned example of an admissible revision. It now names the
  ;; drawing it revises, which is what makes it admissible.
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.85 :stake :medium
                  :drawing-id "DWG-1"}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-revision-naming-no-drawing
  ;; The half the rewritten test above gave up: the shape it used to admit.
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.85 :stake :medium}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :revision-without-drawing (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-project
  (let [st (fresh-store)
        proposal {:op :draft-technical-drawing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:project-id "no-such-project"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-project (:rule %)) (:violations v)))))

(deftest hard-on-request-naming-no-project
  ;; Measured on the pre-change tree: a project registered as `{}` lands under
  ;; the nil key, and a request with no `:project-id` found it and was admitted
  ;; clean. Provenance answered yes for a project that is not a project.
  (let [st (fresh-store)
        _ (store/register-project! st {})
        proposal {:op :draft-technical-drawing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-project-id (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-technical-drawing :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-finalize-drawing
  ;; REWRITTEN. The rule keyword moved from `:no-finalization` to
  ;; `:reserved-op` when the two hard-coded ops became a declared authority
  ;; boundary. The violation now carries `:op`, so this pins WHICH boundary was
  ;; crossed -- strictly more than the keyword it replaced, which is the bar a
  ;; rename has to clear.
  (let [st (fresh-store)
        proposal {:op :finalize-drawing :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(and (= :reserved-op (:rule %)) (= :finalize-drawing (:op %))) (:violations v)))))

(deftest hard-on-attempt-to-certify-construction-ready
  ;; REWRITTEN, same rename as above.
  (let [st (fresh-store)
        proposal {:op :certify-construction-ready :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(and (= :reserved-op (:rule %)) (= :certify-construction-ready (:op %))) (:violations v)))))

(deftest hard-on-the-same-authority-spelled-differently
  ;; The defect the two tests above could not see. Measured on the pre-change
  ;; tree, all three of these were admitted as CLEAN verdicts -- `:ok? true`,
  ;; no escalation, empty violation list -- while the governor's docstring
  ;; declared certifying a drawing a permanent hard block. A denylist over an
  ;; open vocabulary enumerates spellings, not authority.
  (let [st (fresh-store)]
    (doseq [op [:approve-for-construction :seal-and-stamp :delete-project-archive]]
      (testing (str op)
        (let [v (governor/check {:project-id "proj-1"} {}
                                {:op op :effect :propose :confidence 0.9 :stake :high} st)]
          (is (:hard? v))
          (is (not (:escalate? v)) "an authority boundary is never a question for a human")
          (is (some #(and (= :reserved-op (:rule %)) (= op (:op %))) (:violations v))))))))

(deftest hard-on-undeclared-op
  (let [st (fresh-store)]
    (doseq [op [:rush-the-drawing nil]]
      (testing (str "op=" (pr-str op))
        (let [v (governor/check {:project-id "proj-1"} {}
                                {:op op :effect :propose :confidence 0.9} st)]
          (is (:hard? v))
          (is (some #(= :undeclared-op (:rule %)) (:violations v))))))))

(deftest hard-on-unusable-confidence
  ;; Measured on the pre-change tree, `{:confidence "high"}` THREW a
  ;; ClassCastException under Clojure and returned `{:ok? true}` under
  ;; ClojureScript -- same .cljc file, same input, opposite verdicts. And
  ;; `99.0` was admitted clean on both, because the floor had no ceiling.
  (let [st (fresh-store)]
    (doseq [c ["high" 99.0 -5.0 nil]]
      (testing (str "confidence=" (pr-str c))
        (let [v (governor/check {:project-id "proj-1"} {}
                                {:op :draft-technical-drawing :effect :propose :confidence c} st)]
          (is (:hard? v))
          (is (some #(= :unusable-confidence (:rule %)) (:violations v))))))))

(deftest escalates-on-specification-discrepancy-flag
  ;; REWRITTEN. This used to omit `:drawing-id`. A specification discrepancy is
  ;; a disagreement BETWEEN a drawing and a specification, so the drawing is
  ;; part of the claim; the pre-change store had no drawings to name.
  (let [st (fresh-store)
        proposal {:op :flag-specification-discrepancy :effect :propose :confidence 0.9 :stake :high
                  :drawing-id "DWG-1"}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))
    (is (some #{:escalating-op} (:escalation-reasons v)))))

(deftest escalates-on-low-confidence
  ;; REWRITTEN, same reason: it used to omit `:drawing-id` on a revision.
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.2 :stake :low
                  :drawing-id "DWG-1"}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))
    (is (some #{:low-confidence} (:escalation-reasons v)))))

(deftest escalates-on-revising-an-issued-drawing
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.9 :stake :low
                  :drawing-id "DWG-ISSUED"}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))
    (is (some #{:issued-for-construction} (:escalation-reasons v)))))

(deftest escalation-reasons-are-plural
  ;; A revision of an issued drawing submitted at low confidence needs a human
  ;; for two independent reasons, and a reviewer should be shown both. A
  ;; single-reason field would silently drop one.
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.2 :stake :low
                  :drawing-id "DWG-ISSUED"}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (= #{:low-confidence :issued-for-construction} (set (:escalation-reasons v))))))

(deftest hard-beats-escalation
  ;; An escalating op that is ALSO hard-blocked must hold, not escalate.
  (let [st (fresh-store)
        proposal {:op :flag-specification-discrepancy :effect :propose :confidence 0.9
                  :drawing-id "DWG-NOT-REAL"}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (not (:escalate? v)))
    (is (empty? (:escalation-reasons v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:project-id "proj-1" :op :draft-technical-drawing})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "proj-1"))))
    (is (= 1 (count (store/ledger st))))))
