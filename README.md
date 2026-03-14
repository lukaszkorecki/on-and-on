# on-and-on

Clojure scheduler library with fixed-rate and cron-based task scheduling, built on [CronScheduler](https://github.com/TimeAndSpaceIO/CronScheduler) and [cron-utils](https://github.com/jmrozanec/cron-utils).

> *Oh, on and on and on and on*
> *My cypher keeps moving like a rolling stone*
> - Erykah Badu


## Rationalle

Use this if:

- you want to use CRON expressions, but you need to support sub-minute periods (e.g. every 15s)
- your recurring tasks can't tolerate clock drift - `on-and-on` uses a special scheduler class (`CronScheduler`) which is not succeptible to clock drift - which unfortunately affects `j.u.c.ScheduledThreadPoolExecutor`, [read more here](https://leventov.medium.com/cronscheduler-a-reliable-java-scheduler-for-external-interactions-cb7ce4a4f2cd) - this matters if you have strict requirements of periodical job execution
- you looked at [Quartz Scheduler](https://github.com/quartz-scheduler/quartz) but you've decided that it's really heavy and comes with a lot of features and extensions that you don't need
- you're using Component and you don't want to wrap `j.u.c.ScheduledThreadPoolExecutor` for the nth time

## Installation

-  `deps.edn` via `git`:

```clojure
{:deps {com.github.lukaszkorecki/on-and-on {:git/tag "..." :git/sha "..."}}}
```

- Clojars

TODO

## Usage


### Fixed-rate scheduling (Component)

```clojure
(require '[on-and-on.component :as scheduler]
         '[com.stuartsierra.component :as component])

(def sys
  (component/map->SystemMap
   {:scheduler (scheduler/create-pool {:name "my-app"})
    :my-task (component/using
              (scheduler/create-task {:name "heartbeat"
                                      :period-ms 5000
                                      :handler (fn [ctx] (println "tick"))})
              [:scheduler])}))
```

### Cron-based scheduling (Component)

```clojure
(def sys
  (component/map->SystemMap
   {:scheduler (scheduler/create-pool {:name "my-app"})
    :my-task (component/using
              (scheduler/create-task {:name "daily-cleanup"
                                      :schedule "0 0 2 * * ?"
                                      :handler (fn [ctx] (println "cleanup"))})
              [:scheduler])}))
```

### Standalone (no Component)

```clojure
(require '[on-and-on.scheduler :as scheduler])

(let [pool (scheduler/make-scheduler-pool {:name "standalone"})]
  ;; fixed-rate
  (scheduler/schedule-task pool {:handler #(println "tick") :period-ms 1000})
  ;; cron
  (scheduler/schedule-cron-task pool {:name "every-5min"
                                       :handler #(println "cron tick")
                                       :schedule "0 /5 * * * ?"})
  ;; ... later
  (scheduler/shutdown-scheduler-pool pool))
```

## Testing

```bash
clojure -M:test
```

## License

MIT
