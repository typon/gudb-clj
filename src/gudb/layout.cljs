(ns gudb.layout
  (:require
   [shrimp-log.core :as l]
   [gudb.utils :refer [r-el r-component]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :refer [app-state]]
   [gudb.source-widget :as sbox]
   [gudb.input-widget :as ibox]
   [gudb.progout-widget :as pbox]
   [gudb.history-widget :as hbox]
   ; [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all]]
   ; ["blessed" :as blessed]
   [clojure.string :as str])
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))

(defonce screen-ref (atom nil))

;; (def HistoryBox
;;   (r-component "HistoryBox"
;;                :render (fn [props] (r-el "list" {:ref (fn [el] (ptk/emit! app-state (strm/->Register-Elem :history-box el)))
;;                                                  :key (:key props)
;;                                                  :name "history-box"
;;                                                  :bottom 2,
;;                                                  :top 0,
;;                                                  :left 0,
;;                                                  :width "100%-2",
;;                                                  :height "100%-4",
;;                                                  :invertSelected false,
;;                                                  :alwaysScroll true,
;;                                                  :scrollable true,
;;                                                  :scrollbar {"ch" "|", "inverse" true},
;;                                                  :search true,
;;                                                  :mouse true,
;;                                                  :keys true,
;;                                                  :vi true,
;;                                                  :style {:fg "white",
;;                                                          :bg "magenta",
;;                                                          :selected {:fg "white" :bg "magenta"}}
;;                                                  :tags true}))))


;; (defn input-box [& {:keys [key]}]
;;   (r-el "textbox" {:ref (fn [el] (ptk/emit! app-state (strm/->Register-Elem :input-box el)))
;;                    :key key
;;                    :name "input-box"
;;                    :bottom 0
;;                    :left 0
;;                    :height 1,
;;                    :mouse true,
;;                    :tags true
;;                    :keys true
;;                    :inputOnFocus true
;;                    :style (:input-box color-scheme)
;;                    :onKeypress (fn [_ key-obj] (ptk/emit! app-state (cmd/->Input-Box-Keypress (js->clj key-obj))))
;;                    :onSubmit (fn [text]
;;                                (do
;;                                  (dispatch :input-box-submit text)))
;;                    :onCancel (fn [_]
;;                                (do
;;                                  (dispatch :input-box-cancel nil)))}))


(def CommandContainer
  (r-component "CommandContainer"
               :render (fn [props]
                         (r-el "element" {:label "Commands"
                                          :height (:height props)
                                          :width (:width props)
                                          :bottom (:bottom props)
                                          :left (:left props)
                                          :border {:type "line"}}
                               (r-el ibox/InputBox {:key 0})
                               (r-el "line"  {:orientation "horizontal", :bottom 1, :key 1, :height 1})
                               (r-el hbox/HistoryBox {:value (:input-box-value props) :key 2})
                               (r-el ibox/AutoCompleteBox (assoc (:auto-box props) :key 3))))))

(def MainLayout
  (r-component "MainLayout"
               :render (fn [props]
                         (r-el "element" {}
                               ; (r-el sbox/SourceBox (merge props {:key 0 :height "50%" :width "50%" :left 0 :top 0}))
                               (r-el CommandContainer (merge props {:key 1 :height "50%" :width "50%" :left 0 :bottom 0}))
                               ;(r-el ProgramOutputBox (merge props {:key 2 :height "50%" :width "50%" :right 0 :bottom 0}))))))
                               ))))

(def App
  (r-component "App"
               :render (fn [props]
                         (r-el MainLayout props))))
