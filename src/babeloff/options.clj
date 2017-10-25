(ns babeloff.options
  "an antlr task that builds lexers and parsers."
  (:require (boot [core :as boot :refer [deftask]]
                  [util :as util]
                  [task-helpers :as helper])))

(defn show-array
  "this shows the array arguments and then returns them."
  [arry]
  (loop [arr arry, builder (StringBuilder.)]
    (if (empty? arr)
      (do
        (util/info " tool args: %s\n"
                (.toString builder))
        arry)
      (recur (rest arr)
             (-> builder
                 (.append " ")
                 (.append (first arr)))))))

(defmacro with-err-str
  "Evaluates exprs in a context in which *out* and *err*
  are bound to a fresh StringWriter.
  Returns the string created by any nested printing calls."
  {:added "1.9"}
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*out* s#, *err* s#]
       ~@body
       (str s#))))


(defn value!
  [args option object]
  (if object
    (do
      ;(util/info "arg: %s %s\n" option object)
      (-> args
          (conj! option)
          (conj! object)))
    (do
      ;(util/info "no-arg: %s %s\n" option object)
      args)))


(defn bool!
  [args option object]
  (if (sequential? option)
    (do
      ;(util/info "arg: %s %s\n" option object)
      (conj! args (if object (first option) (second option))))
    (do
      ;(util/info "arg: %s %s\n" option object)
      (if object
        (conj! args option)
        args))))

(defn override!
  [args overrides]
  (if-not (empty? overrides)
    (do)
      ;(util/info "no-overrides\n"))
    (loop [argset args,
           items overrides]
      (if (empty? items)
        argset
        (do
          ;(util/info "arg: %s\n" (first items))
          (recur (conj! argset (str "-D" (first items)))
                 (rest items)))))))

(defn source!
  [args source fileset]
  (-> args
    (conj! "-lib")
    (conj! (or source
             (.getCanonicalPath (first (boot/input-dirs fileset)))))))
