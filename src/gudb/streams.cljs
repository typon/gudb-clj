(ns gudb.streams
  (:require [cljs.core.async :refer [chan <! >! put!]]
            [cljs.cache :as cache])
(:require-macros [cljs.core.async.macros :refer [go go-loop]])

  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defonce actions (chan))


(defonce app-state (atom {:prefix ">>>"
                          :elements {}
                          :source-box {:line-els (cache/lru-cache-factory {} :threshold 50)
                                       :source-lines []}
                          :source-box-value {}
                          :input-box-value ""
                          :history-box-value []
                          :prog-output-box-value "" ; stores the last log
                          :auto-box {:value [] :curr-selected-index 0 :interactive false}
                          :changed false}))

;; Components call this function to request state changing.
(defn dispatch
  "Dispatch new action. Type should be keyword."
  ([type] (dispatch type nil))
  ([type data]
   (put! actions [type data])))

;; All state changes should be done via this method.
(defmulti transform
  "Transform state by action. Return updated state."
  (fn [state data dispatch action-type] action-type))

;; Start actions pipeline
(go-loop []
  (when-let [a (<! actions)]
    (let [[type data] a]
      (trace (str "Handle action: " type "\nData: " data))
      (swap! app-state transform data dispatch type))
    (trace (str "State: " @app-state))
    (recur)))
