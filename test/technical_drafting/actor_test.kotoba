(ns technical-drafting.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [technical-drafting.ledger :as led]
            [technical-drafting.store :as store]
            [technical-drafting.actor :as actor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :designer-id "designer-1" :name "Building Site Survey"})
    (store/register-drawing! st {:drawing-id "DWG-1" :project-id "proj-1" :rev "A" :status :draft})
    st))

(deftest ^:integration actor-run-clean-drawing-request
  (let [st (fresh-store)
        g (actor/build-graph {:store st})
        result (actor/run-request!
                g
                {:project-id "proj-1" :op :draft-technical-drawing :stake :low}
                {}
                "thread-1")]
    (is (some? result))
    (is (= 1 (count (store/records-of st "proj-1"))))
    (is (pos? (count (store/ledger st))))))

(deftest ^:integration actor-request-approval-on-discrepancy-flag
  ;; REWRITTEN. This used to submit a discrepancy flag with no `:drawing-id`.
  ;; With the drawing registry in place that is a hard block, so the test was
  ;; asserting escalation on a request that now holds -- and it failed on the
  ;; ledger assertion, because a hold writes an entry. Naming the drawing
  ;; restores what the test was actually about: an escalation interrupts
  ;; BEFORE committing anything.
  (let [st (fresh-store)
        g (actor/build-graph {:store st})
        result (actor/run-request!
                g
                {:project-id "proj-1" :op :flag-specification-discrepancy :stake :high
                 :drawing-id "DWG-1"}
                {}
                "thread-1")]
    (is (some? result))
    ;; Escalations interrupt before commit, so no record committed yet
    (is (= 0 (count (store/records-of st "proj-1"))))
    ;; Ledger is only populated on hard blocks or after approval+commit
    ;; At interrupt point, ledger is still empty (audit trail is in graph state)
    (is (= 0 (count (store/ledger st))))))

(deftest ^:integration actor-hard-block-on-finalize-attempt
  (let [st (fresh-store)
        g (actor/build-graph {:store st})
        result (actor/run-request!
                g
                {:project-id "proj-1" :op :finalize-drawing :stake :high}
                {}
                "thread-1")]
    (is (some? result))
    (is (= 0 (count (store/records-of st "proj-1"))))
    (is (pos? (count (store/ledger st))))))

(deftest ^:integration a-human-approved-write-is-distinguishable-from-an-automatic-one
  ;; Measured on the pre-change tree, it was not: both left
  ;; `{:disposition :commit :record ...}` with no field telling them apart,
  ;; which is the one question the approval interrupt exists to answer.
  (testing "automatic commit"
    (let [st (fresh-store)
          g (actor/build-graph {:store st})]
      (actor/run-request! g {:project-id "proj-1" :op :draft-technical-drawing :stake :low} {} "auto")
      (is (= :actor (:approved-by (first (store/ledger st)))))))
  (testing "commit reached by a human resuming the escalated thread"
    (let [st (fresh-store)
          g (actor/build-graph {:store st})]
      (actor/run-request! g {:project-id "proj-1" :op :flag-specification-discrepancy
                             :stake :high :drawing-id "DWG-1"} {} "escalated")
      (is (= 0 (count (store/ledger st))) "nothing written while awaiting a human")
      (actor/approve! g "escalated")
      (is (= 1 (count (store/records-of st "proj-1"))))
      (is (= :human (:approved-by (first (store/ledger st))))))))

(deftest ^:integration the-ledger-it-leaves-behind-verifies
  ;; Two runs on ONE store, which is what makes an unchained entry observable:
  ;; a single-entry ledger has `:ledger/prev` 0 legitimately, so a `ledger/entry`
  ;; that always hashed against 0 would still verify.
  (let [st (fresh-store)
        g (actor/build-graph {:store st})]
    (actor/run-request! g {:project-id "proj-1" :op :draft-technical-drawing :stake :low} {} "t1")
    (actor/run-request! g {:project-id "proj-1" :op :finalize-drawing :stake :high} {} "t2")
    (let [l (store/ledger st)]
      (is (= 2 (count l)))
      (is (:ok? (led/verify l)))
      (testing "and it detects an entry dropped out of the middle"
        (is (not (:ok? (led/verify [(first l) (second l) (second l)]))))))))

(deftest ^:integration a-refused-request-writes-no-record
  (doseq [[label request] {"reserved op"        {:project-id "proj-1" :op :seal-and-stamp}
                           "undeclared op"      {:project-id "proj-1" :op :rush-the-drawing}
                           "unregistered draw"  {:project-id "proj-1" :op :revise-drawing
                                                 :drawing-id "DWG-NOT-REAL"}}]
    (testing label
      (let [st (fresh-store)
            g (actor/build-graph {:store st})]
        (actor/run-request! g request {} (str "refuse-" label))
        (is (= 0 (count (store/records-of st "proj-1"))))
        (is (= 1 (count (store/ledger st))) "the refusal is recorded")
        (is (= :hold (:disposition (first (store/ledger st)))))))))
