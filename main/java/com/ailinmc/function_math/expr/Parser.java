package com.ailinmc.function_math.expr;

import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public AstNode parse() {
        return parseExpression();
    }

    // 表达式 → 加减
    private AstNode parseExpression() {
        AstNode node = parseTerm();
        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type == TokenType.OPERATOR && (token.value.equals("+") || token.value.equals("-"))) {
                pos++;
                AstNode right = parseTerm();
                node = new BinaryOpNode(token.value, node, right);
            } else {
                break;
            }
        }
        return node;
    }

    // 项 → 乘除
    private AstNode parseTerm() {
        AstNode node = parseFactor();
        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type == TokenType.OPERATOR && (token.value.equals("*") || token.value.equals("/"))) {
                pos++;
                AstNode right = parseFactor();
                node = new BinaryOpNode(token.value, node, right);
            } else {
                break;
            }
        }
        return node;
    }

    // 因子 → 乘方（右结合）
    private AstNode parseFactor() {
        AstNode node = parsePrimary();
        if (pos < tokens.size() && tokens.get(pos).type == TokenType.OPERATOR && tokens.get(pos).value.equals("^")) {
            pos++;
            AstNode right = parseFactor(); // 右结合
            node = new BinaryOpNode("^", node, right);
        }
        return node;
    }

    // 基本单元：数字、变量、函数调用、括号表达式
    private AstNode parsePrimary() {
        Token token = tokens.get(pos);
        if (token.type == TokenType.NUMBER) {
            pos++;
            return new NumberNode(token.number);
        } else if (token.type == TokenType.VARIABLE) {
            pos++;
            return new VariableNode();
        } else if (token.type == TokenType.FUNCTION) {
            String funcName = token.value;
            pos++;
            expect(TokenType.LPAREN);
            AstNode arg = parseExpression();
            expect(TokenType.RPAREN);
            return new FunctionNode(funcName, arg);
        } else if (token.type == TokenType.LPAREN) {
            pos++;
            AstNode node = parseExpression();
            expect(TokenType.RPAREN);
            return node;
        } else {
            throw new IllegalArgumentException("Unexpected token: " + token);
        }
    }

    private void expect(TokenType type) {
        if (pos >= tokens.size() || tokens.get(pos).type != type) {
            throw new IllegalArgumentException("Expected " + type + ", but got " +
                    (pos < tokens.size() ? tokens.get(pos).type : "EOF"));
        }
        pos++;
    }
}