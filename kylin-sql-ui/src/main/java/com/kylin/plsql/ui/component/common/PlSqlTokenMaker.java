package com.kylin.plsql.ui.component.common;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.text.Segment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.fife.ui.rsyntaxtextarea.TokenMap;

/** Custom RSyntaxTextArea TokenMaker for PL/SQL syntax highlighting. */
public class PlSqlTokenMaker extends AbstractTokenMaker {

    public static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        // DML - Core
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "MERGE",
        // DDL
        "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT",
        "TABLE", "INDEX", "VIEW", "SEQUENCE", "SYNONYM", "MATERIALIZED",
        "TRIGGER", "FUNCTION", "PROCEDURE", "PACKAGE", "BODY", "REPLACE",
        "TYPE", "VARRAY", "RECORD", "SUBTYPE", "CURSOR",
        // PL/SQL blocks
        "BEGIN", "END", "DECLARE", "EXCEPTION",
        "IF", "THEN", "ELSE", "ELSIF", "END IF", "END CASE", "END LOOP",
        "LOOP", "FOR", "WHILE", "FORALL", "REVERSE",
        "CASE", "WHEN",
        "RETURN", "RETURNING", "EXIT", "CONTINUE", "GOTO", "NULL",
        "COMMIT", "ROLLBACK", "SAVEPOINT", "LOCK",
        "OPEN", "FETCH", "CLOSE", "PIPE", "PIPED",
        "RAISE", "PRAGMA", "EXECUTE", "IMMEDIATE",
        // Joins
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL", "JOIN", "ON",
        // Clauses
        "ORDER", "GROUP", "BY", "HAVING", "OFFSET", "LIMIT", "FETCH",
        "UNION", "MINUS", "INTERSECT", "EXCEPT",
        "AS", "IS", "OUT", "NOCOPY", "REF",
        "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE", "CASCADE",
        "DISTINCT", "ALL", "NOWAIT", "SKIP", "LOCKED",
        // Privileges
        "GRANT", "REVOKE",
        // Hierarchical
        "WITH", "CONNECT", "PRIOR", "START", "LEVEL", "SIBLINGS",
        // Operators
        "BETWEEN", "LIKE", "REGEXP_LIKE",
        "ANY", "SOME", "ALL",
        "TRUE", "FALSE", "OTHERS", "UNKNOWN",
        // Analytic / Window
        "OVER", "PARTITION", "ROWS", "RANGE", "GROUPS", "UNBOUNDED",
        "PRECEDING", "FOLLOWING", "CURRENT", "ROW",
        "FIRST", "LAST", "KEEP", "DENSE_RANK", "RANK", "ROW_NUMBER",
        "NTILE", "CUME_DIST", "PERCENT_RANK", "PERCENTILE_CONT", "PERCENTILE_DISC",
        "FIRST_VALUE", "LAST_VALUE", "LAG", "LEAD", "LISTAGG",
        "NVL", "NVL2", "DECODE", "COALESCE", "NULLIF",
        "MAX", "MIN", "SUM", "COUNT", "AVG", "MEDIAN", "STDDEV", "VARIANCE",
        "CAST", "MULTISET", "COLLECT",
        // Math
        "ABS", "CEIL", "COS", "COSH", "EXP", "FLOOR", "GREATEST", "LEAST",
        "LN", "LOG", "MOD", "POWER", "ROUND", "SIGN",
        "SIN", "SINH", "SQRT", "TAN", "TANH", "TRUNC",
        // String
        "UPPER", "LOWER", "INITCAP", "SUBSTR", "INSTR", "LENGTH",
        "REPLACE", "TRANSLATE", "TRIM", "LTRIM", "RTRIM",
        "LPAD", "RPAD", "CONCAT", "CHR", "ASCII",
        "TO_CHAR", "TO_DATE", "TO_NUMBER", "TO_CLOB", "TO_TIMESTAMP",
        // Date
        "SYSDATE", "SYSTIMESTAMP", "CURRENT_DATE", "CURRENT_TIMESTAMP",
        "ADD_MONTHS", "MONTHS_BETWEEN", "LAST_DAY", "NEXT_DAY",
        "EXTRACT", "CONVERT",
        // Data types
        "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "NUMBER",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE", "REAL",
        "DATE", "TIMESTAMP", "INTERVAL", "YEAR", "MONTH", "DAY",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR",
        // PL/SQL extras
        "BULK", "COLLECT", "INTO",
        "SQLERRM", "SQLCODE",
        "AUTHID", "DETERMINISTIC", "PIPELINED", "PARALLEL_ENABLE",
        "RESULT_CACHE",
        "SYS_GUID"
    ));

    @Override
    public Token getTokenList(Segment text, int startTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int count = text.count;
        int end = offset + count;

        int newStartOfs = startOffset - offset;
        int currentTokenStart = offset;
        int currentTokenType = startTokenType;

        for (int i = offset; i < end; i++) {
            char ch = array[i];

            switch (currentTokenType) {
                case TokenTypes.NULL:
                    currentTokenStart = i;
                    switch (ch) {
                        case ' ':
                        case '\t':
                            currentTokenType = TokenTypes.WHITESPACE;
                            break;
                        case '"':
                            currentTokenType = TokenTypes.LITERAL_STRING_DOUBLE_QUOTE;
                            break;
                        case '\'':
                            currentTokenType = TokenTypes.LITERAL_CHAR;
                            break;
                        case '-':
                            if (i + 1 < end && array[i + 1] == '-') {
                                currentTokenType = TokenTypes.COMMENT_EOL;
                            } else {
                                currentTokenType = TokenTypes.OPERATOR;
                            }
                            break;
                        case '/':
                            if (i + 1 < end && array[i + 1] == '*') {
                                currentTokenType = TokenTypes.COMMENT_MULTILINE;
                            } else {
                                currentTokenType = TokenTypes.OPERATOR;
                            }
                            break;
                        case '(':
                        case ')':
                        case ',':
                        case ';':
                        case '.':
                        // 全角分隔符
                        case '\uFF08': case '\uFF09': case '\uFF0C':
                        case '\uFF1B': case '\uFF0E':
                        case '\u3001': case '\u3002':
                            currentTokenType = TokenTypes.SEPARATOR;
                            break;
                        case ':':
                            if (i + 1 < end && array[i + 1] == '=') {
                                currentTokenType = TokenTypes.OPERATOR;
                                i++;
                            } else {
                                currentTokenType = TokenTypes.OPERATOR;
                            }
                            break;
                        case '=':
                        case '<':
                        case '>':
                        case '!':
                        case '+':
                        case '*':
                        case '%':
                        case '|':
                        case '~':
                        case '^':
                        case '&':
                        case '@':
                        case '\\':
                        case '#':
                        case '$':
                        case '[':
                        case ']':
                        case '{':
                        case '}':
                        // 全角操作符
                        case '\uFF1D': case '\uFF0B': case '\uFF0D':
                        case '\uFF0A': case '\uFF0F': case '\uFF1C':
                        case '\uFF1E': case '\uFF01': case '\uFF5C':
                        case '\uFF5E': case '\uFF3E': case '\uFF06':
                        case '\uFF20': case '\uFF03': case '\uFF05':
                        case '\uFF1A':
                        // 全角引号
                        case '\u201C': case '\u201D': case '\u2018':
                        case '\u2019': case '\uFF07': case '\uFF02':
                            currentTokenType = TokenTypes.OPERATOR;
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                            currentTokenType = TokenTypes.LITERAL_NUMBER_FLOAT;
                            break;
                        default:
                            if (Character.isLetter(ch) || ch == '_') {
                                currentTokenType = TokenTypes.IDENTIFIER;
                            } else {
                                currentTokenType = TokenTypes.NULL;
                            }
                            break;
                    }
                    break;

                case TokenTypes.WHITESPACE:
                    if (ch == ' ' || ch == '\t') break;
                    addToken(text, currentTokenStart, i - 1, TokenTypes.WHITESPACE, newStartOfs + currentTokenStart);
                    currentTokenStart = i;
                    i--;
                    currentTokenType = TokenTypes.NULL;
                    break;

                case TokenTypes.IDENTIFIER:
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '#'
                        || ch == '$' || ch == '%') {
                        break;
                    }
                    String word = new String(array, currentTokenStart, i - currentTokenStart).toUpperCase();
                    int type = KEYWORDS.contains(word) ? TokenTypes.RESERVED_WORD : TokenTypes.IDENTIFIER;
                    addToken(text, currentTokenStart, i - 1, type, newStartOfs + currentTokenStart);
                    currentTokenStart = i;
                    i--;
                    currentTokenType = TokenTypes.NULL;
                    break;

                case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE:
                    if (ch == '"') {
                        addToken(text, currentTokenStart, i, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, newStartOfs + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.LITERAL_CHAR:
                    if (ch == '\'') {
                        if (i + 1 < end && array[i + 1] == '\'') {
                            i++;
                        } else {
                            addToken(text, currentTokenStart, i, TokenTypes.LITERAL_CHAR, newStartOfs + currentTokenStart);
                            currentTokenType = TokenTypes.NULL;
                        }
                    }
                    break;

                case TokenTypes.COMMENT_EOL:
                    if (ch == '\n') {
                        addToken(text, currentTokenStart, i - 1, TokenTypes.COMMENT_EOL, newStartOfs + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.COMMENT_MULTILINE:
                    if (ch == '*' && i + 1 < end && array[i + 1] == '/') {
                        i++;
                        addToken(text, currentTokenStart, i, TokenTypes.COMMENT_MULTILINE, newStartOfs + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.OPERATOR:
                case TokenTypes.SEPARATOR:
                case TokenTypes.LITERAL_NUMBER_FLOAT:
                    addToken(text, currentTokenStart, i - 1, currentTokenType, newStartOfs + currentTokenStart);
                    currentTokenStart = i;
                    i--;
                    currentTokenType = TokenTypes.NULL;
                    break;
            }
        }

        switch (currentTokenType) {
            case TokenTypes.NULL:
                addNullToken();
                break;
            case TokenTypes.COMMENT_EOL:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOfs + currentTokenStart);
                addNullToken();
                break;
            case TokenTypes.COMMENT_MULTILINE:
            case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE:
            case TokenTypes.LITERAL_CHAR:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOfs + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOfs + currentTokenStart);
                addNullToken();
                break;
        }

        return firstToken;
    }

    @Override
    public TokenMap getWordsToHighlight() {
        var map = new TokenMap();
        for (var kw : KEYWORDS) {
            map.put(kw, TokenTypes.RESERVED_WORD);
        }
        return map;
    }
}
