(ns gudb.flow
  (:require [cljs.core.async :as a]
            [gudb.gdb :refer [gdb-cmds]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace debug spy]]))

(defonce app-state (atom {:elements {}
                          :input-box-value "Empty!"
                          :changed false
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
             ;(trace (str "Handle action: " type "\nData: " data))
             (swap! app-state transform data dispatch type))
             ; (trace (str "State: " @app-state))
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
    ; (.setContent output-box curr-text)
    ; (dispatch :set-box-contents {:el output-box :value curr-text})
    ; (when-not (= (:input-box-value state) curr-text) (assoc state :input-box-value curr-text))))
    ; (a/put! gdb-cmds curr-text)
    (assoc state :input-box-value curr-text)
    ))

(defmethod transform :input-box-reset
  [state _]
  (let [[input-box] [(get-in state [:elements :input-box])]
       ]
    ; (.setContent output-box curr-text)
    ; (dispatch :set-box-contents {:el output-box :value curr-text})
    ; (when-not (= (:input-box-value state) curr-text) (assoc state :input-box-value curr-text))))
    ; (a/put! gdb-cmds curr-text)
    (.clearValue input-box)
    (assoc state :input-box-value "")))


(defmethod transform :set-box-contents
  [state args]
  (let [[box-elem text] [(:el args) (:value args)]
       [screen] [(get-in state [:elements :screen])]
       ]
    (.setContent box-elem text)
    (.render screen)
    state))

(defmethod transform :register-elem
  [state value]
  (assoc-in state [:elements (:el-name value)] (:el-obj value))
)
