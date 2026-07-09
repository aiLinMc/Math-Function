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
                // 检查是否是 Ran#
                if (pos + 3 < input.length() && input.startsWith("Ran#", pos)) {
                    tokens.add(new Token(TokenType.FUNCTION, "Ran#"));
                    pos += 4;
                } else {
                    tokens.add(parseIdentifier());
                }
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
        // 数学常数 e 和 pi
        if (id.equals("e")) {
            return new Token(Math.E);
        }
        if (id.equals("pi")) {
            return new Token(Math.PI);
        }
        // 处理 logN 格式（如 log2, log10）
        // 如果标识符以 log 开头且超过3个字符（如 logx），回退到 log，只读数字
        if (id.startsWith("log") && id.length() > 3) {
            pos = start + 3; // 回退到 log 之后
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            id = input.substring(start, pos);
        } else if (id.equals("log")) {
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            id = input.substring(start, pos);
        }
        if (id.equals("x")) {
            return new Token(TokenType.VARIABLE, id);
        } else if (isFunction(id)) {
            return new Token(TokenType.FUNCTION, id);
        } else if (CustomFunctions.exists(id)) {
            return new Token(TokenType.FUNCTION, id);
        } else {
            throw new IllegalArgumentException("Unknown identifier: " + id);
        }
    }

    private boolean isFunction(String name) {
        if (name.equals("sin") || name.equals("cos") || name.equals("tan") ||
            name.equals("sqrt") || name.equals("ln") || name.equals("exp") || name.equals("abs") ||
            name.equals("RanInt") || name.equals("floor") || name.equals("ceil") ||
            name.equals("round") || name.equals("trunc") || name.equals("mod") ||
            name.equals("min") || name.equals("max")) {
            return true;
        }
        // log 或 logN（如 log2, log10）
        if (name.equals("log")) return true;
        if (name.startsWith("log")) {
            try {
                Integer.parseInt(name.substring(3));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}