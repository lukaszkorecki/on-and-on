(ns on-and-on.cron
  "Cron expression parsing using cron-utils (Quartz flavor).
  Supports second-level precision with 7-field expressions."
  (:require
   [on-and-on.time :as time])
  (:import
   (com.cronutils.descriptor CronDescriptor)
   (com.cronutils.model Cron CronType)
   (com.cronutils.model.definition CronDefinition CronDefinitionBuilder)
   (com.cronutils.model.time ExecutionTime)
   (com.cronutils.parser CronParser)
   (java.time Duration Instant)
   (java.util Locale Optional)))

(set! *warn-on-reflection* true)

;; NOTE: we're using Quartz flavor of Cron to allow for second schedules
;; UNIX cron only supports minute schedules
(def parser
  (let [cron-definition (CronDefinitionBuilder/instanceDefinitionFor CronType/QUARTZ)]
    (new CronParser ^CronDefinition cron-definition)))

(def descriptor
  (CronDescriptor/instance Locale/US))

(defn parse
  "Parses a Quartz cron expression string into a Cron object."
  ^Cron [^String expr]
  (CronParser/.parse parser expr))

(defn explain
  "Returns a human-readable description of a parsed cron expression."
  ^String [^Cron parsed]
  (CronDescriptor/.describe descriptor parsed))

(defn matches-schedule?
  "Returns true if the given instant matches the cron schedule."
  [^Instant now ^Cron parsed]
  (let [now-in-utc (time/instant->zoned-date-time-utc now)
        execution-time (ExecutionTime/forCron parsed)]
    (ExecutionTime/.isMatch execution-time now-in-utc)))

(defn next-execution
  "Returns the next execution instant after the given time."
  ^Instant [^Instant now ^Cron parsed]
  (let [now-in-utc (time/instant->zoned-date-time-utc (time/truncate-to now :seconds))
        execution-time (ExecutionTime/forCron parsed)
        next (Optional/.get (ExecutionTime/.nextExecution execution-time now-in-utc))]
    (time/zoned-date-time->instant next)))

(defn next-execution-in-seconds
  "Returns the number of seconds until the next execution.
  Returns 0 if current time matches the schedule."
  [^Instant now ^Cron parsed]
  (let [now-in-utc (time/instant->zoned-date-time-utc (time/truncate-to now :seconds))
        execution-time (ExecutionTime/forCron parsed)
        next (Optional/.get (ExecutionTime/.timeToNextExecution execution-time now-in-utc))
        is-now-matching? (ExecutionTime/.isMatch execution-time now-in-utc)]
    (if is-now-matching?
      ;; NOTE: if current time matches the cron expression -
      ;; ExecutionTime/next will return the NEXT execution rather than current
      0
      (Duration/.getSeconds next))))

(defn execution-interval-in-seconds
  "Calculates the interval in seconds between consecutive executions.
  Uses next two executions to avoid a cronutils bug with 0th-second truncation."
  [^Instant now ^Cron parsed]
  (let [now-in-utc (time/instant->zoned-date-time-utc (time/truncate-to now :seconds))
        ^ExecutionTime execution-time (ExecutionTime/forCron parsed)
        ;; NOTE: We use next+next-next instead of previous+next because cronutils
        ;; has a bug: if current time is on 0th second, previous execution gets
        ;; truncated and Duration/between reports 1s
        next (Optional/.get (ExecutionTime/.nextExecution execution-time now-in-utc))
        next-next (Optional/.get (ExecutionTime/.nextExecution execution-time next))
        duration (Duration/between ^Instant (time/zoned-date-time->instant next)
                                   ^Instant (time/zoned-date-time->instant next-next))
        duration-s (Duration/.getSeconds duration)]
    (if (pos? duration-s)
      duration-s
      (throw (ex-info "somehow duration is <= 0!" {:now now
                                                   :next-previous next-next
                                                   :next next
                                                   :duration duration
                                                   :now-in-utc now-in-utc
                                                   :explained (explain parsed)
                                                   :schedule (Cron/.asString parsed)})))))
