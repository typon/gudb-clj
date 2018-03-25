(ns gudb.flow
  (:require [gudb.gdb :refer [gdb-chan]] 
            [cljs.core.async :refer [chan <! >! put!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])

  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defonce app-state (atom {:elements {}
                          :input-box-value ""
                          :history-box-value []
                          :auto-box {:value [] :curr-selected-index 0 :interactive false}
                          :changed false
                          }))
(defonce actions (chan))

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

;; State here is map, not atom!
(defmethod transform :input-box-keypress
  [state key-obj]
  ; (do 
  ;   (trace (.-value (get-in state [:elements :input-box])))
  ;   state)
  (let [[curr-text] [(.-value (get-in state [:elements :input-box]))]
        [output-box] [(get-in state [:elements :output-box])]
        [auto-box] [(get-in state [:elements :autocomplete-box])]
        [screen] [(get-in state [:elements :screen])]
       ]
    ; (.setContent output-box curr-text)
    ; (dispatch :set-box-contents {:el output-box :value curr-text})
    ; (when-not (= (:input-box-value state) curr-text) (assoc state :input-box-value curr-text))))
    ; (a/put! gdb-cmds curr-text)
    ;(assoc state :input-box-value curr-text)
    (case (get key-obj "full")
      ; do nothing if enter was just pressed. This is handled in submit
      "return" ()
      "enter" ()
      "tab" (dispatch :auto-box-scroll :down) 
      "S-tab" (dispatch :auto-box-scroll :up) 
      ; Default case
      (do 
        (dispatch :auto-box-update curr-text) 
        ))
    (assoc state :input-box-value curr-text)))

;; State here is map, not atom!
(defmethod transform :auto-box-update
  [state curr-text]
  (let [[auto-box] [(get-in state [:elements :autocomplete-box])]
        [history-strings] [(:history-box-value state)]
        [screen] [(get-in state [:elements :screen])]
        [state'] [(assoc-in state [:auto-box :interactive] false)]
       ]
    (.setFront auto-box)
    (case curr-text
      "" (assoc-in state' [:auto-box :value] [])

      ; Default case
      ; Figure out the matches to the current
      ; text from the history box list and remove the ones that dont match
      (assoc-in state' [:auto-box :value] (into [] (distinct (remove nil? (map (fn [s] (re-matches (re-pattern (str "^" curr-text ".*")) s)) history-strings))))))))
    
(defmethod transform :auto-box-reset
  [state _]
  (let [[auto-box] [(get-in state [:elements :autocomplete-box])]
        [state'] [(assoc-in state [:auto-box :interactive] false)]
       ]
    (.clearItems auto-box)
    (set! (.-selected auto-box) 0)
    (assoc-in state' [:auto-box :value] [])))


(defmethod transform :auto-box-scroll
  [state direction]
  (let [[auto-box] [(get-in state [:elements :autocomplete-box])]
        [screen] [(get-in state [:elements :screen])]
        [offset] [(case direction :down 1 :up -1)]
       ]
    (.move auto-box offset)
    ; TODO: Making this render after select up/down declarative.
    (.render screen)
    (let [[curr-selected][(when-let [item (aget (.-items auto-box) (spy :info (.-selected auto-box)))] (.-content (spy :info item)))]]
      (dispatch :input-box-reset curr-selected))
    (assoc-in state [:auto-box :interactive] true)))

(defmethod transform :input-box-reset
  [state text]
  (let [[input-box] [(get-in state [:elements :input-box])]
       ]
    (.setValue input-box text)
    (.readInput input-box nil)
    (assoc state :input-box-value text)))

(defmethod transform :input-box-cancel
  [state _]
  (let [[input-box] [(get-in state [:elements :input-box])]
       ]
    (dispatch :auto-box-reset nil)
    (.clearValue input-box)
    (assoc state :input-box-value "")))

(defmethod transform :input-box-submit
  [state text]
  (case text
    ("" nil) (do 
         (dispatch :input-box-reset "") 
         (identity state))
    (do 
      (dispatch :history-box-append text) 
      (dispatch :input-box-reset "") 
      (dispatch :auto-box-reset nil)
      (go (>! gdb-chan {:topic :cmd-sent :cmd text}))
      (identity state))))
    

(defmethod transform :history-box-append
  [state item]
  (let [[history-box] [(get-in state [:elements :history-box])]]
      (.pushItem history-box item)
      (.setScrollPerc history-box 100)
      (update-in state [:history-box-value] (fn [lst] (conj lst item)))))


; (defmethod transform :set-box-contents
;   [state args]
;   (let [[box-elem text] [(:el args) (:value args)]
;        [screen] [(get-in state [:elements :screen])]
;        ]
;     (.setContent box-elem text)
;     (.render screen)
;     state))

(defmethod transform :register-elem
  [state value]
  (assoc-in state [:elements (:el-name value)] (:el-obj value))
)
