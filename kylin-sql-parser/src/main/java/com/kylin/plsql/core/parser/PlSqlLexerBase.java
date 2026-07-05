package com.kylin.plsql.core.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

/** Base class for PL/SQL ANTLR lexer with helper methods. */
public abstract class PlSqlLexerBase extends Lexer {
    public PlSqlLexerBase(CharStream input) {
        super(input);
    }

    protected boolean IsNewlineAtPos(int pos) {
        int la = _input.LA(pos);
        return la == -1 || la == '\n';
    }
}
