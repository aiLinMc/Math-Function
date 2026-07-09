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
        switch (func) {
            case "Ran#":
                return Math.random();
            case "RanInt":
                if (arg2 != null) {
                    double min = arg.evaluate(x);
                    double max = arg2.evaluate(x);
                    double lower = Math.min(min, max);
                    double upper = Math.max(min, max);
                    return (int) (lower + Math.random() * (upper - lower + 1));
                }
                return Double.NaN;
            case "sin": return Math.sin(arg.evaluate(x));
            case "cos": return Math.cos(arg.evaluate(x));
            case "tan": return Math.tan(arg.evaluate(x));
            case "sqrt":
                if (arg2 != null) {
                    double root = arg.evaluate(x);
                    double value = arg2.evaluate(x);
                    return Math.pow(value, 1.0 / root);
                }
                return Math.sqrt(arg.evaluate(x));
            case "ln": return Math.log(arg.evaluate(x));
            case "exp": return Math.exp(arg.evaluate(x));
            case "abs": return Math.abs(arg.evaluate(x));
            case "floor": return Math.floor(arg.evaluate(x));
            case "ceil": return Math.ceil(arg.evaluate(x));
            case "round": return Math.round(arg.evaluate(x));
            case "trunc": return Math.floor(arg.evaluate(x) >= 0 ? arg.evaluate(x) : -Math.floor(-arg.evaluate(x)));
            case "mod":
                if (arg2 != null) {
                    return arg.evaluate(x) % arg2.evaluate(x);
                }
                return Double.NaN;
            case "min":
                if (arg2 != null) {
                    return Math.min(arg.evaluate(x), arg2.evaluate(x));
                }
                return Double.NaN;
            case "max":
                if (arg2 != null) {
                    return Math.max(arg.evaluate(x), arg2.evaluate(x));
                }
                return Double.NaN;
            case "log":
                if (arg2 != null) {
                    double base = arg.evaluate(x);
                    double value = arg2.evaluate(x);
                    return Math.log(value) / Math.log(base);
                }
                return Math.log10(arg.evaluate(x));
            default:
                if (func.startsWith("log")) {
                    double a = arg.evaluate(x);
                    int base = Integer.parseInt(func.substring(3));
                    return Math.log(a) / Math.log(base);
                }
                throw new UnsupportedOperationException("Function: " + func);
        }
    }
}
