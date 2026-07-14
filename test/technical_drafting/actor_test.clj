(ns technical-drafting.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [technical-drafting.store :as store]
            [technical-drafting.actor :as actor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :designer-id "designer-1" :name "Building Site Survey"})
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
  (let [st (fresh-store)
        g (actor/build-graph {:store st})
        result (actor/run-request!
                g
                {:project-id "proj-1" :op :flag-specification-discrepancy :stake :high}
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
