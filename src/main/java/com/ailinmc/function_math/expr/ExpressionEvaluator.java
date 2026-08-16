package com.ailinmc.function_math.expr;

import java.util.List;

public class ExpressionEvaluator {
    //解析表达式字符串，返回抽象语法树
    public static AstNode parse(String expression) {
        Lexer lexer = new Lexer(expression);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }


    //直接计算表达式在给定 x 处的值
    public static double evaluate(String expression, double x) {
        AstNode node = parse(expression);
        return node.evaluate(x);
    }
}