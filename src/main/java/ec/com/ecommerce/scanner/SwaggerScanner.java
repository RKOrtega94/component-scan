package ec.com.ecommerce.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.com.ecommerce.scanner.dto.RouteConfigMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Swagger/OpenAPI documentation route scanner
 */
@Slf4j
public class SwaggerScanner {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Scan and generate Swagger/OpenAPI routes
     */
    public static void scanSwaggerRoutes(ApplicationContext context, KafkaTemplate<String, String> kafkaTemplate) {
        try {
            String serviceName = getServiceName(context);
            log.info("Scanning Swagger routes for service: {}", serviceName);

            // Check if Swagger/OpenAPI is available in the classpath
            if (!isSwaggerAvailable()) {
                log.info("Swagger/OpenAPI not detected in classpath for service: {}", serviceName);
                return;
            }

            List<RouteConfigMessage> swaggerRoutes = generateSwaggerRoutes(serviceName, context);
            
            for (RouteConfigMessage route : swaggerRoutes) {
                String jsonMessage = objectMapper.writeValueAsString(route);
                kafkaTemplate.send("gateway-route-config", serviceName, jsonMessage);
                log.info("Sent Swagger route configuration: {} -> {}", route.getRouteId(), route.getPredicates());
            }

            log.info("Completed scanning {} Swagger routes for service: {}", swaggerRoutes.size(), serviceName);
        } catch (Exception e) {
            log.error("Error scanning Swagger routes", e);
        }
    }

    /**
     * Check if Swagger/OpenAPI dependencies are available
     */
    private static boolean isSwaggerAvailable() {
        try {
            // Try to load springdoc classes
            Class.forName("org.springdoc.core.configuration.SpringDocConfiguration");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                // Try to load older swagger classes
                Class.forName("springfox.documentation.spring.web.plugins.Docket");
                return true;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }
    }

