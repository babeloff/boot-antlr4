(ns babeloff.uuid
  (:import (java.util UUID)))

(defn new [] (java.util.UUID/randomUUID))
(defn new-name [] (keyword (java.util.UUID/randomUUID)))
