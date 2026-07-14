(ns technical-drafting.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [technical-drafting.store :as store]
            [technical-drafting.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :designer-id "designer-1" :name "Building Site Survey"})
    st))

(deftest ok-on-clean-technical-drawing
  (let [st (fresh-store)
        proposal {:op :draft-technical-drawing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-on-drawing-revision
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.85 :stake :medium}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-project
  (let [st (fresh-store)
        proposal {:op :draft-technical-drawing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:project-id "no-such-project"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-project (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-technical-drawing :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-finalize-drawing
  (let [st (fresh-store)
        proposal {:op :finalize-drawing :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-finalization (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-certify-construction-ready
  (let [st (fresh-store)
        proposal {:op :certify-construction-ready :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-finalization (:rule %)) (:violations v)))))

(deftest escalates-on-specification-discrepancy-flag
  (let [st (fresh-store)
        proposal {:op :flag-specification-discrepancy :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :revise-drawing :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:project-id "proj-1" :op :draft-technical-drawing})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "proj-1"))))
    (is (= 1 (count (store/ledger st))))))
