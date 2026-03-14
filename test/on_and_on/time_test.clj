(ns on-and-on.time-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [on-and-on.time :as time])
  (:import
   (java.time Instant ZonedDateTime)))

(deftest now-test
  (testing "returns an Instant"
    (is (instance? Instant (time/now)))))

(deftest instant->zoned-date-time-utc-test
  (testing "converts instant to UTC ZonedDateTime"
    (let [instant (time/str->instant "2022-03-13T10:20:15Z")
          zdt (time/instant->zoned-date-time-utc instant)]
      (is (instance? ZonedDateTime zdt))
      (is (= "UTC" (str (.getZone zdt))))
      (is (= instant (time/zoned-date-time->instant zdt))))))

(deftest str->instant-test
  (testing "parses ISO-8601 string"
    (let [inst (time/str->instant "2022-03-13T10:20:15Z")]
      (is (instance? Instant inst))
      (is (= "2022-03-13T10:20:15Z" (str inst))))))

(deftest truncate-to-test
  (testing "truncates to minutes"
    (let [inst (time/str->instant "2022-03-13T10:20:15.123Z")]
      (is (= "2022-03-13T10:20:00Z" (str (time/truncate-to inst :minutes))))))
  (testing "truncates to seconds"
    (let [inst (time/str->instant "2022-03-13T10:20:15.123Z")]
      (is (= "2022-03-13T10:20:15Z" (str (time/truncate-to inst :seconds)))))))
