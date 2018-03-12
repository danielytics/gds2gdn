(ns gds2gdn.core
 (:require #?(:cljs [instaparse.core :as insta :refer-macros [defparser]]
              :clj  [instaparse.core :as insta])
           [instaparse.failure :refer [pprint-failure]]
           [clojure.walk :refer [postwalk]]
           [clojure.set :refer [rename-keys]]
           #?(:cljs [lumo.io :as io]
              :clj [clojure.java.io :as io])
           [clojure.string :as string]
           [clojure.pprint]))

#?(:cljs (defparser gdscript (io/slurp "grammar.ebnf"))
   :clj  (def gdscript (insta/parser (slurp (io/resource "grammar.ebnf")))))

(def statement-types {:pass-stmt :pass})

(defn remove-tags [m]
  (for [[k v] m]
    [k (map #(vec (drop 1 %)) v)]))

(defn flatten-groups [m]
  (for [[k v] m]
    (if (and (seqable? v)
             (= (count v) 1))
      [k (first v)]
      [k v])))

(defn update-many [m update-map]
  (reduce
    (fn [m [k f]]
      (update m k f))
    m
    update-map))

(defn mapify-children [[_ & children]]
  (->> children
    (group-by first)
    (remove-tags)
    (flatten-groups)
    (into {})))

(defn mk [node args]
  [(first node) args])

(defmulti xform first)

(defmethod xform :default [node] node)

(defmethod xform :gdscript [node]
  (-> node
      (mapify-children)
      (rename-keys
        {:enum :enums
         :function :functions})
      (update-many
        {:extends first})))

(defmethod xform :enum [node]
  (let [{:keys [enum-name enum-entry]} (mapify-children (rest node))]
    (mk node
      {:name (str (-> enum-name first second))
       :entries (mapv first enum-entry)})))

(defmethod xform :enum-entry [node]
  (mk node
    (rename-keys
      (into {} (rest node))
      {:enum-name :name
       :enum-value :value})))

(defmethod xform :integer [node]
  [:integer (int (apply str (rest node)))])

(defn transform-all
  [node]
  (if (and (vector? node)
           (keyword? (first node)))
    (xform node)
    node))

(defn parse-script [script total? trace?]
  (gdscript script :total total? :trace trace?))

(defn preprocess-indention [source]
  (let [source-lines (conj (string/split-lines source)
                           "#EOF")] ; #EOF comment ensures that the file always ends with correct dedents
    (string/join
      "\n"
      (first
        (reduce
          (fn [[processed indent-level] line]
            (let [leading-spaces (take-while #(contains? #{\space \tab} %) line)
                  without-spaces (drop (count leading-spaces) line)
                  indents        (reduce + (map {\space 1 \tab 8} leading-spaces))
                  blank-line?    (or (= 0 (count without-spaces)) (and (= (first without-spaces) \#)
                                                                       (not= without-spaces [\# \E \O \F])))]
              (vector
               (conj processed
                (str
                  (cond
                    blank-line?
                    ""
                    (> indents indent-level)
                    (string/join "" (into without-spaces (repeat (- indents indent-level) \»)))
                    (< indents indent-level)
                    (string/join "" (into without-spaces (repeat (- indent-level indents) \«)))
                    :else
                    (string/join "" without-spaces))
                  (when-not blank-line? (str "#«" indent-level "»")))) ; used for converting line/col numbers to raw for error reporting
               (if blank-line? indent-level indents))))
          [[] 0]
          source-lines)))))

(defn to-int [s]
  (if (nil? s)
    0
    (#?(:cljs int :clj Integer/parseInt) s)))

(defn locate-error! [source error context-lines]
  (let [line-num (:line error)
        col-num (+ (:column error) (to-int (second (re-find #"#«([0-9]+)»$" (:text error)))))
        numbered-source (->> source
                          (string/split-lines)
                          (map #(vector (str (inc %1)) %2) (range)))
        error-context (if (< line-num context-lines)
                        (take line-num numbered-source)
                        (->> numbered-source
                          (drop (- line-num context-lines))
                          (take context-lines)))
        padding (apply max (map (comp count first) error-context))]
    (println "Parse error on line" (str line-num ", column " col-num))
    (doseq [[line-number line-source] error-context]
      (println
        (str
          " "
          (apply str (repeat (- padding (count line-number)) " "))
          line-number
          "\t"
          line-source)))
    (println
      (str
        " "
        (apply str (repeat padding " "))
        "\t"
        (apply str (repeat (dec col-num) " "))
        "^"))))


(defn transpile-file [script-file opts]
  (let [raw-source (#?(:cljs io/slurp :clj slurp) script-file)
            source (preprocess-indention raw-source)
            result (parse-script source false false)]
    (when (contains? opts "--show-indents")
      (println "--------------- Source Code With Indent Info ---------------")
      (println source)
      (println "--------------- ---------------------------- ---------------")
      (println))
    (if (insta/failure? result)
      (do
        (println "ERROR:")
        (clojure.pprint/pprint result)
        (when (contains? opts "-t")
          (println)
          (println "Total parse mode results:")
          (clojure.pprint/pprint (parse-script source true (contains? opts "--trace"))))
        (println)
        ;(pprint-failure result)
        (locate-error! raw-source result 3)
        (println))
      (do
        (when (contains? opts "--viz")
          (insta/visualize (gdscript source) :options {:dpi 63}))
        (clojure.pprint/pprint
          (postwalk transform-all result))))))

(defn -main [& args]
  (let [has-opts? (> (count args) 1)
        opts (if has-opts? (set (butlast args)) #{})]
    (when (contains? opts "--grammar")
      (println gdscript))
    (if-let [script-file (if has-opts? (last args) (first args))]
      (transpile-file script-file opts)
      (println "ERROR: No source file specified"))))

#_
[:gdscript
 [:extends "RigidBody2D"]
 [:enum
  [:enum-entry [:enum-name "UNIT_NEUTRAL"]]
  [:enum-entry [:enum-name "UNIT_ENEMY"]]
  [:enum-entry [:enum-name "UNIT_ALLY"]]]
 [:enum
  [:enum-name "Named"]
  [:enum-entry [:enum-name "THING_1"]]
  [:enum-entry [:enum-name "THING_2"] [:enum-value [:integer "3"]]]
  [:enum-entry
   [:enum-name "ANOTHER_THING"]
   [:enum-value [:integer "-" "1"]]]]
 [:function
  [:function-name "some_function"]
  [:parameter-list [:parameter "param1"] [:parameter "param2"]]
  [:function-body
   [:statement [:indent] [:pass-stmt]]
   [:statement [:indent] [:pass-stmt]]
   [:statement [:indent] [:indent] [:pass-stmt]]]]]



