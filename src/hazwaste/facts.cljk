(ns hazwaste.facts
  "R0 source-basis catalog — the ONLY provenance classes the HazWasteGovernor
  will accept as a citation for a manifest step or treatment record (mirrors
  `cloud-itonami-isic-6311`/`isic-8291`'s honesty-over-coverage discipline).
  Three real regulatory-provenance classes: the US RCRA Subtitle C manifest
  system, the EU Waste Shipment Regulation, and the Basel Convention
  transboundary-movement document regime. This actor NEVER treats an
  unsourced or self-asserted chain-of-custody step as grounded — a real
  signed manifest document (or its electronic equivalent) is always
  required.")

(def catalog
  [{:id :rcra-manifest-system
    :name "US EPA RCRA Subtitle C Uniform Hazardous Waste Manifest (40 CFR Part 262)"
    :jurisdiction :usa :class :rcra-manifest-system :access :regulatory-filing
    :url "https://www.epa.gov/hwgenerators/hazardous-waste-manifest-system"}
   {:id :eu-waste-shipment-regulation
    :name "EU Waste Shipment Regulation (Regulation (EC) No 1013/2006)"
    :jurisdiction :eu :class :eu-waste-shipment-regulation :access :regulatory-filing
    :url "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32006R1013"}
   {:id :basel-convention-movement-document
    :name "Basel Convention transboundary movement notification/consent document"
    :jurisdiction :intl :class :basel-convention-movement-document :access :regulatory-filing
    :url "https://www.basel.int"}])

(def allowed-source-classes (into #{} (map :class catalog)))

(defn coverage []
  {:source-count (count catalog)
   :jurisdictions (into (sorted-set) (map :jurisdiction catalog))
   :note (str "R0 scope: 3 real regulatory-provenance regimes (US RCRA manifest "
              "system, EU Waste Shipment Regulation, Basel Convention). Extend only "
              "by appending a real, citable regulatory-filing regime — never fabricate one.")})

(defn class-allowed? [c] (contains? allowed-source-classes c))
