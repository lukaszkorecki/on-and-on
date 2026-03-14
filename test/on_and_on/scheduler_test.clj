(ns on-and-on.scheduler-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [on-and-on.scheduler :as scheduler]))

(deftest make-and-shutdown-pool-test
  (testing "creates and shuts down a scheduler pool"
    (let [pool (scheduler/make-scheduler-pool {:name "test"})]
      (is (scheduler/scheduler-pool? pool))
      (scheduler/shutdown-scheduler-pool pool))))


(deftest schedule-task-rejects-mode-test
  (testing "passing :mode throws because only fixed-rate is supported"
    (let [pool (scheduler/make-scheduler-pool {:name "mode-test"})]
      (try
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Only fixed-rate scheduling is supported"
                              (scheduler/schedule-task pool {:handler (fn [])
                                                             :period-ms 100
                                                             :mode :fixed-delay})))
        (finally
          (scheduler/shutdown-scheduler-pool pool))))))

(defn- round-down [ms]
  (let [round-to 100]
    (* (quot ms round-to) round-to)))

(deftest scheduler-fixed-rate-test
  (let [pool (scheduler/make-scheduler-pool {:name "test"})
        start-time (System/currentTimeMillis)
        state (atom [])
        task (fn []
               (Thread/sleep 200)
               (swap! state conj (- (System/currentTimeMillis) ^long start-time)))]

    (testing "scheduling a task with fixed-rate fires regardless of previous execution time"
      (scheduler/schedule-task pool {:handler task
                                     :period-ms 100})

      (is (empty? @state))

      (Thread/sleep 300)
      (is (= [200] (mapv round-down @state)))

      (Thread/sleep 350)
      (testing "after another 350ms, the task ran again without waiting"
        (is (= [200 400 600] (mapv round-down @state))))

      (scheduler/shutdown-scheduler-pool pool))))

(deftest shutdown-long-task-test
  (testing "long-running task gets interrupted on shutdown"
    (let [pool (scheduler/make-scheduler-pool {:name "shutdown-test"})
          has-run? (atom false)
          has-finished? (atom false)
          task (fn []
                 (try
                   (reset! has-run? true)
                   (Thread/sleep 12000) ;; over the 10s deadline
                   (reset! has-finished? true)
                   (catch InterruptedException _)))]

      (scheduler/schedule-task pool {:handler task :period-ms 1000})
      ;; wait for the task to start before shutting down
      (Thread/sleep 500)
      (scheduler/shutdown-scheduler-pool pool)

      (is (= true @has-run?))
      (is (= false @has-finished?)
          "Task should not have finished because it should have been interrupted"))))

(deftest schedule-cron-task-test
  (testing "cron task schedules and fires"
    (let [pool (scheduler/make-scheduler-pool {:name "cron-test"})
          counter (atom 0)
          task (fn [] (swap! counter inc))]
      (try
        (scheduler/schedule-cron-task pool {:name "every-second"
                                            :handler task
                                            :schedule "/1 * * * * ? *"})
        (Thread/sleep 3500)
        (is (>= @counter 2) "cron task should have fired at least twice in 3.5s")
        (finally
          (scheduler/shutdown-scheduler-pool pool))))))
