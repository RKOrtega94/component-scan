package ec.com.ecommerce.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.com.ecommerce.scanner.dto.RouteConfigMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Controller scanner
 */
@Slf4j
public class ControllerScanner {
    private static final String BASE_PACKAGE = "ec.com.ecommerce";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Scan controller
     */
    public static void scanController(ApplicationContext context, KafkaTemplate<String, String> kafkaTemplate) {
        try {
            String serviceName = getServiceName(context);
            Set<Class<?>> controllers = scanControllers(context);

            log.info("Scanning controllers for service: {}", serviceName);

            int routeOrder = 0;
            for (Class<?> controllerClass : controllers) {
                List<RouteConfigMessage> routes = extractRoutes(controllerClass, serviceName, routeOrder);

                for (RouteConfigMessage route : routes) {
                    String jsonMessage = objectMapper.writeValueAsString(route);
                    kafkaTemplate.send("gateway-route-config", serviceName, jsonMessage);
                    log.info("Sent route configuration: {} -> {}", route.getRouteId(), route.getPredicates());
                    routeOrder++;
                }
            }

            log.info("Completed scanning {} controllers for service: {}", controllers.size(), serviceName);
        } catch (Exception e) {
            log.error("Error scanning controllers", e);
        }
    }

    /**
     * Extract routes from controller class
     */
    private static List<RouteConfigMessage> extractRoutes(Class<?> controllerClass, String serviceName, int startOrder) {
        List<RouteConfigMessage> routes = new ArrayList<>();

        // Get base path from @RequestMapping at class level
        String basePath = "";
        if (controllerClass.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
            if (classMapping.value().length > 0) {
                basePath = classMapping.value()[0];
            } else if (classMapping.path().length > 0) {
                basePath = classMapping.path()[0];
            }
        }

        // Normalize base path
        basePath = normalizePath(basePath);

        // Get all methods with mapping annotations
        Method[] methods = controllerClass.getDeclaredMethods();
        Set<String> uniquePaths = new HashSet<>();

        for (Method method : methods) {
            String methodPath = extractMethodPath(method);
            if (methodPath != null) {
                String fullPath = normalizePath(basePath + methodPath);
                uniquePaths.add(fullPath);
            }
        }

        // Create route for base path with wildcard if we have mapped methods
        if (!uniquePaths.isEmpty()) {
            String pathPattern = basePath.equals("/") ? "/**" : basePath + "/**";
            
            RouteConfigMessage route = RouteConfigMessage.builder()
                    .routeId(serviceName + "-" + controllerClass.getSimpleName().toLowerCase())
                    .uri("lb://" + getFullServiceName(serviceName))
                    .predicates(Arrays.asList("Path=" + pathPattern))
                    .filters(Arrays.asList("StripPrefix=0"))
                    .orderNum(startOrder)
                    .description("Auto-generated route for " + serviceName + " service - " + controllerClass.getSimpleName())
                    .enabled(true)
                    .serviceName(serviceName)
                    .build();

            routes.add(route);
            log.debug("Generated route: {} -> {}", route.getRouteId(), pathPattern);
        }

        return routes;
    }

    /**
     * Extract path from method-level mapping annotations
     */
    private static String extractMethodPath(Method method) {
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            return getFirstPath(mapping.value(), mapping.path());
        }
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            return getFirstPath(mapping.value(), mapping.path());
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            return getFirstPath(mapping.value(), mapping.path());
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            return getFirstPath(mapping.value(), mapping.path());
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            return getFirstPath(mapping.value(), mapping.path());
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            PatchMapping mapping = method.getAnnotation(PatchMapping.class);
            return getFirstPath(mapping.value(), mapping.path());
        }
        return null;
    }

    /**
     * Get first path from value or path arrays
     */
    private static String getFirstPath(String[] value, String[] path) {
        if (value.length > 0) {
            return value[0];
        }
        if (path.length > 0) {
            return path[0];
        }
        return "";
    }

    /**
     * Normalize path by ensuring it starts with / and removing duplicate slashes
     */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        path = path.replaceAll("/+", "/");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /**
     * Get full service name (with -service suffix)
     */
    private static String getFullServiceName(String serviceName) {
        return serviceName + "-service";
    }

    /**
     * Get service name from application context
     *
     * @param context application context
     * @return service name
     */
    private static String getServiceName(ApplicationContext context) {
        return context.getEnvironment().getProperty("spring.application.name", "unknown").replaceAll("(?i)[-_]service$", "");
    }

    /**
     * Scan controllers
     *
     * @param context application context
     * @return set of controllers
     */
    private static Set<Class<?>> scanControllers(ApplicationContext context) {
        Set<Class<?>> controllers = new LinkedHashSet<>();
        String[] beanNames = context.getBeanNamesForAnnotation(RestController.class);
        for (String beanName : beanNames) {
            Object controller = context.getBean(beanName);
            Class<?> controllerClass = AopUtils.getTargetClass(controller);
            if (controllerClass.getPackageName().startsWith(BASE_PACKAGE)) {
                controllers.add(controllerClass);
            }
        }
        return controllers;
    }
}
