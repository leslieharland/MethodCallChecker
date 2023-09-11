import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CustomClassLoader extends ClassLoader {
    private final String classPath;

    // Define a constructor to specify the parent class loader and the class path
    public CustomClassLoader(ClassLoader parent, String classPath) {
        super(parent);
        this.classPath = classPath;
    }

    // Override the findClass method to load a class from the specified directory
    @Override
    public Class<?> findClass(String name) {
        byte[] classData = loadClassData(name);
        return defineClass(name, classData, 0, classData.length);
    }

    // Load class data from the specified directory
    public byte[] loadClassData(String className) {
        String classFilePath = classPath + File.separator + className.replace('.', File.separatorChar) + ".class";
        try (FileInputStream inputStream = new FileInputStream(classFilePath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int bytesRead;
            byte[] buffer = new byte[1024];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error loading class data: " + className, e);
        }
    }

}
