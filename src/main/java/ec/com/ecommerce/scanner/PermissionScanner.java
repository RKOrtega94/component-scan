package ec.com.ecommerce.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.com.ecommerce.scanner.dto.SecurityModuleConfigMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Permission scanner - extracts modules, submodules, actions and permissions from controllers
 */
@Slf4j
public class PermissionScanner {
    private PermissionScanner() {
        throw new IllegalStateException("Utility class");
    }

    private static final String BASE_PACKAGE = "ec.com.ecommerce";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("hasAuthority\\(['\"]([^'\"]+)['\"]\\)|hasAnyAuthority\\(([^)]+)\\)");

    /**
     * Scan controllers and publish security modules, submodules, actions and permissions
     */
    public static void scanPermissions(ApplicationContext context, KafkaTemplate<String, String> kafkaTemplate) {
        try {
            String serviceName = getServiceName(context);

            // Skip permission scanning for auth service
            if ("auth".equalsIgnoreCase(serviceName)) {
                log.info("Skipping permission scanning for service: {} (auth service excluded)", serviceName);
                return;
            }

            String securityTopic = getSecurityTopic(context);
            Set<Class<?>> controllers = scanControllers(context);

            if (controllers.isEmpty()) {
                log.debug("No controllers found for permission scanning in service: {}", serviceName);
                return;
            }

            log.info("=== Scanning security modules for service: {} ===", serviceName);

            // Publish module (service name)
            publishModule(securityTopic, kafkaTemplate, serviceName,
                "Auto-generated security module for " + serviceName + " service");

            // Scan controllers and publish submodules, actions, permissions
            for (Class<?> controllerClass : controllers) {
                String submoduleName = extractSubmoduleName(controllerClass);
                String basePath = extractBasePath(controllerClass);

                // Publish submodule
                publishSubmodule(securityTopic, kafkaTemplate, serviceName, submoduleName,
                    "Auto-generated submodule for " + controllerClass.getSimpleName());

                // Extract and publish actions with permissions
                extractAndPublishActionsWithPermissions(securityTopic, kafkaTemplate,
                    serviceName, submoduleName, basePath, controllerClass);
            }

            log.info("✓ Completed scanning security modules for service: {}", serviceName);

        } catch (Exception e) {
            log.error("✗ Error scanning permissions", e);
        }
    }

    /**
     * Extract submodule name from controller class (remove "Controller" suffix)
     */
    private static String extractSubmoduleName(Class<?> controllerClass) {
        String name = controllerClass.getSimpleName();
        return name.replaceAll("(?i)controller$", "").toLowerCase();
    }

    /**
     * Extract base path from controller class @RequestMapping
     */
    private static String extractBasePath(Class<?> controllerClass) {
        if (controllerClass.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
            if (mapping.value().length > 0) {
                return normalizePath(mapping.value()[0]);
            }
            if (mapping.path().length > 0) {
                return normalizePath(mapping.path()[0]);
            }
        }
        return "";
    }

