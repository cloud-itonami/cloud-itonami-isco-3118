(ns technical-drafting.facts
  "Shape predicates and violation functions for the ISCO-08 3118 technical
  draughtsperson actor: the questions the Governor asks, each one named, pure,
  and testable without building a graph.

  Runtime: portable `.cljc`. Every predicate here is written so that Clojure
  and ClojureScript agree on the answer, which is not automatic — see
  `usable-confidence?`.

  Why this namespace exists. The Governor's checks were an inline `cond->` over
  a proposal map, so each one could only be exercised by calling `check` and
  reading a violation list, and none of them had a name a test could hold.
  Three separate defects lived in that shape, all measured on the pre-change
  tree against the registered project `P-1`:

  1. THE CONFIDENCE COMPARISON DISAGREED ACROSS HOSTS. `(< conf floor)` on a
     non-numeric confidence is a `ClassCastException` under Clojure and
     `false` under ClojureScript, where it compiles to a JavaScript
     string-versus-number test. Same `.cljc` file, same input, opposite
     outcomes — both halves measured:

         clj:  {:confidence \"high\"} => THREW ClassCastException
         cljs: {:confidence \"high\"} => {:ok? true :hard? false :escalate? false}

     On the host this actor is portable to, an unreadable confidence bought a
     clean admission. On the host it is tested on, it took the whole run down
     without producing a verdict.

  2. THE FLOOR HAD NO CEILING. A confidence of `99.0` was admitted clean on
     BOTH hosts, because the only test was `(< conf 0.6)`:

         {:op :revise-drawing :confidence 99.0} => {:ok? true :violations []}

     An advisor that reports a confidence outside `[0,1]` is not a confident
     advisor; it is one whose output does not mean what the field says it
     means. `usable-confidence?` tests `number?` FIRST, so the ordering
     comparison never sees a non-number on either host, and bounds the value on
     both sides. This is why the check is a named predicate and not an inline
     comparison: the bug was in the comparison's *shape*, and a shape is
     exactly what an inline expression does not let you name or test.

  3. THE EMPTY-MAP PROJECT ANSWERED FOR A REQUEST WITH NO PROJECT ID. The
     governor's provenance test was `(nil? (store/project store (:project-id
     request)))`. A project registered as `{}` lands in the store under the
     `nil` key, and a request carrying no `:project-id` looks it up and finds
     it:

         request {} (no :project-id), store containing {} => {:ok? true :violations []}

     Provenance answered `yes` for a project that is not a project, to a
     request that never named one. `identified?` is the missing question:
     asking whether an id is usable BEFORE asking what it resolves to.

  The revision basis checks (`revision-violations`) are new capability rather
  than a repair: the pre-change store held no drawings, so `:revise-drawing`
  could cite any string and there was nothing to check it against. That is
  recorded here honestly — an unasked question and a failing check are not the
  same defect, and the sim table marks only the measured ones `pre-change`."
  (:require [clojure.string :as str]
            [technical-drafting.operation :as op]
            [technical-drafting.store :as store]))

(def confidence-floor
  "Below this, a proposal escalates to a human. It is a floor on a value that
  has already been established to be usable — `usable-confidence?` runs first,
  and an unusable confidence is a hard block rather than a low one, because
  escalating it would ask a human to sign off on a number that does not mean
  anything."
  0.6)

(defn identified?
  "True for a usable identifier: a non-blank string. Used for project and
  drawing ids. A `nil` id is what let the empty-map project land in the store
  under the `nil` key and then answer a request that named no project at all;
  a blank string is the same hole with a different spelling."
  [id]
  (and (string? id) (not (str/blank? id))))

(defn usable-confidence?
  "True when `c` is a number in `[0,1]`.

  `number?` is tested FIRST and the ordering comparisons are only reached for
  numbers, so Clojure and ClojureScript cannot disagree: the pre-change
  `(< c 0.6)` threw on one host and returned false on the other for the same
  non-numeric input. Both bounds are checked, because a floor alone let `99.0`
  buy its way out of escalation."
  [c]
  (and (number? c) (<= 0 c) (<= c 1)))

(defn low-confidence?
  "True when a USABLE confidence is below the floor. Callers must establish
  usability first; this predicate deliberately answers false for an unusable
  value rather than guessing, so that an unusable confidence is never routed as
  a merely-low one."
  [c]
  (and (usable-confidence? c) (< c confidence-floor)))

(defn registered-project
  "The project record for a request, or nil. Returns nil for an unusable
  project id WITHOUT consulting the store, which is the half the pre-change
  check was missing."
  [store request]
  (when (identified? (:project-id request))
    (store/project store (:project-id request))))

(defn provenance-violations
  "Violations about whether this request names a project that exists.

  Ordered so the more specific fact is reported: a request with no usable
  project id is `:no-project-id`, not `:no-project` — the difference is whether
  the caller failed to name a project or named one that is not registered, and
  a reviewer needs to be told which."
  [store request]
  (cond
    (not (identified? (:project-id request)))
    [{:rule :no-project-id
      :detail (str "request が project を名指していない（:project-id=" (pr-str (:project-id request)) "）")}]

    (nil? (store/project store (:project-id request)))
    [{:rule :no-project
      :detail (str "未登録 project: " (pr-str (:project-id request)))}]

    :else []))

(defn vocabulary-violations
  "Violations about the operation itself.

  An undeclared op and a reserved op are different failures and are reported as
  different rules. Undeclared means the vocabulary does not contain the word;
  reserved means it does, and the word names authority this actor does not
  hold. Both are hard — but a reviewer reading `:reserved-op` learns that
  someone asked the actor to certify a drawing, and a reviewer reading
  `:undeclared-op` learns that someone asked it for something nobody has
  defined."
  [proposal]
  (let [o (:op proposal)]
    (cond
      (op/reserved? o)
      ;; `:op` is carried so a caller can pin WHICH authority boundary was
      ;; crossed, not merely that one was. The pre-change governor named
      ;; `:no-finalization` for its two hard-coded ops, and a rule keyword that
      ;; says only `:reserved-op` would be strictly less informative than what
      ;; it replaced -- a repair is not allowed to cost the reader a distinction.
      [{:rule :reserved-op
        :op o
        :detail (str (op/label o) ": " (op/reserved-reason o))}]

      (not (op/supported? o))
      [{:rule :undeclared-op
        :op o
        :detail (str "宣言されていない op: " (op/label o)
                     "（許可されているのは "
                     (str/join ", " (sort (map name (keys op/supported))))
                     "）")}]

      :else [])))

(defn actuation-violations
  "The advisor proposes; it never writes. `:effect` must be `:propose`."
  [proposal]
  (if (= :propose (:effect proposal))
    []
    [{:rule :no-actuation
      :detail (str "effect は :propose のみ許可（直接書込禁止）。受け取った値: " (pr-str (:effect proposal)))}]))

(defn confidence-violations
  "An unusable confidence is a HARD block, not an escalation. Escalating it
  would put a number in front of a human that does not mean anything, and ask
  them to sign off on it."
  [proposal]
  (if (usable-confidence? (:confidence proposal))
    []
    [{:rule :unusable-confidence
      :detail (str ":confidence は 0..1 の数でなければならない。受け取った値: "
                   (pr-str (:confidence proposal)))}]))

(defn revision-violations
  "Violations about the drawing a citing operation names.

  Only consulted for ops whose `:cites-drawing?` is true. Four distinct
  failures, each named so the refusal explains itself:

    :revision-without-drawing — a citing op that named no drawing
    :unknown-drawing          — a drawing id nothing registered
    :drawing-wrong-project    — a drawing registered to a DIFFERENT project
    :superseded-drawing       — a drawing already replaced by a later revision

  `:issued-for-construction` is deliberately NOT here. Revising a drawing that
  is already on site is legitimate draughting work; what it is not is a write
  that happens without a human. It is returned by `issued-for-construction?`
  and routed to escalation by the governor, because a hard block would refuse
  the revision that a discovered error on site actually requires."
  [store request proposal]
  (let [drawing-id (:drawing-id proposal)]
    (cond
      (not (identified? drawing-id))
      [{:rule :revision-without-drawing
        :detail (str (op/label (:op proposal)) " は既存図面を名指す必要がある（:drawing-id="
                     (pr-str drawing-id) "）")}]

      (nil? (store/drawing store drawing-id))
      [{:rule :unknown-drawing
        :detail (str "未登録の図面: " (pr-str drawing-id))}]

      (not= (:project-id request) (:project-id (store/drawing store drawing-id)))
      [{:rule :drawing-wrong-project
        :detail (str "図面 " (pr-str drawing-id) " は project "
                     (pr-str (:project-id (store/drawing store drawing-id)))
                     " のもので、request の project " (pr-str (:project-id request)) " のものではない")}]

      (= :superseded (:status (store/drawing store drawing-id)))
      [{:rule :superseded-drawing
        :detail (str "図面 " (pr-str drawing-id) " は既に後続改訂に置き換えられている。"
                     "改訂は現行版に対して行う")}]

      :else [])))

(defn citation-violations
  "The other half of the citation gate: a NON-citing op that names a drawing.

  Without this, closing the gate on `:revise-drawing` would leave
  `:draft-technical-drawing` as the new hole — an op that cites nothing by
  declaration would be able to carry a `:drawing-id` past every check in
  `revision-violations`, because those are only consulted for citing ops. This
  is the mirror of the defect this namespace exists to close, and it is written
  now rather than discovered later."
  [proposal]
  (if (and (op/supported? (:op proposal))
           (not (op/cites-drawing? (:op proposal)))
           (some? (:drawing-id proposal)))
    [{:rule :citation-without-citing-op
      :detail (str (op/label (:op proposal)) " は図面を名指さない op なのに :drawing-id "
                   (pr-str (:drawing-id proposal)) " を運んでいる")}]
    []))

(defn issued-for-construction?
  "True when a citing proposal names a drawing that is currently issued for
  construction. Not a violation — an escalation trigger. People are building
  from that drawing, so the revision is exactly the work a draughtsperson does
  and exactly the write that must not happen without the licensed designer."
  [store proposal]
  (boolean
   (and (op/cites-drawing? (:op proposal))
        (identified? (:drawing-id proposal))
        (= :issued-for-construction (:status (store/drawing store (:drawing-id proposal)))))))
