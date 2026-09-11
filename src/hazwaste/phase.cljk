(ns hazwaste.phase
  "Phase 0→3 staged rollout, same shape as `cloud-itonami-isic-6311`/
  `isic-3811`'s phase gates: the phase can only make the actor MORE
  conservative than the governor, never less.

    Phase 0  read-only          — `:disclosure/query` only (still governor-gated).
    Phase 1  assisted-manifest  — `:manifest/receive` allowed, human-approved.
    Phase 2  + treatment        — adds `:treatment/execute`/`:correction/request`.
    Phase 3  supervised auto    — governor-clean `:manifest/receive`/
                                  `:treatment/execute` may auto-commit.

  `:correction/request` is never in any phase's `:auto` set.")

(def read-ops  #{:disclosure/query})
(def write-ops #{:manifest/receive :treatment/execute :correction/request})

(def phases
  {0 {:label "read-only"          :writes #{} :auto #{}}
   1 {:label "assisted-manifest"  :writes #{:manifest/receive} :auto #{}}
   2 {:label "assisted-treatment" :writes #{:manifest/receive :treatment/execute :correction/request} :auto #{}}
   3 {:label "supervised-auto"    :writes #{:manifest/receive :treatment/execute :correction/request}
                                   :auto #{:manifest/receive :treatment/execute}}})

(def default-phase
  "A caller that omits :phase must get the conservative default, never max
  autonomy (the fail-open bug found and fixed this session across the
  isic-6311/talent.phase sibling templates) — never 3, always 1."
  1)

(defn gate [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)      {:disposition :hold :reason nil}
      (contains? read-ops op)             {:disposition governor-disposition :reason nil}
      (not (contains? writes op))         {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))     {:disposition :escalate :reason :phase-approval}
      :else                               {:disposition governor-disposition :reason nil})))

(defn verdict->disposition [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
