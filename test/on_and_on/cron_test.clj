(ns on-and-on.cron-test
  (:require
   [on-and-on.time :as time]
   [on-and-on.cron :as cron]
   [clojure.test :refer [deftest testing is]]))

(deftest parsing-and-extracting-data
  (testing "parses an expression and calculates next execution values"
    (let [schedule (cron/parse "0 /5 * * * ?")
          now (time/str->instant "2022-03-13T10:20:15Z")]
      (is (= "2022-03-13T10:25:00Z" (str (cron/next-execution now schedule))))
      (is (= 285 (cron/next-execution-in-seconds now schedule)))
      (is (= 300 (cron/execution-interval-in-seconds now schedule)))))

  (testing "complicated expression using exact beginning of minute"
    (let [schedule (cron/parse "0 0,5,10,15,20,25,30,35,40,45,50,55  * * * ?")
          now (time/str->instant "2022-03-13T10:20:00Z")]
      (is (= "2022-03-13T10:25:00Z" (str (cron/next-execution now schedule))))
      (is (= 0 (cron/next-execution-in-seconds now schedule)))
      (is (= 300 (cron/execution-interval-in-seconds now schedule)))))

  (testing "short schedules - every 2s"
    (let [schedule (cron/parse "/2 * * * * ? *")
          now (time/str->instant "2022-03-13T10:20:15.120Z")]
      (is (= "every 2 seconds" (cron/explain schedule)))
      (is (= "2022-03-13T10:20:16Z" (str (cron/next-execution now schedule))))
      (is (= 1 (cron/next-execution-in-seconds now schedule)))
      (is (= 2 (cron/execution-interval-in-seconds now schedule)))))

  (testing "short schedules - every 5s"
    (let [schedule (cron/parse "/5 * * * * ? *")
          now (time/str->instant "2022-03-13T10:20:15Z")]
      (is (= "every 5 seconds" (cron/explain schedule)))
      (is (= "2022-03-13T10:20:20Z" (str (cron/next-execution now schedule))))
      (is (= 0 (cron/next-execution-in-seconds now schedule)))
      (is (= 5 (cron/execution-interval-in-seconds now schedule)))))

  (testing "long schedules"
    (let [schedule (cron/parse "0 0 2 * * ?")
          now (time/str->instant "2022-03-13T10:20:15Z")]
      (is (= "at 02:00" (cron/explain schedule)))
      (is (= "2022-03-14T02:00:00Z" (str (cron/next-execution now schedule))))
      (is (= 56385 (cron/next-execution-in-seconds now schedule)))
      (is (= (* 24 60 60) (cron/execution-interval-in-seconds now schedule))))))

(deftest testing-schedule-matching
  (testing "core loop schedule"
    (let [schedule (cron/parse "0 /5 * * * ?")]
      (is (cron/matches-schedule? (time/str->instant "2021-02-03T00:05:00Z") schedule))
      (is (not (cron/matches-schedule? (time/str->instant "2021-02-03T00:05:03Z") schedule)))
      (is (not (cron/matches-schedule? (time/str->instant "2021-02-03T00:03:00Z") schedule))))))

(deftest bugs
  (testing "short schedules get interval of 1"
    (testing "every 3s starting at 0th second"
      (let [schedule (cron/parse "/3 * * * * ?")
            now (time/str->instant "2022-03-16T19:45:00.357135Z")]
        (is (= "every 3 seconds" (cron/explain schedule)))
        (is (= "2022-03-16T19:45:03Z" (str (cron/next-execution now schedule))))
        (is (= 0 (cron/next-execution-in-seconds now schedule)))
        (is (= 3 (cron/execution-interval-in-seconds now schedule)))))

    (testing "every 3s starting so that next falls at 0th second"
      (let [schedule (cron/parse "/3 * * * * ?")
            now (time/str->instant "2022-03-16T19:44:57.357135Z")]
        (is (= "every 3 seconds" (cron/explain schedule)))
        (is (= "2022-03-16T19:45:00Z" (str (cron/next-execution now schedule))))
        (is (= 0 (cron/next-execution-in-seconds now schedule)))
        (is (= 3 (cron/execution-interval-in-seconds now schedule)))))))
