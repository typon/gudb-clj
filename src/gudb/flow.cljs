(ns gudb.flow
  (:require [cljs.core.async :as a])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace debug spy]]))

(defonce app-state (atom {:elements {}
                          :input-box-value ""
                          }))
(defonce actions (a/chan))

;; Components call this function to request state changing.
(defn dispatch
  "Dispatch new action. Type should be keyword."
  ([type] (dispatch type nil))
  ([type data]
   (a/put! actions [type data])))

;; All state changes should be done via this method.
(defmulti transform
  "Transform state by action. Return updated state."
  (fn [state data dispatch action-type] action-type))

;; Start actions pipeline
(go-loop []
         (when-let [a (a/<! actions)]
           (let [[type data] a]
             (trace (str "Handle action: " type "\nData: " data))
             (swap! app-state transform data dispatch type))
             (trace (str "State: " @app-state))
          (recur)))

;; State here is map, not atom!
(defmethod transform :input-box-keypress
  [state key-obj]
  ; (do 
  ;   (trace (.-value (get-in state [:elements :input-box])))
  ;   state)
  (let [[curr-text] [(.-value (get-in state [:elements :input-box]))]
        [output-box] [(get-in state [:elements :output-box])]
        [screen] [(get-in state [:elements :screen])]
       ]
    (.setContent output-box curr-text)
    (.render screen)
    (assoc state :input-box-value curr-text)
    ))

(defmethod transform :register-elem
  [state value]
  (assoc-in state [:elements (:el-name value)] (:el-obj value))
)
