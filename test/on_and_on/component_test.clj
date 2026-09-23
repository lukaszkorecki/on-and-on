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

(deftest stop-cancels-task-test
  (testing "stopping a task cancels it while the pool keeps running"
    (let [counter (atom 0)
          pool (component/start (comp/create-pool {:name "StopTest"}))
          task (component/start (assoc (comp/create-task {:name "stop-task"
                                                          :period-ms 10
                                                          :handler (fn [_] (swap! counter inc))})
                                       :scheduler pool))]
      (try
        (Thread/sleep 100)
        (component/stop task)
        (let [seen @counter]
          (Thread/sleep 100)
          (is (pos? seen))
          (is (<= @counter (inc seen)) "task should not fire after stop"))
        (finally
          (component/stop pool))))))

(deftest pool-thread-name-test
  (testing "pool threads are named after the pool"
    (let [thread-name (promise)
          pool (component/start (comp/create-pool {:name "NameTest"}))
          task (component/start (assoc (comp/create-task {:name "name-task"
                                                          :period-ms 10
                                                          :handler (fn [_]
                                                                     (deliver thread-name (.getName (Thread/currentThread))))})
                                       :scheduler pool))]
      (try
        (is (re-find #"^NameTest-scheduler-" (deref thread-name 1000 "timeout")))
        (finally
          (component/stop task)
          (component/stop pool))))))
