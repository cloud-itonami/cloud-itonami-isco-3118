(ns technical-drafting.facts-test
  "The Governor's questions, asked directly. Each of these used to be an
  unnamed clause inside an inline `cond->`, reachable only by calling `check`
  and reading a violation list."
  (:require [clojure.test :refer [deftest is testing]]
            [technical-drafting.facts :as facts]
            [technical-drafting.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "P-1" :name "Awai clinic"})
    (store/register-project! st {:project-id "P-2" :name "Another office"})
    (store/register-drawing! st {:drawing-id "D-DRAFT" :project-id "P-1" :rev "A" :status :draft})
    (store/register-drawing! st {:drawing-id "D-ISSUED" :project-id "P-1" :rev "C" :status :issued-for-construction})
    (store/register-drawing! st {:drawing-id "D-OLD" :project-id "P-1" :rev "A" :status :superseded})
    (store/register-drawing! st {:drawing-id "D-OTHER" :project-id "P-2" :rev "A" :status :draft})
    st))

(deftest usable-confidence-agrees-across-hosts
  ;; The measured defect: `(< c 0.6)` on a non-number THREW under Clojure and
  ;; returned false under ClojureScript. `number?` is tested first, so the
  ;; ordering comparison never sees a non-number on either host.
  (testing "rejected without throwing"
    (doseq [c ["high" nil :high [] {} true]]
      (is (not (facts/usable-confidence? c)) (str "confidence=" (pr-str c)))))
  (testing "the floor has a ceiling"
    (is (not (facts/usable-confidence? 99.0)))
    (is (not (facts/usable-confidence? 1.0001)))
    (is (not (facts/usable-confidence? -5.0))))
  (testing "admitted"
    (doseq [c [0 0.0 0.2 0.6 0.95 1 1.0]]
      (is (facts/usable-confidence? c) (str "confidence=" (pr-str c))))))

(deftest low-confidence-answers-false-for-an-unusable-value
  ;; Deliberate: an unusable confidence must be routed as a hard block, not as
  ;; a merely-low one. If this returned true, an unreadable number would reach
  ;; a human for sign-off.
  (is (facts/low-confidence? 0.2))
  (is (not (facts/low-confidence? 0.6)) "the floor itself is usable")
  (is (not (facts/low-confidence? "high")))
  (is (not (facts/low-confidence? nil)))
  (is (not (facts/low-confidence? 99.0))))

(deftest identified-rejects-the-ids-that-let-provenance-answer-yes
  (is (facts/identified? "P-1"))
  (is (not (facts/identified? nil)))
  (is (not (facts/identified? "")))
  (is (not (facts/identified? "   ")))
  (is (not (facts/identified? :P-1)) "a keyword is not the string key the store uses"))

(deftest provenance-separates-naming-none-from-naming-an-unregistered-one
  (let [st (fresh-store)]
    (testing "a request that names no project"
      ;; Measured on the pre-change tree: a project registered as `{}` lands
      ;; under the nil key, so this lookup SUCCEEDED and the request was
      ;; admitted clean.
      (store/register-project! st {})
      (is (= [:no-project-id] (mapv :rule (facts/provenance-violations st {})))))
    (testing "a request that names an unregistered project"
      (is (= [:no-project] (mapv :rule (facts/provenance-violations st {:project-id "P-404"})))))
    (testing "a request that names a registered project"
      (is (empty? (facts/provenance-violations st {:project-id "P-1"}))))))

(deftest vocabulary-separates-undeclared-from-reserved
  (is (= [:undeclared-op] (mapv :rule (facts/vocabulary-violations {:op :rush-the-drawing}))))
  (is (= [:undeclared-op] (mapv :rule (facts/vocabulary-violations {:op nil}))))
  (is (= [:reserved-op] (mapv :rule (facts/vocabulary-violations {:op :seal-and-stamp}))))
  (is (empty? (facts/vocabulary-violations {:op :revise-drawing})))
  (testing "and reports WHICH op, not merely that there was one"
    (is (= :seal-and-stamp (:op (first (facts/vocabulary-violations {:op :seal-and-stamp})))))))

(deftest revision-violations-name-four-distinct-failures
  (let [st (fresh-store)
        rules (fn [proposal] (mapv :rule (facts/revision-violations st {:project-id "P-1"} proposal)))]
    (is (= [:revision-without-drawing] (rules {:op :revise-drawing})))
    (is (= [:revision-without-drawing] (rules {:op :revise-drawing :drawing-id ""})))
    (is (= [:unknown-drawing] (rules {:op :revise-drawing :drawing-id "D-NOPE"})))
    (is (= [:drawing-wrong-project] (rules {:op :revise-drawing :drawing-id "D-OTHER"})))
    (is (= [:superseded-drawing] (rules {:op :revise-drawing :drawing-id "D-OLD"})))
    (testing "an issued-for-construction drawing is NOT a violation"
      ;; It escalates instead. A hard block here would refuse the revision that
      ;; an error discovered on site actually requires.
      (is (empty? (rules {:op :revise-drawing :drawing-id "D-ISSUED"}))))
    (testing "a current drawing of this project is admissible"
      (is (empty? (rules {:op :revise-drawing :drawing-id "D-DRAFT"}))))))

(deftest the-ungated-op-is-gated-too
  ;; The mirror of the defect this namespace closes: without this, an op that
  ;; cites nothing by declaration could carry a `:drawing-id` past every check
  ;; in `revision-violations`, because those are only asked for citing ops.
  (is (= [:citation-without-citing-op]
         (mapv :rule (facts/citation-violations {:op :draft-technical-drawing :drawing-id "D-OTHER"}))))
  (is (empty? (facts/citation-violations {:op :draft-technical-drawing})))
  (is (empty? (facts/citation-violations {:op :revise-drawing :drawing-id "D-DRAFT"})))
  (testing "an undeclared op is not reported here"
    ;; It is already hard-blocked by `vocabulary-violations`, and reporting a
    ;; second, less specific reason would bury the first.
    (is (empty? (facts/citation-violations {:op :rush-the-drawing :drawing-id "D-OTHER"})))))

(deftest issued-for-construction-is-asked-only-of-citing-ops
  (let [st (fresh-store)]
    (is (facts/issued-for-construction? st {:op :revise-drawing :drawing-id "D-ISSUED"}))
    (is (not (facts/issued-for-construction? st {:op :revise-drawing :drawing-id "D-DRAFT"})))
    (is (not (facts/issued-for-construction? st {:op :revise-drawing :drawing-id "D-NOPE"})))
    (is (not (facts/issued-for-construction? st {:op :revise-drawing})))
    (is (not (facts/issued-for-construction? st {:op :draft-technical-drawing :drawing-id "D-ISSUED"}))
        "a non-citing op does not bind to the drawing it should not be carrying")))

(deftest actuation-and-effect
  (is (empty? (facts/actuation-violations {:effect :propose})))
  (is (= [:no-actuation] (mapv :rule (facts/actuation-violations {:effect :write}))))
  (is (= [:no-actuation] (mapv :rule (facts/actuation-violations {})))
      "an absent effect is not an implicit :propose"))
