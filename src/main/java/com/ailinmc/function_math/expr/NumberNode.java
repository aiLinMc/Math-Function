package com.ailinmc.function_math.expr;

public class NumberNode extends AstNode {
    private final double value;

    public NumberNode(double value) {
        this.value = value;
    }

    @Override
    public double evaluate(double x) {
        return value;
    }
}