(ns technical-drafting.operation
  "The closed vocabulary of operations the ISCO-08 3118 technical
  draughtsperson actor may propose.

  Runtime: portable `.cljc` (pure data + pure predicates, no host interop).

  Why this namespace exists. Before it, the operation vocabulary lived in two
  places that could not disagree loudly: the Advisor's docstring, which names
  four operations in prose, and the Governor's private `hard-violations`, which
  named exactly two — `:finalize-drawing` and `:certify-construction-ready`.
  That made the Governor a *denylist*: it bound two named ops and admitted
  everything else. Measured on the pre-change tree, against the registered
  project `P-1`:

      {:op :seal-and-stamp :effect :propose :confidence 0.95}
      => {:ok? true :hard? false :escalate? false :violations []}

      {:op :approve-for-construction :effect :propose :confidence 0.95}
      => {:ok? true :hard? false :escalate? false :violations []}

      {:op :delete-project-archive :effect :propose :confidence 0.95}
      => {:ok? true :hard? false :escalate? false :violations []}

      {:op nil :effect :propose :confidence 0.95}
      => {:ok? true :hard? false :escalate? false :violations []}

  All admitted, and admitted as *clean* verdicts: no escalation, no human, and
  an empty violation list to show a reviewer.

  Read the second one again. The Governor's own docstring declares as a HARD
  invariant that `any attempt to finalize/certify a drawing as
  construction-ready` is a permanent block, because that is the licensed
  designer's or engineer's exclusive professional responsibility. The
  invariant was implemented as two keyword equality tests, so
  `:approve-for-construction` — the same authority, the same act, a different
  word for it — walked through clean. A denylist over an open vocabulary does
  not enforce an authority boundary; it enumerates the spellings someone
  happened to think of.

  An actor whose operation set is open cannot be governed, because the governor
  is answering a question about a vocabulary nobody declared. So the vocabulary
  is declared here, once, as an allowlist, and `technical-drafting.governor`
  refuses anything outside it.

  Two disjoint maps:

  * `supported` — what the actor may propose. `:escalates?` and
    `:cites-drawing?` are properties of the operation, not of the governor's
    mood, so they live beside it.
  * `reserved` — operations naming authority this cognitive actor does not
    hold: sealing, certifying or approving a drawing for construction, and
    destroying the record. These are *declared* rather than merely absent so
    the refusal can say why. An undeclared op is a vocabulary error; a reserved
    op is an authority boundary. Conflating them would let a future edit
    `supported`-list one of them by accident."
  (:require [clojure.string :as str]))

(def supported
  "Operations the actor may propose.

  `:escalates?` true means human sign-off is required regardless of advisor
  confidence. `:cites-drawing?` true means the proposal binds to a REGISTERED
  drawing of this project, and must therefore satisfy the revision basis
  checks in `technical-drafting.facts/revision-violations`.

  Only the four operations the Advisor's docstring has always claimed are
  listed. The vocabulary is deliberately not widened here: this namespace
  exists to close an opening, and adding ops would be the opposite of that."
  {:draft-technical-drawing
   {:escalates?     false
    :cites-drawing? false
    :summary "draft a new technical drawing for the project; cites no existing drawing"}

   :revise-drawing
   {:escalates?     false
    :cites-drawing? true
    :summary "prepare a revision of a registered drawing of this project"}

   :flag-specification-discrepancy
   {:escalates?     true
    :cites-drawing? true
    :summary "flag a discrepancy between drawing and specification (always human sign-off)"}

   :request-designer-review
   {:escalates?     false
    :cites-drawing? true
    :summary "ask the licensed designer to review a registered drawing"}})

(def reserved
  "Operations reserved to someone this actor is not. Naming one in a proposal
  is a permanent hard block, never an escalation: escalation would imply a
  human could approve the *actor* doing it, and neither the project's designer
  nor the client can delegate a professional seal, a construction-ready
  certification, or the destruction of a project record to a remote cognitive
  actor.

  The first two were the Governor's entire denylist. The next three were
  measured walking through it clean on the pre-change tree — see this
  namespace's docstring. They are the same authority under different words,
  which is precisely why the boundary has to be declared as data rather than
  matched as a spelling.

  This is the machine-readable form of the scope sentence the README and the
  Advisor docstring have carried since the repo was created — the advisor only
  proposes. Prose in a docstring does not refuse anything."
  {:finalize-drawing
   {:reason "図面の確定は有資格の設計者・技術者の専属責任であって、製図支援の結論ではない"}

   :certify-construction-ready
   {:reason "施工可の証明は有資格者の職業責任であり、遠隔の認知アクタに委任できない"}

   :approve-for-construction
   {:reason "施工承認は :certify-construction-ready と同じ権限境界を別の語で言ったもの（計測 2026-09-10: 素通りしていた）"}

   :seal-and-stamp
   {:reason "職印・押印は有資格者本人の行為であって、代行させられるものではない（計測 2026-09-10: 素通りしていた）"}

   :delete-project-archive
   {:reason "設計記録の破棄は、事後の検証が依拠する証拠そのものを消すこと（計測 2026-09-10: 素通りしていた）"}

   :disable-audit-ledger
   {:reason "監査台帳を外すことは、この actor 自身の governance が依拠している証拠を消すこと"}})

(defn supported? [op] (contains? supported op))
(defn reserved? [op] (contains? reserved op))

(defn declared?
  "True if `op` is named anywhere in this vocabulary. An op that is neither
  supported nor reserved is undeclared — the governor refuses it."
  [op]
  (or (supported? op) (reserved? op)))

(defn escalates?
  "True if the operation itself always requires human sign-off. Undeclared and
  reserved ops are hard-blocked before this is consulted, so a false here is
  not an admission."
  [op]
  (boolean (get-in supported [op :escalates?])))

(defn cites-drawing?
  "True if the operation binds to a registered drawing of this project and must
  therefore satisfy the revision basis checks. False for undeclared and
  reserved ops, which the governor hard-blocks first, and false for
  `:draft-technical-drawing`, which creates a drawing rather than citing one —
  `technical-drafting.facts/citation-violations` enforces that other half, so
  closing the gate on citing ops does not simply move the hole to the op that
  has no gate."
  [op]
  (boolean (get-in supported [op :cites-drawing?])))

(defn reserved-reason [op] (get-in reserved [op :reason]))

(defn label
  "A printable name for an op, including `nil` and non-keyword values. Exists
  because `(name nil)` throws, and the pre-change advisor called `name` on the
  request's op while building its rationale — so `{:op nil}` crashed before
  the governor was ever consulted. A crash is not a refusal: it leaves no
  verdict, no hold, and no ledger entry."
  [op]
  (cond
    (nil? op)     "nil"
    (keyword? op) (name op)
    (string? op)  op
    :else         (str op)))

(defn descriptor
  "The declared entry for `op`, or nil. Used by the governor's refusal message
  so an undeclared op is reported with the vocabulary it missed rather than
  with a bare false."
  [op]
  (or (get supported op) (get reserved op)))

(def vocabulary-summary
  "One line per declared op, for operators and for the sim report."
  (str "supported: " (str/join ", " (sort (map name (keys supported)))) "\n"
       "reserved:  " (str/join ", " (sort (map name (keys reserved))))))
