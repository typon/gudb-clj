(ns gudb.threads
  (:require
    [potok.core :as ptk]
    [beicon.core :as rx]
    [gudb.utils :refer [jsx->clj file-exists?]]
    [gudb.streams :as strm]
    ;[gudb.source-widget :as sbox]
    )

  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defn make-frame [argmap]
  (let [modified (update argmap :line (comp int dec))] ; decrease line number by 1 to
                                                       ; match zero-indexed lines in gudb
    (map->Frame modified)))

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
