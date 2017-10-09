

(import 
    '(org.antlr.v4 Tool)
    '(clojure.lang 
        DynamicClassLoader)
    '(org.antlr.v4.runtime 
        CharStream CharStreams
        CommonToken CommonTokenStream
        DiagnosticErrorListener
        Lexer LexerInterpreter
        Parser ParserInterpreter
        ParserRuleContext
        Token TokenStream)
    '(clojure.lang Reflector)
    '(java.nio.file Paths)
    '(java.nio.charset Charset)
    '(java.io IOException))
    
(def lexer-name "org.antlr.parser.antlr4.ANTLRv4Lexer")
(def parser-name "org.antlr.parser.antlr4.ANTLRv4Parser")
(def class-loader (DynamicClassLoader.))

;;(defn construct [klass & args]
;;    (Reflector/invokeConstructor klass 
;;        (into-array Object args))

(def lexer-class ^Lexer (.loadClass class-loader lexer-name))

(def lexer 
    (Reflector/invokeConstructor lexer-class 
        (into-array CharStream [nil])))
        
(def parser-class ^Parser (.loadClass class-loader parser-name))

(def parser 
    (Reflector/invokeConstructor parser-class 
        (into-array TokenStream [nil])))

(def char-set (Charset/defaultCharset))
(def char-stream 
    (-> (Paths/get "src/antlr4/ANTLRv4.g4" (make-array String 0))
        (CharStreams/fromPath char-set)))

