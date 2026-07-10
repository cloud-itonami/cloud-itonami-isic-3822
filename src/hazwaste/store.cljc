(ns hazwaste.store
  "SSoT for the hazardous-waste treatment/disposal actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite (MemStore default,
  DatomicStore = langchain.db, same contract-tested parity as sibling
  actors). Entities: `shipments` (a manifest with its chain-of-custody
  steps — generator → transporter → facility), `facility-permits` (which
  waste-code × treatment-method combinations a facility may legally treat),
  `treatment-records` (what was actually done to a received shipment), and
  `contracts` (subscriber licensing for regulator/client disclosure). There
  is NO field for order/payment/transport-routing — this actor tracks
  regulatory manifest and treatment state only, it never arranges or
  executes the physical transport itself."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [langchain.db :as d]))

(defprotocol Store
  (shipment [s id])
  (all-shipments [s])
  (facility-permit [s id])
  (permits-of [s facility-id])
  (treatment-record [s manifest-id])
  (contract [s tenant])
  (ledger [s])
  (commit-record! [s record])
  (append-ledger! [s fact])
  (with-shipments [s m])
  (with-facility-permits [s m])
  (with-treatment-records [s m])
  (with-contracts [s m]))

(defn demo-data []
  {:shipments
   {"man-100" {:manifest-id "man-100" :generator-id "gen-1" :transporter-id "trans-1"
               :facility-id "fac-1" :waste-code "D001" :quantity-kg 500 :cross-border? false
               :chain [{:step :generator :signed-by "gen-1" :at "2026-07-01"}
                       {:step :transporter :signed-by "trans-1" :at "2026-07-02"}
                       {:step :facility :signed-by "fac-1" :at "2026-07-03"}]
               :status :received
               :source {:class :rcra-manifest-system :ref "epa-manifest:man-100"}}
    "man-200" {:manifest-id "man-200" :generator-id "gen-2" :transporter-id "trans-2"
               :facility-id "fac-1" :waste-code "D008" :quantity-kg 200 :cross-border? true
               :chain [{:step :generator :signed-by "gen-2" :at "2026-07-01"}
                       {:step :transporter :signed-by "trans-2" :at "2026-07-02"}]
               :status :in-transit
               :source {:class :basel-convention-movement-document :ref "basel-notify:man-200"}}}
   :facility-permits
   {"perm-1" {:permit-id "perm-1" :facility-id "fac-1"
              :waste-codes-authorized #{"D001" "F003"} :methods-authorized #{:incineration :stabilization}
              :active? true}
    "perm-2" {:permit-id "perm-2" :facility-id "fac-1"
              :waste-codes-authorized #{"D008"} :methods-authorized #{:landfill}
              :active? false}}
   :treatment-records {}
   :contracts
   {"tenant-regulator" {:tenant "tenant-regulator" :tier :tier/regulator :active? true :purpose :oversight}
    "tenant-basic" {:tenant "tenant-basic" :tier :tier/basic :active? true :purpose :generator-lookup}}})

