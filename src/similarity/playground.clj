(ns similarity.playground
  (:use [similarity.core :only [shingles]]
        [cascalog.api]
        [cascalog.playground :only [bootstrap bootstrap-emacs]])
  (:require [clojure.string :as str]
            [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.vars :as v])
  (:import [java.io PrintStream]
           [cascalog WriterOutputStream]
           [org.apache.log4j Logger WriterAppender SimpleLayout]
           [com.google.common.hash Hashing]))

(def documents
  [["docA" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy dog. This is a pangram."]
   ["docB" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick white wolf eats the lazy sheep."]
   ["docC" "A pangram is a phrase that contains all of the letters of the English alphabet. The slow brown fox jumps into the quizzical dog."]
   ["docD" "A pangram is a phrase that contains all of the letters of the English alphabet. The slow white wolf lays next to the lazy dog."]
   ["docE" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy cat."]])

(def D
  [["S1" "ad"]
   ["S2" "c"]
   ["S3" "bde"]
   ["S4" "acd"]])

(def D-sigs
  [["S1" 1 0]
   ["S2" 3 2]
   ["S3" 0 0]
   ["S4" 1 0]])

(defn line->row [line] (- (int (first line)) 97))
(defn hash-1 [x] (mod (+ (line->row x) 1) 5))
(defn hash-2 [x] (mod (+ (* (line->row x) 3) 1) 5))

(defmapcatop [extract-shingles [k]] [line] (shingles k line))

(defn make-hash-fn [seed n]
  (fn [shingle]
    (mod (.asInt (.hashString (Hashing/murmur3_32 seed) shingle)) n)))

(defn make-hash-fns [n]
  (apply juxt (map #(make-hash-fn % n) (range n))))

(def multihash (make-hash-fns 8))

(defmacro defhashfn [name seed n]
  (let [sym (gensym)]
    `(defn ~name [~sym]
       (mod (.asInt (.hashString (Hashing/murmur3_32 ~seed) ~sym) ~n)))))

(defn minhash-sig [docs doc-id]
  (let [hash-vars (v/gen-non-nullable-vars 2)]
    (<- hash-vars
        (docs :>> (reduce conj ["?doc-id"] hash-vars))
        (= ?doc-id doc-id))))

(comment
  ;; make minhash signatures with hand-written hash-fns
  (let [hash-vars    (v/gen-non-nullable-vars 2)
        minhash-vars (v/gen-non-nullable-vars 2)]
    (?<- (stdout)
         (reduce conj ["?doc"] minhash-vars)
         (D :> ?doc ?line)
         (extract-shingles 1 ?line :> ?shingle)
         ((apply c/juxt [hash-1 hash-2]) ?shingle :>> hash-vars)
         ((c/each c/min) :<< hash-vars :>> minhash-vars))))

(comment
  ;; get minhash signature for S1
  ;; write to file
  (?- (hfs-textline "S1-minhash-sig")
      (minhash-sig D-sigs "S1")))

(defmapop [simvector [v1]] [& v2]
  (let [counts (group-by #(= (first %) (second %))
                         (partition 2 2 (interleave v1 v2)))]
    (double (/ (count (counts true))
               (+ (count (counts true))
                  (count (counts false)))))))

(defmapop [simhash [v1]] [& v2]
  (do (prn (str "v1: " v1)) (prn (str "v2: " v2))))

(defn simhash [v1 & v2]
  (do (prn (str "v1: " v1)) (prn (str "v2: " v2))))

(defmapop [simhash [x1 x2]] [& y]
  (let [x [x1 x2]
        counts (group-by #(= (first %) (second %))
                         (partition 2 2 (interleave x y)))]
    (double (/ (count (counts true))
               (+ (count (counts true))
                  (count (counts false)))))))

(comment
  (let [target-sig (map #(Integer/parseInt %)
                        (str/split (slurp "S1-minhash-sig/part-00000") #"\s+"))
        hash-vars  (v/gen-non-nullable-vars 2)
        simhash-vars (v/gen-non-nullable-vars 2)]
    (?<- (stdout)
         [?doc-id ?similarity]
         (D-sigs ?doc-id ?hash-1 ?hash-2)
         ((c/negate #'=) ?doc-id "S1")
         (simhash target-sig :<< [?hash-1 ?hash-2] :> ?similarity))))

(comment
  ;; make minhash signatures with generated hash-fns
  ;; doesn't work, defhashfn doesn't result in vars being interned
  (let [n            2
        hash-fns     (for [i (range n)] (gensym "hash-op-"))
        hash-vars    (v/gen-non-nullable-vars n)
        minhash-vars (v/gen-non-nullable-vars n)]
    (doseq [[i sym] (partition 2 (interleave (range n) hash-fns))]
      (defhashfn sym i n)
      (?<- (stdout)
           (reduce conj ["?doc"] minhash-vars)
           (D :> ?doc ?line)
           (extract-shingles 1 ?line :> ?shingle)
           ((apply c/juxt hash-fns) ?shingle :>> hash-vars)
           ((c/each c/min) :<< hash-vars :>> minhash-vars)))))

(comment
  ;; hard-coded # of hash functions
  (let [n            8
        hash-vars    (v/gen-non-nullable-vars n)
        minhash-vars (v/gen-non-nullable-vars n)]
    (?<- (stdout)
         (reduce conj ["?doc"] minhash-vars)
         (D :> ?doc ?line)
         (extract-shingles 1 ?line :> ?shingle)
         (multihash ?shingle :>> hash-vars)
         ((c/each c/min) :<< hash-vars :>> minhash-vars))))

(defn minhash-signatures [docs k]
  (let [n            200
        multihash    (make-hash-fns 8)
        hash-vars    (v/gen-non-nullable-vars n)
        minhash-vars (v/gen-non-nullable-vars n)]
    (<- (reduce conj ["?doc"] minhash-vars)
        (docs :> ?doc ?line)
        (extract-shingles k ?line :> ?shingle)
        (multihash ?shingle :>> hash-vars)
        ((c/each c/min) :<< hash-vars :>> minhash-vars))))
