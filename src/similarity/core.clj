(ns similarity.core
  (:use     [cascalog.api]
            [cascalog.more-taps :only (hfs-delimited)])
  (:require [clojure.string :as str]
            [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.vars :as v])
  (:import  [com.google.common.hash Hashing])
  (:gen-class))

(defmapcatop [extract-shingles [k]]
  [s]
  "Returns the set of k-shingles that appear one or more times in string s."
  (->> (str/replace s #"\s+" " ")
       (partition k 1 s)
       (map #(apply str %))
       set))

(defmapop [multihash [n]]
  [shingle]
  "Returns a hash signature for a set of k-shingles."
  [(map (fn [seed]
          (.asInt (.hashString (Hashing/murmur3_32 seed) shingle)))
        (range n))])

(defn merge-vectors
  [v1 v2]
  "Returns a vector where each item is the minimum of pair-wise elements taken from v1 and v2."
  (map #(map min %1 %2) v1 v2))

(defbufferop minhash-sig
  [hash-sigs]
  "Returns the minhash signature for a collection of hash signatures."
  [(reduce merge-vectors hash-sigs)])

(defn minhash-sigs
  [docs k n]
  "Returns the document ID and minhash signatures as a tuple stream."
  (<- [?doc-id ?minhash-sig]
      (docs :> ?doc-id ?line)
      (extract-shingles k ?line :> ?shingle)
      (multihash n ?shingle :> ?hash-sig)
      (minhash-sig ?hash-sig :> ?minhash-sig)))

(defn find-by-id
  [doc-sigs doc-id]
  "Returns the document with that matches the given document ID."
  (<- [?doc-sig]
      (doc-sigs ?doc-id ?doc-sig)
      (= ?doc-id doc-id)))

(defn simvector
  [v1 v2]
  "Returns the proportion of equal items in v1 and v2."
  (let [counts (group-by #(= (first %) (second %))
                         (partition 2 2 (interleave v1 v2)))]
    (if (empty? counts)
      0
      (double (/ (count (counts true))
                 (+ (count (counts true))
                    (count (counts false))))))))

(defmapcatop [bands [b]]
  [minhash-sig]
  "Returns the document ID and bands of its minhash signature as multiple tuples."
  (let [n (count minhash-sig)
        r (/ n b)]
    [[(partition 2 2 (interleave (range r)
                                 (partition r r minhash-sig)))]]))

(defn multibandhash
  [bands]
  "Returns the hash values of all bands of a document's minhash signature."
  [(map #(.asInt (Hashing/combineOrdered %))
        (map (fn [band]
               (let [[seed row] band
                     hash-fn    (Hashing/murmur3_32 seed)]
                 (map (fn [hash-code]
                        (.hashLong hash-fn hash-code))
                      row)))
             bands))])

(defn lsh-sigs
  [doc-sigs b]
  "Returns the document ID and its locality-sensitive hash signature as a tuple stream."
  (<- [?doc-id ?lsh-sig]
      (doc-sigs ?doc-id ?minhash-sig)
      (bands b ?minhash-sig :> ?bands)
      (multibandhash ?bands :> ?lsh-sig)))

(deffilterop candidate-pair?
  [v1 v2]
  "Returns true if at least one pair of elements from v1 and v2 are equal."
  (some true? (map #(= %1 %2) v1 v2)))

(defn candidates
  [doc-sigs lsh-sigs doc-id]
  "Filters the documents whose locality-sensitive hash signature are sufficiently similar to the target document."
  (let [[[[target-sig]]] (??- (find-by-id lsh-sigs doc-id))]
    (<- [?doc-id ?minhash-sig]
        (doc-sigs ?doc-id ?minhash-sig)
        (lsh-sigs ?doc-id ?lsh-sig)
        (candidate-pair? target-sig ?lsh-sig))))

(defn minhash-similarity
  [docs doc-id threshold k n]
  "Returns the document ID and similarity score with a exhaustive, brute-force comparison between the target document's minhash signature and every other document's minhash signature."
  (let [doc-sigs         (minhash-sigs docs k n)
        [[[target-sig]]] (??- (find-by-id doc-sigs doc-id))]
    (<- [?doc-id ?similarity]
        (doc-sigs ?doc-id ?minhash-sig)
        ((c/negate #'=) ?doc-id doc-id)
        (simvector target-sig ?minhash-sig :> ?similarity)
        (> ?similarity threshold))))

(defn similarity
  [docs doc-id k n b]
  "Returns the document ID and similarity score using locality-sensitive hashing to substantially reduce the number of comparisons."
  (let [doc-sigs         (minhash-sigs docs k n)
        candidates       (candidates doc-sigs (lsh-sigs doc-sigs b) doc-id)
        [[[target-sig]]] (??- (find-by-id doc-sigs doc-id))]
    (<- [?doc-id ?similarity]
        (candidates ?doc-id ?minhash-sig)
        ((c/negate #'=) ?doc-id doc-id)
        (simvector target-sig ?minhash-sig :> ?similarity))))

(defn -main
  [in out doc-id k n b & args]
  (let [docs      (hfs-delimited in :skip-header? false)
        ;; threshold (Double/parseDouble threshold)
        k         (Integer/parseInt k)
        n         (Integer/parseInt n)
        b         (Integer/parseInt b)]
    (?- (hfs-delimited out)
        (similarity docs doc-id k n b))))
