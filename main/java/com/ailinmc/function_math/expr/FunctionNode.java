package com.ailinmc.function_math.expr;

public class FunctionNode extends AstNode {
    private final String func;
    private final AstNode arg;
    private final AstNode arg2; // 第二参数，用于 log(base, value)

    public FunctionNode(String func, AstNode arg) {
        this.func = func;
        this.arg = arg;
        this.arg2 = null;
    }

    public FunctionNode(String func, AstNode arg, AstNode arg2) {
        this.func = func;
        this.arg = arg;
        this.arg2 = arg2;
    }

    @Override
    public double evaluate(double x) {
        double a = arg.evaluate(x);
        switch (func) {
            case "sin": return Math.sin(a);
            case "cos": return Math.cos(a);
            case "tan": return Math.tan(a);
            case "sqrt":
                if (arg2 != null) {
                    // sqrt(root, value): 求 value 的 root 次方根
                    double root = a;
                    double value = arg2.evaluate(x);
                    return Math.pow(value, 1.0 / root);
                }
                return Math.sqrt(a);
            case "ln": return Math.log(a);
            case "exp": return Math.exp(a);
            case "abs": return Math.abs(a);
            case "log":
                if (arg2 != null) {
                    // log(base, value): 以第一个参数为底，对第二个参数取对数
                    double base = a;
                    double value = arg2.evaluate(x);
                    return Math.log(value) / Math.log(base);
                }
                return Math.log10(a);
            default:
                if (func.startsWith("log")) {
                    int base = Integer.parseInt(func.substring(3));
                    return Math.log(a) / Math.log(base);
                }
                throw new UnsupportedOperationException("Function: " + func);
        }
    }
}
