(ns design.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [design.actor :as actor]
            [design.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :client-id "client-1" :name "Summer Collection"})
    st))

(deftest commits-a-clean-low-risk-design-concept
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "proj-1" :op :draft-design-concept :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "proj-1"))))))

(deftest holds-on-unregistered-project-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "no-such-project" :op :draft-design-concept :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-project")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; safety compliance flag always escalates (governor invariant)
        request {:project-id "proj-1" :op :flag-safety-compliance-issue :stake :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "proj-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "proj-1")))))))

(deftest holds-on-production-sign-off-attempt
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; attempting to issue a production sign-off is a hard block
        request {:project-id "proj-1" :op :issue-production-sign-off :stake :high}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "proj-1")))
    (is (= :hold (:disposition (:state result))))))
