(ns timeshift-logger.core
  (:use [flume-node.core :as flume :only [defsink defsource defagent]])
  (:require [taoensso.carmine :as car :refer (wcar)]
            [environ.core :refer [env]]
            [clojure.edn :as edn])
  (:import [org.apache.flume.event EventBuilder]
           [java.net ServerSocket]
           [java.io BufferedInputStream ObjectInputStream]
           [java.nio.charset StandardCharsets]
           [java.util Date]
           [org.apache.log4j Level]))

(def redis-conn {:pool {} :spec {:host "localhost" :port 6379 :db 8}})
(defmacro wcar* [& body] `(car/wcar redis-conn ~@body))

(defn- split-by-char [s delimiter limit]
  (loop [res [], pos 0, counter 0]
    (let [delim-pos (.indexOf s (int delimiter) pos)]
      (if (< delim-pos 0)
        (conj res (.substring s pos))
        (if (< counter (dec limit))
          (recur (conj res (.substring s pos delim-pos)) (inc delim-pos) (inc counter))
          (conj res (.substring s pos)))))))

(defn print-log [log]
  (println (format "%s %-5s %s %s"
             (:date log) (:level log) (:loggerName log) (:message log)))
  (if-let [stack-trace (:throwableStrRep log)]
    (doseq [line stack-trace]
      (println line))))

(def log-server (atom nil))

(defn parse-log [e]
  {:NDC (.getNDC e)
   :level (.toString (.getLevel e))
   :loggerName (.getLoggerName e)
   :date (Date. (.getTimeStamp e))
   :message (.getMessage e)
   :throwableStrRep (vec (.getThrowableStrRep e))})

(defsource log-source
  :start (fn []
           (println "start")
           (let [server (ServerSocket. (or (env :server-port) 5140))
                 socket (.accept server)
                 ois (ObjectInputStream. (BufferedInputStream. (.getInputStream socket)))]
             (reset! log-server {:server server :socket socket :input-stream ois})))

  :process (fn []
             (let [logging-event (.readObject (:input-stream @log-server))]
               (EventBuilder/withBody (pr-str (parse-log logging-event))
                 StandardCharsets/UTF_8)))

  :stop (fn []
          (.close (:input-stream @log-server))
          (.close (:server @log-server))
          (reset! log-server nil)))

(defsink log-sink
  :process (fn [event]
             (let [log (edn/read-string (String. (.getBody event)))
                   user-id (:NDC log)]
               (when-not (empty? user-id)
                 (let [histories (if-let [logs (wcar* (car/get user-id))]
                                   (conj logs log)
                                   [log])]
                   (wcar*
                     (car/set user-id histories)
                     (car/expire user-id (or (env :retention-period) 60)))
                   (when (= (:level log) (or (env :level-threshold) "FATAL"))
                     (doseq [hist histories]
                       (print-log hist))
                     (wcar* (car/del user-id))))))))
               

(defagent :timeshift-logserver
  (flume/source :syslog-source
    :type "timeshift-logger.core/log-source"
    :channels :memory-channel)
  (flume/sink :thimeshift-log-sink
        :type "timeshift-logger.core/log-sink"
        :channel :memory-channel)
  (flume/channel :memory-channel
           :type "memory"
           :capacity 1000
           :transactionCapacity 100))

(defn -main []
  (let [application (flume/make-app)]))

