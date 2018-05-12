(ns gudb.shadow
  (:require 
    [gudb.core :refer [main!]]
    [gudb.utils :refer [r-el r-component]]
    [gudb.layout :refer [App screen-ref]]
    [gudb.flow :refer [app-state]]
    [gudb.gdb :refer [load-gdb]]
    [cljs.pprint :refer [cl-format]]
    [shrimp-log.core :as l]
  )
  (:use-macros [shrimp-log.macros :only [debug trace spy]]))

(defn start
  "Hook to start. Also used as a hook for hot code reload."
  []
  (js/console.log "start called")
  ; (main!)
  )

(defn stop
  "Hot code reload hook to shut down resources so hot code reload can work"
  []
  ;(.destroy @screen-ref))
  (js/console.log "stop called")
  ())
