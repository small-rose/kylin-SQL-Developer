package com.kylin.plsql.ui.component.common;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.text.Segment;
import java.util.HashSet;
import java.util.Set;
import org.fife.ui.rsyntaxtextarea.TokenMap;

public class PlSqlTokenMaker extends AbstractTokenMaker {

    private static final Set<String> KEYWORDS = new HashSet<>(Set.of(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "SEQUENCE",
        "TRIGGER", "FUNCTION", "PROCEDURE", "PACKAGE", "BODY", "REPLACE",
        "BEGIN", "END", "DECLARE", "EXCEPTION",
        "IF", "THEN", "ELSE", "ELSIF", "END IF", "END CASE", "END LOOP",
        "LOOP", "FOR", "WHILE",
        "CASE", "WHEN",
        "RETURN", "EXIT", "CONTINUE", "GOTO",
        "COMMIT", "ROLLBACK", "SAVEPOINT",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "JOIN", "ON",
        "ORDER", "GROUP", "BY", "HAVING",
        "UNION", "MINUS", "INTERSECT", "EXCEPT",
        "AS", "IS", "OUT", "NOCOPY", "REF",
        "NULL", "DEFAULT", "PRIMARY", "KEY", "FOREIGN", "CONSTRAINT",
        "REFERENCES", "CHECK", "UNIQUE", "CASCADE",
        "DISTINCT", "ALL", "NOWAIT",
        "GRANT", "REVOKE",
        "TYPE", "VARRAY", "RECORD", "SUBTYPE", "CURSOR",
        "FETCH", "OPEN", "CLOSE", "INTO", "BULK", "COLLECT",
        "RAISE", "PRAGMA", "EXECUTE", "IMMEDIATE",
        "AUTHID", "DETERMINISTIC", "PIPELINED", "PARALLEL_ENABLE",
        "RESULT_CACHE",
        "TRUNCATE", "MERGE",
        "WITH", "CONNECT", "PRIOR", "START",
        "BETWEEN", "LIKE", "ANY", "SOME",
        "TRUE", "FALSE",
        "AT", "LOCAL", "TIME", "ZONE",
        "SESSION", "CURRENT",
        "MAX", "MIN", "SUM", "COUNT", "AVG",
        "NVL", "NVL2", "DECODE",
        "SYSDATE", "SYSTIMESTAMP", "TRUNC", "ROUND",
        "CEIL", "FLOOR", "MOD", "POWER", "SQRT",
        "UPPER", "LOWER", "INITCAP", "SUBSTR", "INSTR", "LENGTH",
        "REPLACE", "TRANSLATE", "TRIM", "LTRIM", "RTRIM",
        "LPAD", "RPAD", "CONCAT", "COALESCE", "NULLIF",
        "LAG", "LEAD", "RANK", "DENSE_RANK", "ROW_NUMBER",
        "LISTAGG", "EXTRACT", "CAST", "CONVERT",
        "ADD_MONTHS", "MONTHS_BETWEEN", "LAST_DAY", "NEXT_DAY",
        "GREATEST", "LEAST", "SYS_GUID",
        "VARCHAR2", "VARCHAR", "NVARCHAR2", "CHAR", "NCHAR", "NUMBER",
        "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "SIMPLE_INTEGER",
        "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE",
        "DATE", "TIMESTAMP", "INTERVAL",
        "CLOB", "NCLOB", "BLOB", "BFILE", "LONG", "RAW", "LONG RAW",
        "ROWID", "UROWID", "BOOLEAN", "SYS_REFCURSOR",
        "TO_DATE", "TO_CHAR", "TO_NUMBER"
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
                            }
                            break;
                        case '(':
                        case ')':
                        case ',':
                        case ';':
                        case '.':
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
