(ns design.governor
  "DesignGovernor — the independent safety/traceability layer for
  the ISCO-08 2163 independent product and garment designer actor. Wired
  as its own `:govern` node in `design.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of project provenance, safety
  compliance or the designer's professional/legal responsibility, so this
  MUST be a separate system able to reject a proposal (itonami actor
  pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. project provenance  — the request's project must be registered.
    2. no-actuation        — proposal :effect must be :propose.
    3. no-production-sign-off/certification — any attempt to issue a
       production-ready sign-off or certify safety/regulatory compliance is
       a permanent block (that is the designer's exclusive professional
       responsibility).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off, per the
  README robotics-premise: production sign-offs and safety certifications
  always require human designer sign-off):
    4. :op :flag-safety-compliance-issue (always escalates).
    5. design proposals affecting material safety or regulatory compliance.
    6. low confidence (< `confidence-floor`)."
  (:require [design.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:flag-safety-compliance-issue})
(def ^:private safety-keywords #{:safety :regulatory :compliance :material-safety :toxicity :flammability :child-safety :allergen})

(defn- hard-violations [{:keys [proposal]} project-record]
  (cond-> []
    (nil? project-record)
    (conj {:rule :no-project :detail "未登録 project"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (or (= :issue-production-sign-off (:op proposal))
        (= :certify-safety-compliance (:op proposal)))
    (conj {:rule :no-production-sign-off :detail "production sign-offs and safety certifications are designer's exclusive responsibility"})))

(defn- safety-concern? [{:keys [scope tags]}]
  (and scope tags (some safety-keywords tags)))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `design.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [project-record (store/project store (:project-id request))
        hard (hard-violations {:proposal proposal} project-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))
        safety? (and (not hard?) (safety-concern? proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?) (not safety?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op? safety?))}))
