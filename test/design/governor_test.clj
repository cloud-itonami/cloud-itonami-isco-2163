(ns design.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [design.store :as store]
            [design.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :client-id "client-1" :name "Summer Collection"})
    st))

(deftest ok-on-clean-design-concept
  (let [st (fresh-store)
        proposal {:op :draft-design-concept :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-project
  (let [st (fresh-store)
        proposal {:op :draft-design-concept :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:project-id "no-such-project"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-project (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-design-concept :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-issue-production-sign-off
  (let [st (fresh-store)
        proposal {:op :issue-production-sign-off :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-production-sign-off (:rule %)) (:violations v)))))

(deftest hard-on-attempt-to-certify-safety
  (let [st (fresh-store)
        proposal {:op :certify-safety-compliance :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-production-sign-off (:rule %)) (:violations v)))))

(deftest escalates-on-safety-compliance-flag
  (let [st (fresh-store)
        proposal {:op :flag-safety-compliance-issue :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-safety-concern
  (let [st (fresh-store)
        proposal {:op :draft-design-concept :effect :propose :confidence 0.9 :stake :medium
                  :scope :material-safety :tags #{:safety :flammability}}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :prepare-specification :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:project-id "proj-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:project-id "proj-1" :op :prepare-specification})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "proj-1"))))
    (is (= 1 (count (store/ledger st))))))
