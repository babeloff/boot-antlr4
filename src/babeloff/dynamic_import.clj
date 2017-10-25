(ns babeloff.dynamic-import
  "an antlr task that builds lexers and parsers."
  (:require (clojure.java [io :as io]
                          [classpath :as cp]))
  (:import (clojure.lang DynamicClassLoader
                         Reflector)
           (clojure.asm ClassReader)
           (java.nio.file Paths)
           (java.nio.charset Charset)
           (java.io IOException)
           (org.antlr.v4.gui.Trees)
           (java.lang.reflect Constructor
                              InvocationTargetException
                              Method)
           (java.util ArrayList
                      List)))

(defn make-loader [] (DynamicClassLoader.))

(defn load-class [class-loader class-name]
  (.loadClass class-loader class-name))

(defn invoke-constructor [clazz args]
  (Reflector/invokeConstructor clazz args))

(defn invoke-inst-member [inst arg]
    (Reflector/invokeInstanceMember inst arg))

(def default-charset (Charset/defaultCharset))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (when xin
      (io/copy xin xout)
      (.toByteArray xout))))


(defn define-class
 [class-loader class-name class-path]
 ;; (if (.findInMemoryClass class-loader class-name)
 (let [class-bytes (file->bytes class-path)]
  (when class-bytes
    (.defineClass class-loader class-name class-bytes "") )))
