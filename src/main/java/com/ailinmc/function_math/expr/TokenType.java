package com.ailinmc.function_math.expr;

public enum TokenType {
    NUMBER,      // 数值
    VARIABLE,    // 变量 x
    FUNCTION,    // 函数名 sin, cos, ...
    OPERATOR,    // + - * / ^
    LPAREN,      // (
    RPAREN,      // )
    COMMA        // , (暂未使用，但保留)
}