(ns timeshift-logger.core
  (:use [flume-node.core :as flume :only [defsink defsource defagent]])
  (:require [environ.core :refer [env]])
  (:import [java.io ByteArrayInputStream]
           [org.jboss.netty.handler.codec.serialization ObjectDecoderInputStream]
           [org.apache.log4j ConsoleAppender PatternLayout]))

(def console-appender (ConsoleAppender. (PatternLayout. "%x %d{yyyy/MM/dd HH:mm:ss.SSS} %5p %c{1} - %m%n")))
(.activateOptions console-appender)

(defsink print-sink
  :process (fn [event]
             (let [odis (ObjectDecoderInputStream. (ByteArrayInputStream. (.getBody event)))
                   logs (.readObject odis)]
               (println "==================================")
               (doseq [log logs]
                 (.append console-appender log))
               (println "=================================="))))

(defagent :timeshift-logserver
  (flume/source :log4j-socket-source
    :type "net.unit8.timeshift_logger.Log4jSocketSource"
    :port 5140
    :channels :memory-channel)
  (flume/sink :thimeshift-log-sink
        :type "timeshift-logger.core/print-sink"
        :channel :memory-channel)
  (flume/channel :memory-channel
           :type "memory"
           :capacity 1000
           :transactionCapacity 100))

(defn -main []
  (let [application (flume/make-app)]))

