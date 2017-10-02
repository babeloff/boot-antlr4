# antlr4

Boot tasks to work with antlr4.

## Usage

Anltr4 is a parser and lexer generator.

Run the `antlr4-pre` task:

    $ boot antlr4-pre

To use this in your project, add `[babeloff/antlr4 "0.1.0-SNAPSHOT"]` to your `:dependencies`
and then require the task:

    (require '[babeloff.antlr4 :refer [antlr4-pre]])

Other tasks include: `antlr4-simple`, `antlr4-post`, `antlr4-pass-thru`.

## License

Copyright © 2017 Fredrick P. Eisele

Distributed under the Eclipse Public License either
version 1.0 or (at your option) any later version.
