(ns on-and-on.time
  (:import
   (java.time Instant ZonedDateTime ZoneId)
   (java.time.temporal ChronoUnit)))

(set! *warn-on-reflection* true)

(defn now
  "Returns the current instant."
  ^Instant []
  (Instant/now))

(def UTC (ZoneId/of "UTC"))

(defn instant->zoned-date-time-utc
  "Converts an Instant to a ZonedDateTime in UTC."
  ^ZonedDateTime [^Instant instant]
  (ZonedDateTime/ofInstant instant UTC))

(defn zoned-date-time->instant
  "Converts a ZonedDateTime to an Instant."
  ^Instant [^ZonedDateTime zoned-date-time]
  (ZonedDateTime/.toInstant zoned-date-time))

(defn str->instant
  "Parses an ISO-8601 string into an Instant."
  ^Instant [^String s]
  (Instant/parse s))

(defn truncate-to
  "Truncates an Instant to the given unit (:minutes or :seconds)."
  ^Instant [^Instant inst unit]
  (Instant/.truncatedTo inst (case unit
                               :minutes ChronoUnit/MINUTES
                               :seconds ChronoUnit/SECONDS)))
