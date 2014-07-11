(defproject net.unit8/timeshift-logger "0.1.0-SNAPSHOT"
  :description "time shift loggin for log4j."
  :dependencies [[net.unit8/clj-flume-node "0.1.0-SNAPSHOT"]
                 [com.taoensso/carmine "2.6.2"]
                 [log4j "1.2.17"]
                 [environ "0.5.0"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :plugins [[lein-localrepo "0.5.3"]
            [lein-environ "0.5.0"]]
  :main timeshift-logger.core)
