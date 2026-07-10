package com.ailinmc.function_math.expr;

public class RecursionException extends RuntimeException {
    public RecursionException() {
        super("循环调用！");
    }
}