(defrecord MemStore [a]
  Store
  (shipment [_ id] (get-in @a [:shipments id]))
  (all-shipments [_] (sort-by :manifest-id (vals (:shipments @a))))
  (facility-permit [_ id] (get-in @a [:facility-permits id]))
  (permits-of [_ facility-id]
    (->> (vals (:facility-permits @a)) (filter #(= facility-id (:facility-id %))) (sort-by :permit-id)))
  (treatment-record [_ manifest-id] (get-in @a [:treatment-records manifest-id]))
  (contract [_ tenant] (get-in @a [:contracts tenant]))
  (ledger [_] (:ledger @a))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :manifest-upsert  (swap! a assoc-in [:shipments (:manifest-id value)] value)
      :treatment-upsert (swap! a assoc-in [:treatment-records (:manifest-id value)] value)
      :correction-apply (swap! a update-in [:shipments (first path)] merge (:patch value))
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-shipments [s m] (when (seq m) (swap! a assoc :shipments m)) s)
  (with-facility-permits [s m] (when (seq m) (swap! a assoc :facility-permits m)) s)
  (with-treatment-records [s m] (when (seq m) (swap! a assoc :treatment-records m)) s)
  (with-contracts [s m] (when (seq m) (swap! a assoc :contracts m)) s))

(defn seed-db [] (->MemStore (atom (assoc (demo-data) :ledger []))))

(def ^:private schema
  {:shipment/id {:db/unique :db.unique/identity}
   :permit/id {:db/unique :db.unique/identity}
   :treatment/manifest-id {:db/unique :db.unique/identity}
   :contract/tenant {:db/unique :db.unique/identity}
   :ledger/seq {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- shipment->tx [{:keys [manifest-id generator-id transporter-id facility-id waste-code
                              quantity-kg cross-border? chain status source]}]
  {:shipment/id manifest-id :shipment/generator generator-id :shipment/transporter transporter-id
   :shipment/facility facility-id :shipment/waste-code waste-code :shipment/quantity-kg quantity-kg
   :shipment/cross-border (boolean cross-border?) :shipment/chain (enc chain)
   :shipment/status status :shipment/source (enc source)})

(defn- pull->shipment [m]
  (when (:shipment/id m)
    {:manifest-id (:shipment/id m) :generator-id (:shipment/generator m)
     :transporter-id (:shipment/transporter m) :facility-id (:shipment/facility m)
     :waste-code (:shipment/waste-code m) :quantity-kg (:shipment/quantity-kg m)
     :cross-border? (:shipment/cross-border m) :chain (dec* (:shipment/chain m))
     :status (:shipment/status m) :source (dec* (:shipment/source m))}))

(def ^:private shipment-pull
  [:shipment/id :shipment/generator :shipment/transporter :shipment/facility :shipment/waste-code
   :shipment/quantity-kg :shipment/cross-border :shipment/chain :shipment/status :shipment/source])

(defn- permit->tx [{:keys [permit-id facility-id waste-codes-authorized methods-authorized active?]}]
  {:permit/id permit-id :permit/facility facility-id
   :permit/waste-codes (enc waste-codes-authorized) :permit/methods (enc methods-authorized)
   :permit/active active?})

(defn- pull->permit [m]
  (when (:permit/id m)
    {:permit-id (:permit/id m) :facility-id (:permit/facility m)
     :waste-codes-authorized (dec* (:permit/waste-codes m)) :methods-authorized (dec* (:permit/methods m))
     :active? (:permit/active m)}))

(def ^:private permit-pull [:permit/id :permit/facility :permit/waste-codes :permit/methods :permit/active])

(defn- treatment->tx [{:keys [manifest-id method completed-at residue-disposition source]}]
  {:treatment/manifest-id manifest-id :treatment/method method :treatment/completed-at completed-at
   :treatment/residue-disposition residue-disposition :treatment/source (enc source)})

(defn- pull->treatment [m]
  (when (:treatment/manifest-id m)
    {:manifest-id (:treatment/manifest-id m) :method (:treatment/method m)
     :completed-at (:treatment/completed-at m) :residue-disposition (:treatment/residue-disposition m)
     :source (dec* (:treatment/source m))}))

(def ^:private treatment-pull
  [:treatment/manifest-id :treatment/method :treatment/completed-at :treatment/residue-disposition :treatment/source])

(defn- contract->tx [{:keys [tenant tier active? purpose]}]
  {:contract/tenant tenant :contract/tier tier :contract/active active? :contract/purpose purpose})

(defn- pull->contract [m]
  (when (:contract/tenant m)
    {:tenant (:contract/tenant m) :tier (:contract/tier m) :active? (:contract/active m) :purpose (:contract/purpose m)}))

(def ^:private contract-pull [:contract/tenant :contract/tier :contract/active :contract/purpose])

(defrecord DatomicStore [conn]
  Store
  (shipment [_ id] (pull->shipment (d/pull (d/db conn) shipment-pull [:shipment/id id])))
  (all-shipments [_]
    (->> (d/q '[:find [?id ...] :where [?e :shipment/id ?id]] (d/db conn))
         (map #(pull->shipment (d/pull (d/db conn) shipment-pull [:shipment/id %])))
         (sort-by :manifest-id)))
  (facility-permit [_ id] (pull->permit (d/pull (d/db conn) permit-pull [:permit/id id])))
  (permits-of [_ facility-id]
    (->> (d/q '[:find [?id ...] :in $ ?f :where [?p :permit/facility ?f] [?p :permit/id ?id]]
              (d/db conn) facility-id)
         (map #(pull->permit (d/pull (d/db conn) permit-pull [:permit/id %])))
         (sort-by :permit-id)))
  (treatment-record [_ manifest-id]
    (pull->treatment (d/pull (d/db conn) treatment-pull [:treatment/manifest-id manifest-id])))
  (contract [_ tenant] (pull->contract (d/pull (d/db conn) contract-pull [:contract/tenant tenant])))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first) (mapv (comp dec* second))))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :manifest-upsert  (d/transact! conn [(shipment->tx value)])
      :treatment-upsert (d/transact! conn [(treatment->tx value)])
      :correction-apply (d/transact! conn [(shipment->tx (merge (shipment s (first path)) (:patch value)))])
      nil)
    s)
  (append-ledger! [s fact] (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}]) fact)
  (with-shipments [s m] (when (seq m) (d/transact! conn (mapv shipment->tx (vals m)))) s)
  (with-facility-permits [s m] (when (seq m) (d/transact! conn (mapv permit->tx (vals m)))) s)
  (with-treatment-records [s m] (when (seq m) (d/transact! conn (mapv treatment->tx (vals m)))) s)
  (with-contracts [s m] (when (seq m) (d/transact! conn (mapv contract->tx (vals m)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [shipments facility-permits treatment-records contracts]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (-> s (with-shipments shipments) (with-facility-permits facility-permits)
         (with-treatment-records treatment-records) (with-contracts contracts)))))

(defn datomic-seed-db [] (datomic-store (demo-data)))

(defn ledger-line [{:keys [op actor subject disposition basis]}]
  (str/join " · " [(name disposition) (str "op=" op) (str "actor=" actor)
                    (str "subject=" subject) (str "basis=" (pr-str basis))]))
