(ns gudb.utils
  "Defines general purpose utility functions related to react, gdb and other dependencies"
  (:require ["blessed" :as blessed]
            ["react" :as react]
            ["create-react-class" :as create-react-class]
            ["react-blessed" :as react-blessed]))


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
  [name & {:keys [render]}]
  (create-react-class
   #js {:displayName name
        :render (fn []
                  (this-as t
                    (render (js->clj (.-props t) :keywordize-keys true))))}))
