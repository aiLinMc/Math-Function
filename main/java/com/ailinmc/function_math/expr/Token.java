package com.ailinmc.function_math.expr;

public class Token {
    public final TokenType type;
    public final String value;   // 运算符、函数名等
    public final double number;  // 若 type == NUMBER 则存储数值

    // 用于 NUMBER 类型
    public Token(double number) {
        this.type = TokenType.NUMBER;
        this.number = number;
        this.value = null;
    }

    // 用于其他类型
    public Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
        this.number = 0.0;
    }

    @Override
    public String toString() {
        if (type == TokenType.NUMBER) return String.valueOf(number);
        return value;
    }
}