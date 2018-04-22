(ns gudb.utils
  "Defines general purpose utility functions related to react, gdb and other dependencies"
  (:require
    [clojure.string :as str]
    [cljs.core.async :refer [chan <! >! go-loop close! go]]
    ["blessed" :as blessed]
    ["react" :as react]
    ["create-react-class" :as create-react-class]
    ["react-blessed" :as react-blessed]
    ["fs" :as fs])
  (:use-macros [shrimp-log.macros :only [debug trace spy]]))




(defn r-el
  "Create react.js elements, converting clojure values into Javascript"
  [type props & children]
  (react/createElement type (clj->js props) children))

;; Simple page using several elements
;; (def vdom (r-el "div" {}
;;             (r-el "p" {} "Hello from React")
;;             (r-el "img" {:src "/parrot.JPEG"})))


(defn r-component
  "Creates react.js components"
  [name & {:keys [render componentDidMount shouldComponentUpdate]}]
  (create-react-class
   (let [base-obj {:displayName name
                       :render (fn []
                                 (this-as t
                                   (render (js->clj (.-props t) :keywordize-keys true))))}
        base-obj (if (some? componentDidMount) (assoc base-obj :componentDidMount componentDidMount) base-obj)
        base-obj (if (some? shouldComponentUpdate) (assoc base-obj :shouldComponentUpdate shouldComponentUpdate) base-obj)]
    (spy :info (clj->js base-obj)))))


(defn timeout [ms]
  (let [c (chan)]
    (js/setTimeout (fn [] (close! c)) ms)
    c))

(defn throttle [c ms]
  (let [c' (chan)]
    (go
      (while true
        (>! c' (<! c))
        (<! (timeout ms))))
    c'))

(defn unescape
  "Unescape double back-slash escaped characters in a string."
  [s]
  (when s
    (str/replace s #"\\(.)" "$1")))

(defn fix-newline-escaping
  "Fix weirdly escaped newlines such as: 'Hello\\n'"
  [s]
  (when s
    (str/replace s #"\\n" "\n")))

(defn read-file [path]
  (let []
    (.readFileSync fs path "utf8")))

(defn clamp "Clamp n to (min-n, max-n)"
  [n min-n max-n]
  (min max-n (max min-n n)))

(defn jsx->clj
  [x]
  (into {} (for [k (.keys js/Object x)] [k (aget x k)])))
