(ns hazwaste.llm
  "HazWasteTreatment-LLM client — the contained intelligence node. It
  normalizes incoming manifest chain-of-custody claims, drafts treatment/
  disposal records against an already-received manifest, proposes
  disclosure column sets, and drafts correction resolutions. Never commits,
  discloses, or resolves a dispute directly — every output is censored by
  `hazwaste.policy` (the HazWasteGovernor) first."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [langchain.model :as model]
            [hazwaste.store :as store]))

(defn- propose-manifest-receive
  "`:broken-chain?` injects the failure mode we must defend against: the LLM
  proposing a manifest as received with an incomplete/unsigned
  chain-of-custody (e.g. the facility step never got signed off) — the
  chain-of-custody-gate must reject regardless of confidence."
  [_db {:keys [manifest-id generator-id transporter-id facility-id waste-code
               quantity-kg cross-border? chain source broken-chain?]}]
  (let [chain* (if broken-chain?
                 (vec (remove #(= :facility (:step %)) chain))
                 chain)]
    {:summary   (str "manifest receive: " manifest-id " @ " facility-id)
     :rationale "出典引用済みマニフェストの正規化のみ。新規事実の生成なし。"
     :cites     [:manifest-id :chain :waste-code]
     :source    source
     :effect    :manifest-upsert
     :value     {:manifest-id manifest-id :generator-id generator-id :transporter-id transporter-id
                 :facility-id facility-id :waste-code waste-code :quantity-kg quantity-kg
                 :cross-border? cross-border? :chain chain* :status :received :source source}
     :confidence (if broken-chain? 0.9 0.95)}))

(defn- propose-treatment-execute
  [_db {:keys [manifest-id method completed-at residue-disposition source]}]
  {:summary   (str "treatment execute: " manifest-id " via " method)
   :rationale "受入済みマニフェストへの処理記録の正規化のみ。"
   :cites     [:manifest-id :method]
   :source    source
   :effect    :treatment-upsert
   :value     {:manifest-id manifest-id :method method :completed-at completed-at
               :residue-disposition residue-disposition :source source}
   :confidence 0.9})

(defn- propose-disclosure
  [_db {:keys [manifest-id greedy?]}]
  (let [base [:manifest-id :facility-id :waste-code :status :as-of]
        greedy-extra [:chain :treatment-record :raw-source]]
    {:summary   (str "開示列提案: " manifest-id)
     :rationale (if greedy? "分析に有用そうな列を広めに含めた。" "契約 tier に必要な最小列のみ。")
     :cites     base :source nil :effect :disclosure-serve
     :columns   (if greedy? (into base greedy-extra) base)
     :confidence 0.9}))

(defn- propose-correction
  [_db {:keys [disputed-field claim]}]
  {:summary   (str "manifest の " disputed-field " について訂正申立てへの解決案ドラフト")
   :rationale (str "申立て内容: " claim "。裏取りは人間レビューで行う。")
   :cites     [disputed-field] :source nil :effect :correction-apply
   :value     {:patch {disputed-field claim}}
   :confidence 0.5})

(defn infer [db {:keys [op] :as request}]
  (case op
    :manifest/receive    (propose-manifest-receive db request)
    :treatment/execute   (propose-treatment-execute db request)
    :disclosure/query    (propose-disclosure db request)
    :correction/request  (propose-correction db request)
    {:summary "未対応の操作" :rationale (str op) :cites [] :source nil :effect :noop :confidence 0.0}))

(defprotocol Advisor
  (-advise [advisor store request]))

(defn mock-advisor [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは危険廃棄物の受入・処理記録アドバイザーです。事実のみに基づき、"
       "提案を1つだけ EDN マップで返します。キー: :summary :rationale :cites "
       ":source({:class .. :ref ..}か nil) :effect :value :columns :confidence。"
       "出典を伴わない受入・処理記録は絶対に提案してはいけません。chain-of-custody "
       "の完全性判断や施設許可の妥当性判断はあなたの責務ではありません(governorが判定)。"))

(defn- facts-for [st {:keys [subject manifest-id]}]
  {:shipment (store/shipment st (or manifest-id subject))})

(defn- parse-proposal [content]
  (let [p (try (edn/read-string (str/trim (str content))) (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :source nil :effect :noop :confidence 0.0})))

(defn llm-advisor
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req) "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace [request proposal]
  {:t :hazwastellm-proposal :op (:op request) :subject (:subject request)
   :summary (:summary proposal) :rationale (:rationale proposal)
   :cites (:cites proposal) :source (:source proposal) :confidence (:confidence proposal)})
