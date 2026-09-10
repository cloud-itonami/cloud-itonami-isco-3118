(ns technical-drafting.actor
  "TechnicalDraftingActor — the ISCO-08 3118 technical draughtsperson actor as
  a `langgraph.graph/state-graph` (per ADR-2607011000 / CLAUDE.md
  Actors section). One graph run = one drafting operation request
  (intake → advise → govern → decide → commit/hold, with a
  human-approval interrupt for escalated proposals). No infinite
  internal loop; checkpointed per superstep so an interrupted run can
  resume after human sign-off.

  ```text
  :intake -> :advise -> :govern -> :decide -+-> :commit           (phase :commit)
                                             +-> :request-approval  (phase :request-approval, interrupt-before)
                                             +-> :hold              (phase :hold)
  ```

  The unconditional invariant: the TechnicalDraftingAdvisor can never
  directly finalize or certify a drawing, or dispatch action the
  TechnicalDraftingGovernor refuses — every commit-record! call is gated
  behind `:decide`.

  Two things this namespace no longer decides for itself. The verdict → phase
  mapping is `technical-drafting.phase/of-verdict`, a named pure function
  rather than an inline `cond` reachable only by running a graph. And every
  ledger write goes through `technical-drafting.ledger`, which chains each
  entry to the one before it and records WHO approved the write — measured on
  the pre-change tree, a human-approved commit and an automatic one left
  entries with no field telling them apart, which is the one question the
  approval interrupt exists to answer."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [technical-drafting.advisor :as advisor]
            [technical-drafting.governor :as governor]
            [technical-drafting.ledger :as led]
            [technical-drafting.phase :as phase]
            [technical-drafting.store :as store]))

(defn- append-chained!
  "Append `m` to the store's ledger as a chained entry. The chain is built here
  because this is where the previous hash is known; `store/append-ledger!`
  appends what it is given."
  [st m]
  (store/append-ledger! st (led/entry (store/ledger st) m)))

(defn build-graph
  "Build a compiled TechnicalDraftingActor graph. `store` implements
  `technical-drafting.store/Store`. `advisor` implements
  `technical-drafting.advisor/Advisor` (defaults to `mock-advisor`).
  `checkpointer` defaults to an in-memory one."
  [{:keys [store advisor checkpointer]
    :or {advisor (advisor/mock-advisor)
         checkpointer (cp/mem-checkpointer)}}]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :advise
                   (fn [{:keys [request]}]
                     (let [p (advisor/-advise advisor store request)]
                       {:proposal p
                        :audit [{:node :advise :request request :proposal p}]})))
      (g/add-node :govern
                   (fn [{:keys [request context proposal]}]
                     (let [v (governor/check request context proposal store)]
                       {:verdict v
                        :audit [{:node :govern :verdict v}]})))
      (g/add-node :decide
                   (fn [{:keys [verdict]}]
                     {:disposition (phase/of-verdict verdict)}))
      (g/add-node :request-approval (fn [s] s))
      (g/add-node :commit
                   (fn [{:keys [request proposal disposition]}]
                     (let [record {:project-id (:project-id request)
                                   :op (:op proposal)
                                   :drawing-id (:drawing-id proposal)
                                   :payload proposal}
                           ;; The commit node is reached either directly (the
                           ;; governor admitted it) or from the interrupted
                           ;; :request-approval node (a human resumed the
                           ;; thread). `:disposition` still carries which,
                           ;; because :request-approval does not overwrite it.
                           approved-by (if (phase/approved-commit? disposition) :human :actor)]
                       (store/commit-record! store record)
                       (append-chained! store (led/commit-entry record approved-by))
                       {:record record
                        :audit [{:node :commit :record record :approved-by approved-by}]})))
      (g/add-node :hold
                   (fn [{:keys [verdict]}]
                     (append-chained! store (led/hold-entry verdict))
                     {:audit [{:node :hold :verdict verdict}]}))
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :request-approval :request-approval
           :hold)))
      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

(defn run-request!
  "Run one drafting operation request to completion or interrupt. `thread-id`
  scopes checkpointing for resume after human approval. Returns the
  full run result: `{:state .. :events .. :status :done|:interrupted
  :frontier ..}`."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: the interrupted `:request-approval` node
  advances straight to `:commit` on resume (approval is the act of
  resuming the thread). The resulting ledger entry records
  `:approved-by :human`."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
