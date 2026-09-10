(ns technical-drafting.store
  "SSoT for the ISCO-08 3118 technical draughtsperson actor. Store is a
  protocol injected into the `technical-drafting.actor` StateGraph — `MemStore`
  is the default, deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or governor
  (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    project  — a registered design project (:project-id, :designer-id, :name)
    drawing  — a registered technical drawing OF a project
               (:drawing-id, :project-id, :rev, :status)
    record   — a committed technical drafting record under a project
               (drawing draft, drawing revision, specification note,
               design discrepancy flag, designer review proposal) — written ONLY via
               commit-record!, never mutated in place
    ledger   — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold). Entries are
               hash-chained by `technical-drafting.ledger`; append through
               `append-ledger!` only, which is where the chain is extended.

  Why drawings are registered here. The governor's job is to refuse a proposal
  that cites design provenance the project does not have, but before this the
  store held no drawings at all — so `:revise-drawing` could name any string
  and there was nothing to check it against. A citation that cannot be checked
  is not a weak check; it is an unasked question. `drawing-status` values are
  the three a drafting office actually distinguishes:

    :draft                    — in preparation, revisable
    :issued-for-construction  — on site; people are building from it. A
                                revision is legitimate work but is never
                                silently committed (see facts/revision-violations)
    :superseded               — replaced by a later revision. Citing it is a
                                provenance error, not a judgement call.")

(def drawing-statuses
  "The closed set of drawing statuses. Declared as data so that an unknown
  status is a refusable fact rather than a value that silently matches no
  branch of a `case`."
  #{:draft :issued-for-construction :superseded})

(defprotocol Store
  (project [s project-id])
  (drawing [s drawing-id])
  (drawings-of [s project-id])
  (records-of [s project-id])
  (ledger [s])
  (register-project! [s project])
  (register-drawing! [s drawing])
  (commit-record! [s record])
  (append-ledger! [s entry]))

(defrecord MemStore [a]
  Store
  (project [_ project-id] (get-in @a [:projects project-id]))
  (drawing [_ drawing-id] (get-in @a [:drawings drawing-id]))
  (drawings-of [_ project-id] (filterv #(= project-id (:project-id %)) (vals (:drawings @a))))
  (records-of [_ project-id] (filter #(= project-id (:project-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-project! [s project]
    (swap! a assoc-in [:projects (:project-id project)] project) s)
  (register-drawing! [s drawing]
    (swap! a assoc-in [:drawings (:drawing-id drawing)] drawing) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  ;; `entry` is expected to be an already-chained entry from
  ;; `technical-drafting.ledger/entry`. The store appends what it is given; the
  ;; chain is built where the previous hash is known.
  (append-ledger! [s entry]
    (swap! a update :ledger (fnil conj []) entry) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:projects {} :drawings {} :records [] :ledger []} seed)))))
