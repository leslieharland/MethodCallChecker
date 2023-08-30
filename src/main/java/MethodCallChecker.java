import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.PrimitiveType;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;


public class MethodCallChecker {
    private static final Logger Log = Logger.getAnonymousLogger();

    public static void main(String[] args) {
        String filePath = System.getProperty("user.dir") + "\\method_calls.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br
                    .lines()
                    .map(MethodCallChecker::parseLine)
                    .forEach(MethodCallChecker::checkMethod);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String[] parseLine(String line) {
        return line.split(";");
    }

    private static void checkMethod(String[] parts) {
        if (parts.length >= 2) {
            String methodName = parts[0];
            String[] params = Arrays.copyOfRange(parts, 1, parts.length);

            Class<?> clazz = DummyClass.class;

            Class<?>[] paramTypes = Stream
                    .generate(() -> String.class)
                    .limit(params.length)
                    .toArray(Class[]::new);

            Method method = null;
            for (Method mtd : clazz.getDeclaredMethods()) {
                if (mtd.getName().equals(methodName)) {
                    method = mtd;
                }
            }
            // Method method = clazz.getMethod(methodName, paramTypes);

            if (method != null) {
                String filePath = System.getProperty("user.dir") + "\\src\\main\\java\\DummyClass.java";

                CompilationUnit compilationUnit = null;
                try {
                    compilationUnit = StaticJavaParser.parse(new File(filePath));
                } catch (FileNotFoundException e) {
                    Log.info(e.getMessage());
                }

                compilationUnit.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
                    if (methodDeclaration.getNameAsString().equals(methodName)) {
                        validateParameters(methodName, methodDeclaration.getParameters(), List.of(params));
                    }
                });
            } else {
                List<String> similarMethods = findSimilarMethods(clazz, methodName);
                if (!similarMethods.isEmpty()) {
                    if (similarMethods.contains(methodName)) {
                        return;
                    }
                    System.out.println("Method " + methodName + " does not exist. \nFound similar methods, enter " +
                            "selection to replace." +
                            " \n");
                    for (int i = 0 ; i < similarMethods.size(); ++i) {
                     System.out.printf("[%d] %s%n\n", i, similarMethods.get(i));
                    }
                    int idx = readUserInput();
                    applyFix(methodName, similarMethods.get(idx));
                } else {
                    System.out.println("Method " + methodName + " does not exist or parameter types do not match.");
                }
            }
        }
    }

    private static boolean validateParameters(String methodName, NodeList<Parameter> nodes, List<String> params) {
        if (nodes.size() != params.size()) {
            System.out.println("Expected method {" + methodName + "} to have " + nodes.size() +
                            " params, found " + params.size() + " params");
            return false;
        }

        for (int i = 0; i < nodes.size(); i++) {
            if (!validateParameter(methodName, nodes.get(i), params.get(i))) {
                return false;
            }
        }

        return true;
    }

    private static int readUserInput() {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextInt();
    }

    private static void applyFix(String toBeReplaced, String replacement) {

        // executeAndPrintOutput(List.of("cmd", "/c" , "dir"));
        // String currentDirectory = System.getProperty("user.dir");
        // System.out.println("Current directory: " + currentDirectory);
        // List<String> commands = new ArrayList<>();
        // commands.add("powershell");
        // commands.add("-Command");
        // commands.add("Get-ChildItem -Path src -Filter method_calls.txt | " +
        //         "ForEach-Object" +
        //         " " +
        //         "{ " +
        //         "(Get-Content " +
        //         "$_) -replace " +
        //         "'" + toBeReplaced + "', '" + replacement + "' | Set-Content $_ }");
        //
        //
        // Log.info(String.join("", commands));
        // ProcessBuilder pb = new ProcessBuilder(commands);
        // try {
        //     Process process = pb.start();
        //     int exitCode = process.waitFor();
        //
        //     if (exitCode == 0) {
        //         System.out.println("Text replacement completed successfully.");
        //     } else {
        //         System.out.println("Text replacement encountered an error.");
        //     }
        // } catch (IOException | InterruptedException e) {
        //     Log.info(e.getMessage());
        // }

        replaceTextInFiles(System.getProperty("user.dir"), toBeReplaced, replacement);

    }

    private static void replaceTextInFiles(String directoryPath, String toBeReplaced, String replacement){
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                Log.info(file.getAbsolutePath());
                if (file
                        .getName()
                        .endsWith(".txt")) {
                    try {
                        List<String> lines = Files.readAllLines(Paths.get(file.getAbsolutePath()));
                        for (int i = 0; i < lines.size(); i++) {
                            lines.set(i, lines
                                    .get(i)
                                    .replace(toBeReplaced, replacement));
                        }
                        Files.write(Paths.get(file.getAbsolutePath()), lines);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static boolean validateParameter(String methodName, Parameter node, String param) {
        Function<String, Boolean> validator;

        PrimitiveType paramType = node.getType().asPrimitiveType().toPrimitiveType().get();
        String paramName = node.getNameAsString();

        if (paramType.equals(PrimitiveType.charType())) {
            validator = s -> true;
        } else if (paramType.equals(PrimitiveType.intType())) {
            validator = s -> s.matches("\\d+");
        } else if (paramType.equals(PrimitiveType.charType())) {
            validator = s -> s.matches("-?\\d+(\\.\\d+)?");
        } else if (paramType.equals(PrimitiveType.booleanType())) {
            validator = s -> s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
        } else {
            validator = s -> false;
        }

        if (!validator.apply(param)) {
            System.out.println("Expected method {" + methodName + "} parameter {" +  paramName + "} to be " + paramType +
                    ", but " +
                    "found " + param);
            return false;
        }
        return true;
    }

    private static List<String> findSimilarMethods(Class<?> clazz, String targetMethod) {
        List<String> similarMethods = new ArrayList<>();
        Method[] methods = clazz.getDeclaredMethods();
        JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance();

        // Log.info("Checking target method " + targetMethod);
        for (Method method : methods) {
            String methodName = method.getName();

            double similarity = jaroWinklerDistance.apply(methodName, targetMethod);
            // Log.info(methodName + " with similarity " + similarity);
            if (similarity < 0.1) { // You can adjust the threshold as needed
                similarMethods.add(methodName);
            }
        }

        return similarMethods;
    }

    private static void executeAndPrintOutput(List<String> commands) {
        ProcessBuilder processBuilder = new ProcessBuilder(commands);

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Command executed successfully.");

                // Read and display the output of the process
                InputStream inputStream = process.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                reader.close();
            } else {
                System.out.println("Command encountered an error.");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}