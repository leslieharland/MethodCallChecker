import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
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

    private static final String dataRoot = "";

    private static final String classFilePath = "\\target\\classes\\";
    private static final String inspectionClass= "file.java";

    private static final Class<?> clazz = instantiateClassFromUrl();
    public static void main(String[] args) {
        File directory = new File(dataRoot);
        File[] files = directory.listFiles();
        // Log.info(String.valueOf(files.length));
        if (files != null) {
            for (File file : files) {
                // Log.info(file.getName());
                if (!file.getName().endsWith(".txt")) {
                    continue;
                }
                String filePath = file.getAbsolutePath();

                try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PrintStream customPrintStream = new PrintStream(outputStream);
                    PrintStream originalPrintStream = System.out;
                    System.setOut(customPrintStream);
                    br
                            .lines()
                            .map(MethodCallChecker::parseLine)
                            .forEach(MethodCallChecker::checkMethod)
                            ;
                    System.setOut(originalPrintStream);
                    if (!outputStream.toString().isEmpty()) {
                        System.out.println(file.getName());
                        System.out.println(outputStream);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String[] parseLine(String line) {
        return line.split(";");
    }

    private static void checkMethod(String[] parts) {
        // System.out.println("Checking method " + Arrays.toString(parts));
        if (parts.length >= 2) {
            String methodName = parts[0];
            String[] params = Arrays.copyOfRange(parts, 1, parts.length);

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
                String filePath = inspectionClass;

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
                    System.out.println("Method " + methodName + " does not exist. \nFound similar methods" +
                            " \n");
                    for (int i = 0 ; i < similarMethods.size(); ++i) {
                     System.out.printf("[%d] %s%n\n", i, similarMethods.get(i));
                    }
                    // int idx = readUserInput();
                    // applyFix(methodName, similarMethods.get(idx));
                } else {
                    //System.out.println("Method " + methodName + " does not exist or parameter types do not match.");
                }
            }
        }
    }

    private static boolean validateParameters(String methodName, NodeList<Parameter> nodes, List<String> params) {
        if (nodes.size() != params.size()) {
            //System.out.println("Expected method {" + methodName + "} to have " + nodes.size() +
            //                 " params, found " + params.size() + " params");
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
        // //System.out.println("Current directory: " + currentDirectory);
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
        //         //System.out.println("Text replacement completed successfully.");
        //     } else {
        //         //System.out.println("Text replacement encountered an error.");
        //     }
        // } catch (IOException | InterruptedException e) {
        //     Log.info(e.getMessage());
        // }

        //replaceTextInFiles(dataRoot, toBeReplaced, replacement);

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
        String paramName = node.getNameAsString();

        if (node.getType().toString().equals("String")) {
            validator = s -> true;
        } else if (node.getType().toString().equals("int")) {
            validator = s -> s.matches("\\d+");
        } else if (node.getType().toString().equals("char")) {
            validator = s -> s.matches("-?\\d+(\\.\\d+)?");
        } else if (node.getType().toString().equals("boolean")) {
            validator = s -> s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
        } else {
            validator = s -> false;
        }

        if (!validator.apply(param)) {
            //System.out.println("Expected method {" + methodName + "} parameter {" +  paramName + "} to be " + paramType +
            //         ", but " +
            //         "found " + param);
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
            // System.out.println(methodName);
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
                //System.out.println("Command executed successfully.");

                // Read and display the output of the process
                InputStream inputStream = process.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    //System.out.println(line);
                }
                reader.close();
            } else {
                //System.out.println("Command encountered an error.");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Class<?> instantiateClassFromUrl() {
        String className = "com.example.CommandExecutor"; // Fully qualified class name

        try {
            // URL[] urls = new URL[]{new File(classFilePath).toURI().toURL()};
            // System.out.println(Arrays.toString(urls));
            // URLClassLoader classLoader = new URLClassLoader(urls);
            //
            // Class<?> loadedClass = classLoader.loadClass(className);
            //
            // Constructor<?> constructor = loadedClass.getConstructor();
            //
            // return (Class<?>) constructor.newInstance();

            CustomClassLoader customClassLoader = new CustomClassLoader(ClassLoader.getSystemClassLoader(), classFilePath);

            // Load the class using the custom class loader
            Class<?> loadedClass = customClassLoader.loadClass(className);

            return (Class<?>) loadedClass;

        } catch (RuntimeException | ClassNotFoundException e) {
            Log.info(e.getMessage());
        }
        return null;
    }
}
