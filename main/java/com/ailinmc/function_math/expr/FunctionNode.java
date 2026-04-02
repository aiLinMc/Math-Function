package com.ailinmc.function_math.expr;

public class FunctionNode extends AstNode {
    private final String func;
    private final AstNode arg;

    public FunctionNode(String func, AstNode arg) {
        this.func = func;
        this.arg = arg;
    }

    @Override
    public double evaluate(double x) {
        double a = arg.evaluate(x);
        switch (func) {
            case "sin": return Math.sin(a);
            case "cos": return Math.cos(a);
            case "tan": return Math.tan(a);
            case "sqrt": return Math.sqrt(a);
            case "log": return Math.log10(a);
            case "ln": return Math.log(a);
            default: throw new UnsupportedOperationException("Function: " + func);
        }
    }
}