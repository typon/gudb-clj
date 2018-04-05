(ns gudb.source-widget
  (:require
   [clojure.string :as string]
   [gudb.utils :refer [r-el r-component]]
   [cljs.pprint :refer [cl-format]]
   [shrimp-log.core :as l]
   ["fs" :as fs]
   ["blessed" :as blessed]
   ["react" :as react]
   ["react-blessed-contrib" :refer [createBlessedComponent]]
   ["react-blessed" :as react-blessed]
   [pylon.classes])
  (:use-macros [shrimp-log.macros :only [debug trace spy]]
               [pylon.macros :only [defclass super]]))


(l/set-opts! :out-file :log-file
             :pretty-print true
             :log-level :debug)


(defonce screen-ref (atom nil))
(defonce app-state (atom {}))

(defn read-file [path]
  (let []
    (.readFileSync fs path "utf8")))

(defn renderer [coords]
  (let [self (js* "this")
        xi (.-xi coords)
        yi (.-yi coords)
        xl (.-xl coords)
        yl (.-yl coords)
        width (- xl xi)
        height (- yl yi)
        row-offset (atom 0) ; which row are we on?
        row-index (atom 0) ;
        last-left (atom 0) ; the left coord of the last element
        last-elem-newline? (atom false) ; the left coord of the last element
        ]
    (fn [el i]
      (let [last (.getLastCoords self i)]
        (set! (.-shrink el) true)
        (spy :trace (str "content: " (.-content el)))
        (if (nil? last)
          (do ; this is the first element we are rendering
            (set! (.. el -position -left) 0)
            (set! (.. el -position -top) 0))
          (let [last-xl (.-xl last)
                last-xl' (if @last-elem-newline? 0 last-xl)
                is-newline (= (.-content el) ":NL")
                last-left (- last-xl' xi)
                ; _ (swap! last-left #(+ % last-xl))
                new-left (if is-newline 0 last-left)]
            (reset! last-elem-newline? is-newline)
            (if is-newline
              (do
                (set! (.. el -hidden) true)
                (swap! row-offset inc)))
            ; (set! (.. el -position -left) (spy :trace new-left))
            ; (set! (.-left (.-position el)) (spy :trace new-left))
            (set! (.. el -position -left) (spy :trace new-left))
            (set! (.. el -position -top) (spy :trace @row-offset))))))))


(defn code->elements [code key-offset]
  ;; (let [
  ;;       [code'] [(string/split-lines code)]
  ;;       [code''] [(interleave code' (repeat "\n"))]
  ;;       [code'''] [(concat code'')]
  ;;       [words] [(mapcat #(string/split % #" ") code''')]
  ;;       ]
    (let [[code'] [(interleave (string/split-lines code) (repeat ":NL"))]
         ;[code'''] [(concat code'')]
          [words] [(mapcat #(string/split % #"(?=[ ])") code')]
          ]

    (vector (map-indexed (fn [index word] (r-el "text" {:key (+ key-offset index) :content word})) words))))
(def SourceBox
  (r-component "SourceBox"
               :render (fn [props] (r-el "layout" {:key 0
                                                :layout "inline"
                                                :width "50%"
                                                :height "50%"
                                                :style {:fg "white" :bg "black"},
                                                :renderer renderer
                                                }
                                         (code->elements (read-file "./sample_program/simple.c") 4)
                                         ))))


(def App
  (r-component "App"
               :render (fn [props]
                          (r-el SourceBox props)
                          ;(r-el SourceBox (merge props {:key 1 :height "50%" :width "50%"}))))))
                          )))


(defn render [state]
  (do
    (trace "Rendering...........")
    (react-blessed/render (r-el App state) @screen-ref)))

(defn main! []
  (let [[screen] [(blessed/screen (clj->js {:smartCSR true "cursor.blink" true :dockBorders true :log "blessedz.log"}))]
        ]
    (debug "Starting GUDB!")
    (reset! screen-ref screen)
    (swap! app-state assoc-in [:elements :screen] @screen-ref)
    (.key @screen-ref (clj->js ["q" "C-c"]) (fn [ch, key] (js/process.exit 0)))

    (render @app-state)))

; Every time state changes, call render function again to redraw everything.
(add-watch app-state :redraw
           (fn [_ _ old-state new-state]
    ; Only re-render when old-state != new-state, ignoring the :elements
    ; because they get regenerated every time react renders. If we re-render
    ; then we get in infinite loop.
             (when-not
              (=
               (dissoc old-state :elements)
               (dissoc new-state :elements))
               (render new-state))))
