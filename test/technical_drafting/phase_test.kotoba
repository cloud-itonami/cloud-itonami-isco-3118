(ns technical-drafting.phase-test
  (:require [clojure.test :refer [deftest is]]
            [technical-drafting.phase :as phase]))

(deftest hard-is-checked-before-escalate
  ;; This is the mutation `technical-drafting.sim` CANNOT catch: the governor
  ;; never emits a verdict carrying both flags, so reversing the two clauses of
  ;; `of-verdict` leaves the whole scenario table green. It is covered here,
  ;; over the pure function, because the guard exists for any caller building a
  ;; verdict by hand and for a future governor that stops zeroing the flag.
  (is (= :hold (phase/of-verdict {:hard? true :escalate? true}))
      "a proposal that is both hard-blocked and escalating must hold -- escalating it would ask a human to approve something they cannot authorise")
  (is (= :hold (phase/of-verdict {:hard? true :escalate? false})))
  (is (= :request-approval (phase/of-verdict {:hard? false :escalate? true})))
  (is (= :commit (phase/of-verdict {:hard? false :escalate? false})))
  (is (= :commit (phase/of-verdict {})) "an empty verdict is a clean one"))

(deftest only-commit-writes
  (is (phase/writes? :commit))
  (is (not (phase/writes? :hold)))
  (is (not (phase/writes? :request-approval))
      "an escalation has not written anything yet -- that is the whole point of the interrupt"))

(deftest both-non-writing-phases-count-as-refusals
  (is (phase/refusal? :hold))
  (is (phase/refusal? :request-approval))
  (is (not (phase/refusal? :commit))))

(deftest an-unknown-phase-does-not-write
  ;; `writes?` reads through `get-in`, so an unknown phase answers false. That
  ;; is the safe direction, and asserting it keeps a future edit from making
  ;; the default permissive.
  (is (not (phase/writes? :no-phase)))
  (is (not (phase/writes? nil))))

(deftest approved-commit-is-the-one-reached-from-an-escalation
  (is (phase/approved-commit? :request-approval))
  (is (not (phase/approved-commit? :commit)))
  (is (not (phase/approved-commit? :hold))))
