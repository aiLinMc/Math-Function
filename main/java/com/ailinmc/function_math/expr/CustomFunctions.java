package com.ailinmc.function_math.expr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class CustomFunctions {
    private static final Map<String, String> functions = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static File getConfigFile() {
        return new File(FMLPaths.GAMEDIR.get().toFile(), "custom_functions.json");
    }

    private static final String[] RESERVED_NAMES = {
        "x", "e", "pi",
        "sin", "cos", "tan",
        "asin", "acos", "atan",
        "sinh", "cosh", "tanh",
        "sqrt", "ln", "exp", "abs",
        "log", "floor", "ceil", "round", "trunc",
        "mod", "min", "max",
        "Ran#", "RanInt"
    };

    public static void loadFromFile() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, String> saved = GSON.fromJson(reader, MAP_TYPE);
                if (saved != null) {
                    functions.clear();
                    for (Map.Entry<String, String> entry : saved.entrySet()) {
                        if (!isReserved(entry.getKey())) {
                            functions.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveToFile() {
        File file = getConfigFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(functions, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isReserved(String name) {
        for (String reserved : RESERVED_NAMES) {
            if (reserved.equals(name)) {
                return true;
            }
        }
        return name.startsWith("log") && name.length() > 3;
    }

    public static boolean addFunction(String name, String expression) {
        if (isReserved(name)) {
            return false;
        }
        if (!isValidIdentifier(name)) {
            return false;
        }
        if (functions.containsKey(name)) {
            return false;
        }
        functions.put(name, expression);
        saveToFile();
        return true;
    }

    public static boolean modifyFunction(String name, String expression) {
        if (!functions.containsKey(name)) {
            return false;
        }
        try {
            ExpressionEvaluator.parse(expression);
            functions.put(name, expression);
            saveToFile();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidIdentifier(String name) {
        if (name.isEmpty()) {
            return false;
        }
        char firstChar = name.charAt(0);
        if (!Character.isLetter(firstChar)) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    public static String getFunction(String name) {
        return functions.get(name);
    }

    public static boolean removeFunction(String name) {
        boolean removed = functions.remove(name) != null;
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    public static void clearAllFunctions() {
        functions.clear();
        saveToFile();
    }

    public static boolean exists(String name) {
        return functions.containsKey(name);
    }

    public static Map<String, String> getAllFunctions() {
        return new HashMap<>(functions);
    }

    public static double evaluate(String name, double x) {
        String expression = functions.get(name);
        if (expression == null) {
            return Double.NaN;
        }
        return ExpressionEvaluator.evaluate(expression, x);
    }
}
