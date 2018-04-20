(ns gudb.colors)

(defonce color-scheme-prism {
  :cyan "#2aa198" ; /* cyan */
  :base01 "#586e75" ; /* base01 */

  :token.default "#586e75" ; /* base01 */

  :token.comment "#93a1a1" ; /* base1 */
  :token.prolog "#93a1a1" ; /* base1 */
  :token.doctype "#93a1a1" ; /* base1 */
  :token.cdata "#93a1a1" ; /* base1 */

  :token.punctuation "#586e75" ; /* base01 */

  :token.property "#268bd2" ; /* blue */
  :token.tag "#268bd2" ; /* blue */
  :token.boolean "#268bd2" ; /* blue */
  :token.number "#268bd2" ; /* blue */
  :token.constant "#268bd2" ; /* blue */
  :token.symbol "#268bd2" ; /* blue */
  :token.deleted "#268bd2" ; /* blue */

  :token.selector "#2aa198" ; /* cyan */
  :token.attr-name "#2aa198" ; /* cyan */
  :token.string "#2aa198" ; /* cyan */
  :token.char "#2aa198" ; /* cyan */
  :token.builtin "#2aa198" ; /* cyan */
  :token.url "#2aa198" ; /* cyan */
  :token.inserted "#2aa198" ; /* cyan */

  :token.entity "#657b83" ; /* base00 */
                ; background: #eee8d5; /* base2 */

  :token.atrule "#859900" ; /* green */
  :token.attr-value "#859900" ; /* green */
  :token.keyword "#859900" ; /* green */

  :token.function "#b58900" ; /* yellow */

  :token.regex "#cb4b16"; /* orange */
  :token.important "#cb4b16"; /* orange */
  :token.variable "#cb4b16"; /* orange */

})

(defonce color-scheme
  {
   :hljs-comment "#93a1a1"
   :hljs-quote "#93a1a1"
   :hljs-keyword "#859900"
   :hljs-selector-tag "#859900"
   :hljs-addition "#859900"

   :hljs-number "#2aa198"
   :hljs-string "#2aa198"
   :hljs-meta-string "#2aa198"
   :hljs-literal "#2aa198"
   :hljs-doctag "#2aa198"
   :hljs-regexp "#2aa198"

   :hljs-section "#268bd2"
   :hljs-name "#268bd2"
   :hljs-selector-id "#268bd2"
   :hljs-selector-class "#268bd2"

   :hljs-attribute "#b58900"
   :hljs-attr "#b58900"
   :hljs-variable "#b58900"
   :hljs-template-variable "#b58900"
   :hljs-class "#b58900"
   :hljs-type "#b58900"


   :hljs-symbol "#cb4b16"
   :hljs-bullet "#cb4b16"
   :hljs-subst "#cb4b16"
   :hljs-meta "#cb4b16"
   :hljs-selector-attr "#cb4b16"
   :hljs-selector-pseudo "#cb4b16"
   :hljs-link "#cb4b16"

   :hljs-built_in "#dc322f"
   :hljs-deletion "#dc322f"
   })

(defonce hl-lineno-style
  {:bg  (:cyan color-scheme-prism)})

(defonce lineno-style
  {:bg  (:base01 color-scheme-prism)})

