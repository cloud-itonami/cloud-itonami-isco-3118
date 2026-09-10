(ns technical-drafting.operation-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [technical-drafting.operation :as op]))

(deftest supported-and-reserved-are-disjoint
  ;; If an op were in both maps the governor's answer would depend on which
  ;; predicate it happened to ask first, which is exactly the class of defect
  ;; declaring the vocabulary was meant to remove.
  (is (empty? (set/intersection (set (keys op/supported))
                                        (set (keys op/reserved))))))

(deftest every-reserved-op-says-why
  ;; A refusal that cannot explain itself is only marginally better than no
  ;; refusal: the operator cannot tell an authority boundary from a bug.
  (doseq [[o m] op/reserved]
    (testing (str o)
      (is (string? (:reason m)))
      (is (seq (:reason m))))))

(deftest every-supported-op-declares-both-properties
  ;; `escalates?` and `cites-drawing?` fall back to false via `get-in`, so an
  ;; op that simply forgot to declare them would be silently ungated.
  (doseq [[o m] op/supported]
    (testing (str o)
      (is (contains? m :escalates?))
      (is (contains? m :cites-drawing?))
      (is (string? (:summary m))))))

(deftest undeclared-ops-are-not-declared
  (is (not (op/declared? :rush-the-drawing)))
  (is (not (op/declared? nil)))
  (is (not (op/declared? "revise-drawing")) "a string is not the keyword it looks like"))

(deftest the-ops-measured-walking-through-the-old-denylist-are-reserved
  ;; These three were admitted as clean verdicts on the pre-change tree.
  ;; `:approve-for-construction` is the one that matters most: it is the same
  ;; authority as `:certify-construction-ready`, which the governor's docstring
  ;; declared a permanent hard block.
  (doseq [o [:approve-for-construction :seal-and-stamp :delete-project-archive]]
    (testing (str o)
      (is (op/reserved? o))
      (is (not (op/supported? o))))))

(deftest label-does-not-throw-on-nil
  ;; `(name nil)` throws, and the pre-change advisor called `name` on the
  ;; request's op while building its rationale -- so `{:op nil}` killed the run
  ;; before the governor was consulted. A crash leaves no verdict, no hold and
  ;; no ledger entry.
  (is (= "nil" (op/label nil)))
  (is (= "revise-drawing" (op/label :revise-drawing)))
  (is (= "5" (op/label 5))))

(deftest citing-ops-are-the-ones-that-name-an-existing-drawing
  (is (op/cites-drawing? :revise-drawing))
  (is (op/cites-drawing? :flag-specification-discrepancy))
  (is (op/cites-drawing? :request-designer-review))
  (is (not (op/cites-drawing? :draft-technical-drawing))
      "drafting a NEW drawing creates one rather than citing one")
  (is (not (op/cites-drawing? :seal-and-stamp))
      "reserved ops are hard-blocked before this is consulted"))
