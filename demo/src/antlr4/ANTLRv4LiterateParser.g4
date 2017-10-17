parser grammar ANTLRv4LiterateParser;

options
   { tokenVocab = ANTLRv4Lexer; }

import ANTLRv4Parser;

literaryGrammarSpec : (literarySpec | grammarSpec) ;

literarySpec : DOC_COMMENT ;