(ns on-and-on.component
  "Component integration for scheduler pools and tasks."
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component] ;; noqa - needed for metadata protocol extension
   [on-and-on.scheduler :as scheduler]))

(set! *warn-on-reflection* true)

;;; Scheduler Pool

(defrecord SchedulerPool [opts
                          ;; internal state
                          executor]
  component/Lifecycle
  (start [this]
    (if (:executor this)
      this
      (do
        (log/infof "Creating scheduler pool %s" name)
        (assoc this :executor (scheduler/make-scheduler-pool opts)))))

  (stop [this]
    (if (:executor this)
      (do
        (log/warnf "stopping %s scheduler pool" name)
        (scheduler/shutdown-scheduler-pool (:executor this))
        (assoc this :executor nil))
      this)))

(defn create-pool
  "Creates a scheduler pool component."
  [{:keys [name] :as opts}]
  {:pre [(not-empty name)]}
  (map->SchedulerPool opts))

;;; Tasks

(defrecord ScheduledTask [opts scheduler handler delay-ms period-ms schedule
                          ;; internal state
                          task]
  component/Lifecycle
  (start [this]
    (if (:task this)
      this
      (do
        (log/infof "Creating scheduled task %s" name)
        (assert (scheduler/scheduler-pool? (:executor (:scheduler this)))
                "Scheduled task requires a :scheduler dependency")
        (let [pool (-> this :scheduler :executor)
              wrapped-handler (fn scheduled' []
                                (try
                                  (handler (dissoc this :scheduler :task))
                                  (catch Throwable err
                                    (log/errorf err "recurring task '%s' failed" name))))]
          (assoc this :task
                 (if schedule
                   (do
                     (log/infof "Scheduling task '%s' with cron expression '%s'" name schedule)
                     (scheduler/schedule-cron-task pool {:name name
                                                         :handler wrapped-handler
                                                         :schedule schedule}))
                   (do
                     (log/infof "Scheduling task '%s' with fixed period of %d ms and initial delay of %d ms"
                                name period-ms delay-ms)
                     (scheduler/schedule-task pool {:handler wrapped-handler
                                                    :period-ms period-ms
                                                    :delay-ms delay-ms}))))))))

  (stop [this]
    (if (:task this)
      (do
        (log/warnf "stopping task %s" (:name this))
        (assoc this :task nil))
      this)))

(defn create-task
  "Creates a scheduled task component. Requires a :scheduler dependency.

  Supports two scheduling modes (mutually exclusive):
  - `:period-ms` — fixed-rate interval in milliseconds
  - `:schedule` — Quartz cron expression string

  Options:
  - `:name` — task name (required)
  - `:handler` — function called each tick, receives the component map (required)
  - `:period-ms` — interval in ms for fixed-rate scheduling
  - `:schedule` — cron expression for cron-based scheduling
  - `:delay-ms` — initial delay in ms (only when `period-ms` is provided, ignored when cron `schedule` is used, default 0)"
  [{:keys [name period-ms schedule delay-ms handler]
    :or {delay-ms 0}}]
  {:pre [(not-empty name)
         (fn? handler)
         (or (nat-int? period-ms) (string? schedule))
         (not (and period-ms schedule))]}
  (map->ScheduledTask {:name name
                       :handler handler
                       :period-ms period-ms
                       :schedule schedule
                       :delay-ms delay-ms}))
