(ns hazwaste.policy
  "HazWasteGovernor — the independent compliance layer earning HazWasteTreatment-LLM
  the right to receive a manifest, execute treatment, disclose, or resolve a
  dispute. Eight checks, HARD first (human cannot override), then SOFT
  (always escalate to a human who may approve):

    1. rbac
    2. manifest-chain-of-custody-gate — HARD, `:manifest/receive` only. Every
       hazardous shipment must carry an unbroken, signed chain
       generator → transporter → facility (40 CFR Part 262) — a missing or
       unsigned step is rejected regardless of confidence.
    3. treatment-method-authorization-gate — HARD, `:treatment/execute` only.
       The facility must hold an ACTIVE permit authorizing both the specific
       waste-code and the specific treatment method — an unauthorized
       waste-code/method combination, or an inactive/lapsed permit, is
       rejected.
    4. source-provenance-gate — HARD. Source class must be in
       `hazwaste.facts/allowed-source-classes`.
    5. licensed-disclosure — HARD, `:disclosure/query` only.
    6. confidence floor — SOFT.
    7. cross-border-gate — SOFT. Any shipment flagged `:cross-border?` always
       escalates (Basel Convention transboundary-movement oversight) —
       regardless of confidence.
    8. dispute-request — SOFT, unconditional. A manifest correction dispute
       never auto-resolves, at any confidence, any phase."
  (:require [clojure.set :as set]
            [hazwaste.facts :as facts]
            [hazwaste.store :as store]))

(def confidence-floor 0.6)

(def permits
  {:treatment-operator #{:manifest/receive :treatment/execute}
   :compliance-officer #{:manifest/receive :treatment/execute :correction/request}
   :subscriber          #{:disclosure/query}})

(def tier-columns
  (let [base #{:manifest-id :facility-id :waste-code :status :as-of}
        regulator-extra #{:chain :treatment-record :raw-source}]
    {:tier/basic     base
     :tier/regulator (into base regulator-extra)}))

(defn- rbac-violations [{:keys [op]} {:keys [actor-role]}]
  (when-not (contains? (get permits actor-role #{}) op)
    [{:rule :rbac :detail (str actor-role " は " op " の権限を持たない")}]))

(defn- chain-of-custody-violations [{:keys [op]} proposal]
  (when (= op :manifest/receive)
    (let [chain (get-in proposal [:value :chain])
          steps (into #{} (map :step) chain)
          signed? (every? :signed-by chain)]
      (when (or (not (contains? steps :generator))
                (not (contains? steps :transporter))
                (not (contains? steps :facility))
                (not signed?))
        [{:rule :manifest-chain-of-custody-gate
          :detail (str "chain-of-custody が不完全/未署名: " (pr-str chain))}]))))

(defn- treatment-authorization-violations [{:keys [op]} proposal st]
  (when (= op :treatment/execute)
    (let [manifest-id (get-in proposal [:value :manifest-id])
          method       (get-in proposal [:value :method])
          ship         (store/shipment st manifest-id)
          waste-code   (:waste-code ship)
          authorized?  (some (fn [p]
                                (and (:active? p)
                                     (contains? (:waste-codes-authorized p) waste-code)
                                     (contains? (:methods-authorized p) method)))
                              (store/permits-of st (:facility-id ship)))]
      (when-not authorized?
        [{:rule :treatment-method-authorization-gate
          :detail (str "施設 " (:facility-id ship) " は waste-code=" waste-code
                       " method=" method " の有効な許可を持たない")}]))))

(defn- source-provenance-violations [{:keys [op]} proposal]
  (when (contains? #{:manifest/receive :treatment/execute} op)
    (let [src (:source proposal)]
      (when (or (nil? src) (not (facts/class-allowed? (:class src))))
        [{:rule :source-provenance-gate :detail (str "出典が無いか許可された出典クラスでない: " (pr-str src))}]))))

(defn- licensed-disclosure-violations [{:keys [op]} {:keys [tenant]} proposal st]
  (when (= op :disclosure/query)
    (let [c (when tenant (store/contract st tenant))]
      (if (or (nil? c) (not (:active? c)))
        [{:rule :licensed-disclosure :detail (str "有効な契約が無い: tenant=" tenant)}]
        (let [allowed (get tier-columns (:tier c) #{})
              extra (set/difference (set (:columns proposal)) allowed)]
          (when (seq extra)
            [{:rule :licensed-disclosure :detail (str "契約 tier " (:tier c) " に対し過剰な列: " (vec extra))}]))))))

(defn- cross-border?
  "For `:manifest/receive` the shipment doesn't exist in the store yet at
  check time -- the cross-border flag lives on the PROPOSAL, not a store
  lookup. For other ops (e.g. `:disclosure/query` against an already-
  received shipment) fall back to the stored shipment's flag."
  [{:keys [op]} proposal st subject]
  (if (= op :manifest/receive)
    (boolean (get-in proposal [:value :cross-border?]))
    (when subject (boolean (:cross-border? (store/shipment st subject))))))

(defn check [request context proposal st]
  (let [hard (into [] (concat (rbac-violations request context)
                               (chain-of-custody-violations request proposal)
                               (treatment-authorization-violations request proposal st)
                               (source-provenance-violations request proposal)
                               (licensed-disclosure-violations request context proposal st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        cross-border-flag? (cross-border? request proposal st (:subject request))
        correction? (= :correction/request (:op request))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not cross-border-flag?) (not correction?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? cross-border-flag? correction?))
     :cross-border? cross-border-flag?
     :correction?  correction?}))

(defn hold-fact [request context verdict]
  {:t :policy-hold :op (:op request) :actor (:actor-id context) :subject (:subject request)
   :disposition :hold :basis (mapv :rule (:violations verdict))
   :violations (:violations verdict) :confidence (:confidence verdict)})
