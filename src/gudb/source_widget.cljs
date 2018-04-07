(ns gudb.source-widget
  (:require
   [clojure.string :as string]
   [gudb.utils :refer [r-el r-component]]
   [gudb.streams :refer [dispatch app-state transform]]
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
;; (defonce app-state (atom {}))
(defonce cnt (atom 0))

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

(defmethod transform :source-box-update
  [state _]
  (let [source-box (get-in state [:elements :source-box])
        line (blessed/box (clj->js {:height "0%+1" :parent source-box :shrink true :top @cnt}))
        txt (blessed/text (clj->js {:content (str "ree" @cnt) :left 0 :parent line :hoverText "REEZ"}))
        txt2 (blessed/text (clj->js {:content "ray" :left 10 :parent line  :hoverText "RAYS" }))
        ; line-text (str "ree" @cnt "      " "ray")
        ; box-content (.-content source-box)
        ; full-content (str box-content "\n" line-text)
        ; txt3 (blessed/text (clj->js {:content "ray" :hoverText "RAYS" :width "100%" :parent source-box}))
       ]
    ; (.pushItem source-box (blessed/text {"ree" "ss"}))
    ; (.pushItem source-box txt3)
    ;; (.focus source-box)
    ; (.setHover (aget (.-items source-box) 1) "OUEA")
    ; (.setContent source-box full-content)
    ; (debug "Sourcebox: " (.-content source-box))
    (set! (.-scroll source-box) (fn [offset always]
                                  (let [-offset (* -1 offset)
                                        source-box-height (.-height source-box)
                                        first-child (first (.-children source-box))
                                        first-child-top (.-top first-child)
                                        last-child (last (.-children source-box))
                                        last-child-top (.-top last-child)]
                                    (debug "Scrolled: " offset )
                                    (debug "Scrolled: neg " -offset )
                                    (when (and (<= (+ first-child-top -offset) 0) (>= (+ last-child-top -offset) (- source-box-height 1))
                                      (doseq [child (.-children source-box)]
                                      (set! (.-top child) (+ (.-top child) -offset))))))))
    ; (set! (.-top source-box) "50%")
    (debug "children: " (count (.-children source-box)))
    (.render @screen-ref)
    (swap! cnt inc)
    (assoc-in state [:source-box-value] "REE")))


(defmethod transform :register-elem
  [state value]
  (assoc-in state [:elements (:el-name value)] (:el-obj value)))

(def SourceBox
  (r-component "SourceBox"
               ;; :componentDidMount (fn [] (this-as this
               ;;                             (.insertItem (.. this -refs -me) 0 (.text blessed #js {:content "TESTING"}))
               ;;                             (.insertItem (.. this -refs -me) 1 (.text blessed #js {:content "TESTING"}))
               ;;                             (reset! app-state {})
               ;;                             ; (.pushItem (.. this -refs -me) (blessed/text #js {:content "xx" :style {:fg "white" :bg "RED"}}))))
               ;;                             ))
               :render (fn [props] (r-el "box" {
                                                 :ref (fn [el] (dispatch :register-elem {:el-name :source-box :el-obj el}))
                                                :key 0
                                                ; :left 0
                                                :width "50%"
                                                :height "0%+10"
                                                :style {:fg "red" :bg "magenta", :hover {:bg "black"}},
                                                ; :search true,
                                                ; :mouse true,
                                                ; :input true,
                                                :keys true,
                                                ; :vi true,
                                                :scrollable true,
                                                ; :scrollbar {"ch" "|"},
                                                ; :border {:type "line"}
                                                ; :content "Hello"

                                                ; :draggable true
                                                :input true
                                                ; :onScroll (fn [event] (dispatch :source-box-scroll event))
                                                :onScroll (fn [event dir] (debug "Scroll: " event dir))
                                                ; :items ["hello" "there"]
                                                ; :hoverText "OYEEEEE"

                                                }
                                         ))))


(def App
  (r-component "App"
               :render (fn [props]
                          (r-el SourceBox merge (props {:height "100%" :width "100%"}))
                         ; (r-el SourceBox (merge props {:key 1 :height "10%" :width "10%"}))
                          )))


(defn render [state]
  (do
    (trace "Rendering...........")
    (react-blessed/render (r-el App state) @screen-ref)))

(defn main! []
  (let [[screen] [(blessed/screen (clj->js {:smartCSR true "cursor.blink" true :log "blessedz.log" :autoPadding false}))]
        ]
    (debug "Starting GUDB!")
    (reset! screen-ref screen)
    (swap! app-state assoc-in [:elements :screen] @screen-ref)
    (.key @screen-ref (clj->js ["q" "C-c"]) (fn [ch, key] (js/process.exit 0)))
    (.key @screen-ref (clj->js ["b"]) (fn [ch, key] (dispatch :source-box-update nil)))
    (.key @screen-ref (clj->js ["l"]) (fn [ch, key] (debug (str "Width: " (.-width @screen-ref) "Hiegth: " (.-height @screen-ref)))))
    (.enableInput @screen-ref)
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
