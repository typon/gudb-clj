(ns gudb.core
  (:require ["blessed" :as blessed]))

(def screen (blessed/screen (js-obj "smartCSR" true)))

(def box (blessed/box (clj->js
          {"top" "center", 
           "left" "center", 
           "width" "50%", 
           "height" "50%", 
           "content" "REE {bold} world{/bold}",
           "tags" true,
           "border" {"type" "line"},
           "style" {"fg" "white", "bg" "magenta"}
           })))


(defn main! [] (do
  (.append screen box)
  (.focus box)
  (.render screen)
  ))
