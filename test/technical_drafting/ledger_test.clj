(ns technical-drafting.ledger-test
  (:require [clojure.test :refer [deftest is]]
            [technical-drafting.ledger :as led]))

(deftest an-empty-ledger-verifies
  (is (:ok? (led/verify [])))
  (is (= 0 (:length (led/verify [])))))

(deftest a-chain-built-by-append-verifies
  (let [l (-> [] (led/append {:disposition :commit :n 1})
              (led/append {:disposition :hold :n 2})
              (led/append {:disposition :commit :n 3}))]
    (is (= 3 (count l)))
    (is (:ok? (led/verify l)))
    (is (= [0 1 2] (mapv :ledger/seq l)))))

(deftest a-tampered-entry-is-detected
  (let [l (-> [] (led/append {:disposition :commit :n 1})
              (led/append {:disposition :commit :n 2}))
        tampered (assoc-in l [0 :n] 99)
        v (led/verify tampered)]
    (is (not (:ok? v)))
    (is (= 0 (:broken-at v)))
    (is (= :hash-mismatch (:reason v)))))

(deftest a-dropped-entry-is-detected
  (let [l (-> [] (led/append {:n 1}) (led/append {:n 2}) (led/append {:n 3}))
        v (led/verify [(nth l 0) (nth l 2)])]
    (is (not (:ok? v)))
    (is (= :seq-mismatch (:reason v)))))

(deftest a-reordered-ledger-is-detected
  (let [l (-> [] (led/append {:n 1}) (led/append {:n 2}))
        v (led/verify [(second l) (first l)])]
    (is (not (:ok? v)))
    (is (= :seq-mismatch (:reason v)))))

(deftest entries-are-actually-chained
  ;; The mutation this pins: `entry` always hashing against prev 0. A
  ;; single-entry ledger has prev 0 legitimately, so it takes two entries to
  ;; make the break observable -- which is why `technical-drafting.sim` cannot
  ;; catch it and this test can.
  (let [l (-> [] (led/append {:n 1}) (led/append {:n 2}))]
    (is (= 0 (:ledger/prev (first l))))
    (is (= (:ledger/hash (first l)) (:ledger/prev (second l))))
    (is (not= (:ledger/prev (second l)) 0))))

(deftest truncation-is-not-detected-and-that-is-stated
  ;; A chain cannot detect entries it never saw. `verify` claims only what it
  ;; can show; detecting truncation needs an external anchor this in-memory
  ;; store does not have. Asserted so the limit is a decision rather than a
  ;; surprise.
  (let [l (-> [] (led/append {:n 1}) (led/append {:n 2}) (led/append {:n 3}))]
    (is (:ok? (led/verify (vec (take 2 l)))))))

(deftest a-commit-entry-records-who-approved-it
  (let [auto (led/commit-entry {:op :draft-technical-drawing} :actor)
        human (led/commit-entry {:op :flag-specification-discrepancy} :human)]
    (is (= :actor (:approved-by auto)))
    (is (= :human (:approved-by human)))
    (is (not= auto human) "the pre-change entries for these two cases were identical")))

(deftest a-hold-entry-carries-the-violations
  (let [e (led/hold-entry {:hard? true :violations [{:rule :reserved-op}]})]
    (is (= :hold (:disposition e)))
    (is (= :none (:approved-by e)))
    (is (= [{:rule :reserved-op}] (:violations (:verdict e))))))

(deftest the-hash-is-deterministic
  ;; Same input, same number, every run -- and by construction the same number
  ;; under ClojureScript, which is why clojure.core/hash was not used.
  (is (= (led/chain-hash 0 {:a 1 :b "x"}) (led/chain-hash 0 {:a 1 :b "x"})))
  (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 1 {:a 1})))
  (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 0 {:a 2}))))
