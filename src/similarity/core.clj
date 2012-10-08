(ns similarity.core
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str])
  (:use swank.core))

(def gettysburg-address
  "Four score and seven years ago our fathers brought forth on this continent a new nation, conceived in liberty, and dedicated to the proposition that all men are created equal.\nNow we are engaged in a great civil war, testing whether that nation, or any nation, so conceived and so dedicated, can long endure. We are met on a great battle-field of that war. We have come to dedicate a portion of that field, as a final resting place for those who here gave their lives that that nation might live. It is altogether fitting and proper that we should do this.\nBut, in a larger sense, we can not dedicate, we can not consecrate, we can not hallow this ground. The brave men, living and dead, who struggled here, have consecrated it, far above our poor power to add or detract. The world will little note, nor long remember what we say here, but it can never forget what they did here. It is for us the living, rather, to be dedicated to the great task remaining before us--that from these honored dead we take increased devotion to that cause for which they gave the last full measure of devotion--that we here highly resolve that these dead shall not have died in vain--that this nation, under God, shall have a new birth of freedom--and that government of the people, by the people, for the people, shall not perish from the earth.")

(def U  #{\a \b \c \d \e})
(def S1 #{\a \d})
(def S2 #{\c})
(def S3 #{\b \d \e})
(def S4 #{\a \c \d})
(def docs [S1 S2 S3 S4])

(defn sim-jaccard
  [s t]
  "Returns the similarity of sets as a relative size of their intersection."
  (/ (count (set/intersection s t))
     (count (set/union s t))))

(defn shingles
  [k s]
  "Returns the set of k-shingles that appear one or more times in string s."
  (->> (str/replace s #"\s+" " ")
       (partition k 1 s)
       (map #(apply str %))
       (map #(.hashCode %))
       (apply sorted-set)))

(defn characteristics
  [coll]
  "Builds a naively dense characteristic matrix from a collection of documents.
  Each column represents a bit-vector representing if a particular
  document's k-shingles is part of the universal k-shingle set."
  (let [universe (apply set/union coll)]
    (vec (map (fn [s]
                (vec (map #(contains? s %) universe)))
              coll))))
;; (characteristics [S1 S2 S3 S4])
(defn count-cols [C]
  (count C))

(defn count-rows [C]
  (reduce max (map count C)))
(def C (characteristics docs))

(defn char->row [c] (- (int c) 97))
(defn hash-1 [x] (mod (+ x 1) 5))
(defn hash-2 [x] (mod (+ (* x 3) 1) 5))

(defn transpose
  [C]
  (partition (count C) (apply interleave C)))

(defn merge-vectors
  [v1 v2]
  (map #(map min %1 %2) v1 v2))

(defn signatures
  [doc-shingles & hash-fns]
  (let [U         (map char->row (apply set/union doc-shingles))
        C         (characteristics doc-shingles)
        hash-sigs (map (apply juxt hash-fns) U)
        sig-rows  (map (fn [row sig]
                         (map #(if %
                                 sig
                                 (vec (take (count sig)
                                            (repeat Integer/MAX_VALUE))))
                              row))
                       (transpose C) hash-sigs)]
    (reduce merge-vectors sig-rows)))
;; (signatures [S1 S2 S3 S4] hash-1 hash-2)
;; (map (fn [row sig] ...) matrix sigs)
;; => ([[1 1] nil nil [1 1]] ...)
;; (reduce merge-sigs (vec (cycle c nil)) sig-partials)
(comment
  (signatures docs hash-1 hash-2))
