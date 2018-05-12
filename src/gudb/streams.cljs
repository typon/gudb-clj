(ns gudb.streams
  (:require [cljs.core.async :refer [chan <! >! put!]]
            [cljs.cache :as cache]
            [potok.core :as ptk]
            [beicon.core :as rx])
(:require-macros [cljs.core.async.macros :refer [go go-loop]])

  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defonce app-state (ptk/store {:state
                           {:prefix ">>>"
                            :elements {}
                            :source-box {:line-els (cache/lru-cache-factory {} :threshold 50)
                                        :lineno-els (cache/lru-cache-factory {} :threshold 50)
                                        :source-lines [] :current-line 0
                                        :vert-padding 2}
                            :input-box {:value  ""}
                            :history-box {:items [] :cmds []}
                            :prog-output-box {:last ""} ; stores the last log
                            :auto-box {:items [] :curr-selected-index 0 :interactive false}
                            :changed false
                            :breakpoints []
                            :stopped-info false
                            }
                          }))

(defonce app-state-view (rx/to-atom app-state))
(defonce app-stream (ptk/input-stream app-state))

(defrecord Register-Elem [el-name el-obj]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:elements el-name] el-obj)))

(defrecord Render-Screen []
  ptk/EffectEvent
  (effect [_ state stream]
    (let [screen (get-in state [:elements :screen])]
      (debug "In Render-Screen")
      (.render screen))))

(defrecord Set-Active-Variables [vars]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:variables] vars)))
