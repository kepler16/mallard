(ns k16.mallard.log
  (:import
   [java.lang System System$Logger System$Logger$Level]))

(set! *warn-on-reflection* true)

(defn get-logger ^System$Logger [name]
  (System/getLogger name))

(defmacro info [message]
  `(let [logger# (get-logger (str ~*ns*))]
     (.log logger# System$Logger$Level/INFO ~message)))

(defn -log-error
  ([^System$Logger logger ^String msg]
   (.log logger System$Logger$Level/ERROR msg))
  ([^System$Logger logger ^String msg ^Throwable ex]
   (.log logger System$Logger$Level/ERROR msg ex)))

(defmacro error
  ([message]
   `(-log-error (get-logger (str ~*ns*)) ~message))
  ([message ex]
   `(-log-error (get-logger (str ~*ns*)) ~message ~ex)))