    /**
     * Extract and publish actions with their permissions from @PreAuthorize
     */
    private static void extractAndPublishActionsWithPermissions(
            String topic, KafkaTemplate<String, String> kafkaTemplate,
            String serviceName, String submoduleName, String basePath, Class<?> controllerClass) {

        try {
            Method[] methods = controllerClass.getDeclaredMethods();
            Set<String> processedPermissions = new HashSet<>();

            for (Method method : methods) {
                String methodPath = extractMethodPath(method);
                if (methodPath == null) {
                    continue;
                }

                String actionName = extractActionName(method.getName());
                String fullPath = normalizePath(basePath + methodPath);

                // Publish action
                SecurityModuleConfigMessage actionMsg = SecurityModuleConfigMessage.builder()
                    .type("ACTION")
                    .name(actionName)
                    .parent(submoduleName)
                    .path(fullPath)
                    .serviceName(serviceName)
                    .description("Action: " + actionName + " for " + submoduleName)
                    .build();

                String actionJson = objectMapper.writeValueAsString(actionMsg);
                kafkaTemplate.send(topic, serviceName, actionJson);
                log.debug("Published action: {} -> {}", actionName, fullPath);

                // Extract and publish permissions from @PreAuthorize
                if (method.isAnnotationPresent(PreAuthorize.class)) {
                    PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);
                    Set<String> permissions = extractPermissionsFromPreAuthorize(preAuth.value());

                    for (String permission : permissions) {
                        // Avoid duplicate permissions
                        String permissionKey = submoduleName + ":" + permission;
                        if (processedPermissions.contains(permissionKey)) {
                            continue;
                        }
                        processedPermissions.add(permissionKey);

                        SecurityModuleConfigMessage permMsg = SecurityModuleConfigMessage.builder()
                            .type("PERMISSION")
                            .name(permission)
                            .parent(actionName)
                            .serviceName(serviceName)
                            .description("Permission: " + permission + " for action " + actionName)
                            .build();

                        String permJson = objectMapper.writeValueAsString(permMsg);
                        kafkaTemplate.send(topic, serviceName, permJson);
                        log.debug("Published permission: {} for action: {}", permission, actionName);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error extracting actions and permissions from controller: {}", controllerClass.getSimpleName(), e);
        }
    }

    /**
     * Extract action name from method name (remove Form/Json suffix)
     */
    private static String extractActionName(String methodName) {
        return methodName
            .replaceAll("(?i)(Form|Json)$", "")
            .toLowerCase();
    }

    /**
     * Extract permissions from @PreAuthorize annotation value
     * Supports: hasAuthority('PERM'), hasAnyAuthority('PERM1', 'PERM2')
     */
    private static Set<String> extractPermissionsFromPreAuthorize(String value) {
        Set<String> permissions = new HashSet<>();

        if (value == null || value.isEmpty()) {
            return permissions;
        }

        Matcher matcher = PERMISSION_PATTERN.matcher(value);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Single permission from hasAuthority
                permissions.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                // Multiple permissions from hasAnyAuthority
                String[] perms = matcher.group(2).split(",");
                for (String perm : perms) {
                    String cleaned = perm.trim().replaceAll("['\"]", "");
                    if (!cleaned.isEmpty()) {
                        permissions.add(cleaned);
                    }
                }
            }
        }

        return permissions;
    }

    /**
     * Extract path from method-level mapping annotations
     */
    private static String extractMethodPath(Method method) {
        String path = MethodPathExtractor.extractPath(method);
        return path;
    }

    /**
     * Normalize path
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
     * Publish module to Kafka
     */
    private static void publishModule(String topic, KafkaTemplate<String, String> kafkaTemplate,
                                     String moduleName, String description) throws Exception {
        SecurityModuleConfigMessage msg = SecurityModuleConfigMessage.builder()
            .type("MODULE")
            .name(moduleName)
            .description(description)
            .serviceName(moduleName)
            .build();
        String json = objectMapper.writeValueAsString(msg);
        kafkaTemplate.send(topic, moduleName, json);
        log.info("Published module: {}", moduleName);
    }

    /**
     * Publish submodule to Kafka
     */
    private static void publishSubmodule(String topic, KafkaTemplate<String, String> kafkaTemplate,
                                        String serviceName, String submoduleName, String description) throws Exception {
        SecurityModuleConfigMessage msg = SecurityModuleConfigMessage.builder()
            .type("SUBMODULE")
            .name(submoduleName)
            .parent(serviceName)
            .description(description)
            .serviceName(serviceName)
            .build();
        String json = objectMapper.writeValueAsString(msg);
        kafkaTemplate.send(topic, serviceName, json);
        log.debug("Published submodule: {} in service: {}", submoduleName, serviceName);
    }

    /**
     * Get security topic from properties
     */
    private static String getSecurityTopic(ApplicationContext context) {
        return context.getEnvironment().getProperty("route.scanner.kafka.security-topic", "security-modules-config");
    }

    /**
     * Get service name from application context
     */
    private static String getServiceName(ApplicationContext context) {
        return context.getEnvironment().getProperty("spring.application.name", "unknown")
            .replaceAll("(?i)[-_]service$", "");
    }

    /**
     * Scan all RestControllers in the application
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

    /**
     * Helper class to extract method paths
     */
    private static class MethodPathExtractor {
        static String extractPath(Method method) {
            return ControllerScanner.extractMethodPathStatic(method);
        }
    }
}
