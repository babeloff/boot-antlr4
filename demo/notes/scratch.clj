

(def cl (clojure.lang.DynamicClassLoader.))

(.findInMemoryClass cl "java.lang.Long")
