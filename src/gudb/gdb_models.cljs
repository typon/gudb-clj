(ns gudb.gdb-models
  (:require
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

(def make-bpoint [result-record]
  )