    /**
     * Generate standard Swagger/OpenAPI routes for a service
     */
    private static List<RouteConfigMessage> generateSwaggerRoutes(String serviceName, ApplicationContext context) {
        List<RouteConfigMessage> routes = new ArrayList<>();
        
        // Get configuration properties
        boolean includeWebjars = getBooleanProperty(context, "route.scanner.swagger.include-webjars", true);
        boolean includeSwaggerResources = getBooleanProperty(context, "route.scanner.swagger.include-swagger-resources", true);
        
        // Swagger UI route
        RouteConfigMessage swaggerUIRoute = RouteConfigMessage.builder()
                .routeId(serviceName + "-swagger-ui")
                .uri("lb://" + getFullServiceName(serviceName))
                .predicates(Arrays.asList("Path=/swagger-ui/**"))
                .filters(Arrays.asList("StripPrefix=0"))
                .orderNum(1) // High priority for documentation
                .description("Swagger UI for " + serviceName + " service")
                .enabled(true)
                .serviceName(serviceName)
                .build();
        routes.add(swaggerUIRoute);

        // Swagger UI index route
        RouteConfigMessage swaggerUIIndexRoute = RouteConfigMessage.builder()
                .routeId(serviceName + "-swagger-ui-index")
                .uri("lb://" + getFullServiceName(serviceName))
                .predicates(Arrays.asList("Path=/swagger-ui.html"))
                .filters(Arrays.asList("StripPrefix=0"))
                .orderNum(2)
                .description("Swagger UI index page for " + serviceName + " service")
                .enabled(true)
                .serviceName(serviceName)
                .build();
        routes.add(swaggerUIIndexRoute);

        // OpenAPI JSON/YAML documentation route
        RouteConfigMessage apiDocsRoute = RouteConfigMessage.builder()
                .routeId(serviceName + "-api-docs")
                .uri("lb://" + getFullServiceName(serviceName))
                .predicates(Arrays.asList("Path=/v3/api-docs/**"))
                .filters(Arrays.asList("StripPrefix=0"))
                .orderNum(3)
                .description("OpenAPI documentation for " + serviceName + " service")
                .enabled(true)
                .serviceName(serviceName)
                .build();
        routes.add(apiDocsRoute);

        // Conditionally add swagger resources route (for older versions)
        if (includeSwaggerResources) {
            RouteConfigMessage swaggerResourcesRoute = RouteConfigMessage.builder()
                    .routeId(serviceName + "-swagger-resources")
                    .uri("lb://" + getFullServiceName(serviceName))
                    .predicates(Arrays.asList("Path=/swagger-resources/**"))
                    .filters(Arrays.asList("StripPrefix=0"))
                    .orderNum(4)
                    .description("Swagger resources for " + serviceName + " service")
                    .enabled(true)
                    .serviceName(serviceName)
                    .build();
            routes.add(swaggerResourcesRoute);
        }

        // Conditionally add WebjarsLocator route (for Swagger UI assets)
        if (includeWebjars) {
            RouteConfigMessage webjarsRoute = RouteConfigMessage.builder()
                    .routeId(serviceName + "-webjars")
                    .uri("lb://" + getFullServiceName(serviceName))
                    .predicates(Arrays.asList("Path=/webjars/**"))
                    .filters(Arrays.asList("StripPrefix=0"))
                    .orderNum(5)
                    .description("Webjars assets for " + serviceName + " service")
                    .enabled(true)
                    .serviceName(serviceName)
                    .build();
            routes.add(webjarsRoute);
            log.debug("Added WebJars route for service: {}", serviceName);
        } else {
            log.debug("Skipped WebJars route for service: {} (disabled via configuration)", serviceName);
        }

        // Swagger configuration route
        RouteConfigMessage swaggerConfigRoute = RouteConfigMessage.builder()
                .routeId(serviceName + "-swagger-config")
                .uri("lb://" + getFullServiceName(serviceName))
                .predicates(Arrays.asList("Path=/v3/api-docs/swagger-config"))
                .filters(Arrays.asList("StripPrefix=0"))
                .orderNum(6)
                .description("Swagger configuration for " + serviceName + " service")
                .enabled(true)
                .serviceName(serviceName)
                .build();
        routes.add(swaggerConfigRoute);

        log.debug("Generated {} Swagger routes for service: {} (webjars: {}, swagger-resources: {})", 
                routes.size(), serviceName, includeWebjars, includeSwaggerResources);
        return routes;
    }

    /**
     * Generate aggregated Swagger route for Gateway
     * This allows accessing all service documentation from the gateway
     */
    public static List<RouteConfigMessage> generateAggregatedSwaggerRoutes(List<String> serviceNames) {
        List<RouteConfigMessage> routes = new ArrayList<>();

        // Create routes for each service's documentation accessible via gateway
        for (String serviceName : serviceNames) {
            // Route for service-specific Swagger UI via gateway
            RouteConfigMessage serviceSwaggerRoute = RouteConfigMessage.builder()
                    .routeId("gateway-swagger-" + serviceName)
                    .uri("lb://" + getFullServiceName(serviceName))
                    .predicates(Arrays.asList("Path=/docs/" + serviceName + "/**"))
                    .filters(Arrays.asList("StripPrefix=2", "AddRequestHeader=X-Service-Name," + serviceName))
                    .orderNum(10)
                    .description("Gateway aggregated Swagger for " + serviceName + " service")
                    .enabled(true)
                    .serviceName("gateway")
                    .build();
            routes.add(serviceSwaggerRoute);
        }

        return routes;
    }

    /**
     * Get full service name (with -service suffix)
     */
    private static String getFullServiceName(String serviceName) {
        return serviceName.endsWith("-service") ? serviceName : serviceName + "-service";
    }

    /**
     * Get service name from application context
     */
    private static String getServiceName(ApplicationContext context) {
        return context.getEnvironment().getProperty("spring.application.name", "unknown")
                .replaceAll("(?i)[-_]service$", "");
    }

    /**
     * Get boolean property from application context with default value
     */
    private static boolean getBooleanProperty(ApplicationContext context, String propertyName, boolean defaultValue) {
        String value = context.getEnvironment().getProperty(propertyName);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}