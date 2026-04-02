package com.ailinmc.function_math.expr;

public class BinaryOpNode extends AstNode {
    private final String op;
    private final AstNode left;
    private final AstNode right;

    public BinaryOpNode(String op, AstNode left, AstNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public double evaluate(double x) {
        double l = left.evaluate(x);
        double r = right.evaluate(x);
        switch (op) {
            case "+": return l + r;
            case "-": return l - r;
            case "*": return l * r;
            case "/": return l / r;
            case "^": return Math.pow(l, r);
            default: throw new UnsupportedOperationException("Operator: " + op);
        }
    }
}