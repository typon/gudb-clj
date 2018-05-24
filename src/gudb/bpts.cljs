(ns gudb.bpts
  (:require
   [potok.core :as ptk]
   [gudb.utils :refer [jsx->clj]])
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defrecord BPoint
    [number
     type
     catch-type
     disp
     enabled
     addr
     func
     file
     filename
     line
     at
     pending
     evaluated-by
     thread
     task
     cond
     ignore
     enabledmask
     pass
     original-location
     times
     installed
     thread-groups
     what
     ])

; (def make-bpoint [result-record])

(defrecord Create [record-map]
  ptk/UpdateEvent
  (update [_ state]
    (let [bp (map->BPoint record-map)]
      (update-in state [:breakpoints] #(conj % bp)))))

(defrecord Add [bpt]
  ptk/UpdateEvent
  (update [_ state]
    (do
    (debug "Creating new bpt")
    (update-in state [:breakpoints] #(conj % bpt)))))

