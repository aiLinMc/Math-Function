package com.ailinmc.function_math.expr;

public class UnaryOpNode extends AstNode {
    private final String op;
    private final AstNode operand;

    public UnaryOpNode(String op, AstNode operand) {
        this.op = op;
        this.operand = operand;
    }

    @Override
    public double evaluate(double x) {
        double val = operand.evaluate(x);
        if (op.equals("-")) {
            return -val;
        }
        throw new UnsupportedOperationException("Unary operator: " + op);
    }
}
