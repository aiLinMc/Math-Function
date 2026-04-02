package com.ailinmc.function_math.expr;

public class VariableNode extends AstNode {
    // 变量名固定为 "x"
    @Override
    public double evaluate(double x) {
        return x;
    }
}