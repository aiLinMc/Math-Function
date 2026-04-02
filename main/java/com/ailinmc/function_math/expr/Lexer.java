package com.ailinmc.function_math.expr;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private int pos;

    public Lexer(String input) {
        // 去除所有空白字符（空格、制表符等）
        this.input = input.replaceAll("\\s+", "");
        this.pos = 0;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c) || c == '.') {
                tokens.add(parseNumber());
            } else if (Character.isLetter(c)) {
                tokens.add(parseIdentifier());
            } else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^') {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                pos++;
            } else if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                pos++;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                pos++;
            } else if (c == ',') {
                tokens.add(new Token(TokenType.COMMA, ","));
                pos++;
            } else {
                throw new IllegalArgumentException("Unexpected character: " + c);
            }
        }
        return tokens;
    }

    private Token parseNumber() {
        int start = pos;
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        double num = Double.parseDouble(input.substring(start, pos));
        return new Token(num);
    }

    private Token parseIdentifier() {
        int start = pos;
        while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            pos++;
        }
        String id = input.substring(start, pos);
        if (id.equals("x")) {
            return new Token(TokenType.VARIABLE, id);
        } else if (isFunction(id)) {
            return new Token(TokenType.FUNCTION, id);
        } else {
            throw new IllegalArgumentException("Unknown identifier: " + id);
        }
    }

    private boolean isFunction(String name) {
        return name.equals("sin") || name.equals("cos") || name.equals("tan") ||
               name.equals("sqrt") || name.equals("log") || name.equals("ln");
    }
}