import java.nio.file.Paths;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Standalone test runner for MapperTests to bypass Gradle compilation issues
 */
public class MapperTestRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Mapper Test Runner ===");
        
        // Create a classpath from build directory
        List<URL> urls = new ArrayList<>();
        urls.add(Paths.get("build/classes/java/main").toUri().toURL());
        urls.add(Paths.get("build/classes/java/test").toUri().toURL());
        
        // Add dependencies
        String[] gradleDeps = {
            "org.mapstruct:mapstruct:1.5.5.Final",
            "org.springframework:spring-context:6.0.12",
            "org.junit.jupiter:junit-jupiter-api:5.10.0",
            "org.assertj:assertj-core:3.24.2",
            "org.projectlombok:lombok:1.18.30"
        };
        
        URLClassLoader classLoader = new URLClassLoader(
            urls.toArray(new URL[0]),
            MapperTestRunner.class.getClassLoader()
        );
        
        try {
            // Load the test class
            Class<?> testClass = classLoader.loadClass("com.oddiya.mapper.MapperTests");
            Object testInstance = testClass.getDeclaredConstructor().newInstance();
            
            // Find setUp method
            Method setUpMethod = null;
            try {
                setUpMethod = testClass.getDeclaredMethod("setUp");
            } catch (NoSuchMethodException e) {
                System.out.println("No setUp method found");
            }
            
            if (setUpMethod != null) {
                setUpMethod.invoke(testInstance);
                System.out.println("✓ setUp completed");
            }
            
            // Find and run test methods
            Method[] methods = testClass.getDeclaredMethods();
            int passed = 0;
            int failed = 0;
            
            for (Method method : methods) {
                if (method.getName().startsWith("test") || 
                    method.isAnnotationPresent(classLoader.loadClass("org.junit.jupiter.api.Test"))) {
                    
                    try {
                        System.out.print("Running " + method.getName() + "... ");
                        method.invoke(testInstance);
                        System.out.println("✓ PASS");
                        passed++;
                    } catch (Exception e) {
                        System.out.println("✗ FAIL: " + e.getCause().getMessage());
                        failed++;
                    }
                }
            }
            
            System.out.println("\n=== Results ===");
            System.out.println("Passed: " + passed);
            System.out.println("Failed: " + failed);
            System.out.println("Total:  " + (passed + failed));
            
            if (failed == 0) {
                System.out.println("🎉 All mapper tests PASSED!");
            } else {
                System.out.println("❌ " + failed + " tests failed");
            }
            
        } catch (ClassNotFoundException e) {
            System.err.println("Could not find test class: " + e.getMessage());
            System.err.println("Make sure to compile with: ./gradlew compileJava");
        }
    }
}