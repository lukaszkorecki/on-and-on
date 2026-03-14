(ns on-and-on.component-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [com.stuartsierra.component :as component]
   [on-and-on.component :as comp]))

(def sys
  (component/map->SystemMap
   {:scheduler (comp/create-pool {:name "Test"})
    :store (atom [])
    :task-1 (component/using
             (comp/create-task {:name "task 1"
                                :period-ms 100
                                :handler (fn [{:keys [store]}]
                                           (swap! store conj :task-1))})
             [:scheduler :store])

    :task-2 (component/using
             (comp/create-task {:name "task 2"
                                :period-ms 200
                                :handler (fn [{:keys [store]}]
                                           (swap! store conj :task-2))})
             [:scheduler :store])}))

(def system (atom nil))

(use-fixtures :once (fn [test]
                      (try
                        (reset! system (component/start sys))
                        (test)
                        (finally
                          (swap! system #(when %
                                           (component/stop %)))))))

(deftest scheduler-test
  (Thread/sleep 500)
  (testing "tasks 'tick' for 500ms"
    (is (= [:task-1
            :task-1
            :task-1
            :task-1
            :task-1
            :task-2
            :task-2
            :task-2]
           (->> @system :store deref (take 8) sort)))))

(deftest task-error-resilience-test
  (testing "a task that throws keeps firing on subsequent ticks"
    (let [counter (atom 0)
          error-sys (component/map->SystemMap
                     {:scheduler (comp/create-pool {:name "ErrorTest"})
                      :task (component/using
                             (comp/create-task {:name "error-task"
                                                :period-ms 100
                                                :handler (fn [_]
                                                           (swap! counter inc)
                                                           (throw (ex-info "boom" {})))})
                             [:scheduler])})
          started (component/start error-sys)]
      (try
        (Thread/sleep 400)
        (is (> @counter 1) "task should have fired multiple times despite throwing")
        (finally
          (component/stop started))))))

(deftest cron-task-component-test
  (testing "cron-based task component works"
    (let [counter (atom 0)
          cron-sys (component/map->SystemMap
                    {:scheduler (comp/create-pool {:name "CronTest"})
                     :task (component/using
                            (comp/create-task {:name "cron-task"
                                               :schedule "/1 * * * * ? *" ;; every second
                                               :handler (fn [_]
                                                          (swap! counter inc))})
                            [:scheduler])})
          started (component/start cron-sys)]
      (try
        (Thread/sleep 3500)
        (is (>= @counter 2) "cron task should have fired at least twice in 3.5s")
        (finally
          (component/stop started))))))
