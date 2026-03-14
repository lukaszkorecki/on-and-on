(ns on-and-on.scheduler
  "Core scheduler pool management. Provides fixed-rate and cron-based task scheduling
  on top of CronScheduler."
  (:require
   [clojure.tools.logging :as log]
   [on-and-on.cron :as cron]
   [on-and-on.time :as time])
  (:import
   (io.timeandspace.cronscheduler CronScheduler CronSchedulerBuilder CronTask OneShotTasksShutdownPolicy)
   (java.util.concurrent ThreadFactory TimeUnit)
   (java.util.concurrent.atomic AtomicLong)))

(set! *warn-on-reflection* true)

(defn- thread-factory
  "Creates a thread factory for use with scheduler pools."
  [{:keys [name daemon?]}]
  (let [thread-id (AtomicLong. 0)]
    (reify ThreadFactory
      (^Thread newThread [_ ^Runnable r]
        (doto (Thread. r)
          (.setDaemon daemon?)
          (.setName (str name "-" (AtomicLong/.getAndIncrement thread-id))))))))

(defn- fn->cron-task
  "Turns a Clojure function into a CronTask."
  [a-fn]
  (reify CronTask
    (run [_this _scheduled-run-time-ms]
      (a-fn))))

(defn make-scheduler-pool
  "Creates a new scheduler pool for running recurring tasks.
  Uses a 5-minute wall clock resync interval (recommended for server-side use)."
  [{:keys [name]}]
  (let [scheduler-builder ^CronSchedulerBuilder (CronScheduler/newBuilder (java.time.Duration/ofMinutes 5))
        scheduler (-> scheduler-builder
                      (.setThreadFactory (thread-factory {:name (str name "-scheduler")
                                                          :daemon? true}))
                      (.build))]
    (CronScheduler/.prestartThread scheduler)
    scheduler))

(defn scheduler-pool?
  "Returns true if thing is a CronScheduler instance."
  [thing]
  (instance? CronScheduler thing))

(def ^:private shutdown-policy (OneShotTasksShutdownPolicy/valueOf "DISCARD_DELAYED"))

(defn shutdown-scheduler-pool
  "Shuts down a scheduler pool, waiting up to 10s for tasks to complete.
  Falls back to shutdownNow if the timeout is exceeded."
  [^CronScheduler pool]
  (try
    (CronScheduler/.shutdown pool ^OneShotTasksShutdownPolicy shutdown-policy)
    (when-not (CronScheduler/.awaitTermination pool 10 TimeUnit/SECONDS)
      (CronScheduler/.shutdownNow pool))
    (catch InterruptedException _
      (CronScheduler/.shutdownNow pool)
      (Thread/.interrupt (Thread/currentThread)))))

(defn schedule-task
  "Schedules a recurring task running approximately every `period-ms` milliseconds.
  The underlying CronScheduler compensates for clock drift."
  [^CronScheduler pool {:keys [handler period-ms delay-ms mode]
                        :or {delay-ms 0}}]
  {:pre [(fn? handler)
         (nat-int? period-ms)
         (not (neg? delay-ms))]}
  (when mode
    (throw (ex-info "Only fixed-rate scheduling is supported" {:mode mode})))
  (CronScheduler/.scheduleAtFixedRate pool
                                      ^long delay-ms
                                      ^long period-ms
                                      TimeUnit/MILLISECONDS
                                      ^CronTask (fn->cron-task handler)))

(defn schedule-cron-task
  "Schedules a task using a Quartz cron expression.
  Uses SECONDS unit for precision with cron schedules.
  If calculated delay is 0, uses 1s to avoid a double-fire bug."
  [^CronScheduler pool {:keys [name handler schedule]}]
  {:pre [(fn? handler)
         (string? schedule)]}
  (let [now (time/now)
        cron-schedule (cron/parse schedule)
        interval-seconds (cron/execution-interval-in-seconds now cron-schedule)
        delay-seconds (cron/next-execution-in-seconds now cron-schedule)
        ;; NOTE: if delay is 0, this is a bug in the scheduler - it will fire off twice
        ;; within 10ms of each other, so we add a 1 second delay to avoid this
        delay-seconds (if (zero? delay-seconds) 1 delay-seconds)
        next-exec (cron/next-execution now cron-schedule)]
    (log/infof "registered %s with schedule %s (%s) - next run in %s seconds (%s)"
               name schedule
               (cron/explain cron-schedule)
               delay-seconds
               next-exec)
    (CronScheduler/.scheduleAtFixedRate pool
                                        ^long delay-seconds
                                        ^long interval-seconds
                                        TimeUnit/SECONDS
                                        ^CronTask (fn->cron-task handler))))
