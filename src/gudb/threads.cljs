(ns gudb.threads
  (:require
   [potok.core :as ptk]
   [gudb.utils :refer [jsx->clj]])
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defrecord Frame [
  addr
  func
  args
  file
  fullname
  line])

(defrecord Stopped [
  reason
  disp
  bkptno
  frame
  thread-id
  stopped-threads])


(defrecord Set-Stopped-Info [stopped]
  ptk/UpdateEvent
  (update [_ state]
    (do
      (debug (str "Setting stopped info: " stopped))
      (assoc-in state [:stopped-info] stopped))))

