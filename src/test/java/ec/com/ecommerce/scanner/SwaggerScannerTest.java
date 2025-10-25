package ec.com.ecommerce.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.com.ecommerce.scanner.dto.RouteConfigMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SwaggerScanner
 */
@ExtendWith(MockitoExtension.class)
class SwaggerScannerTest {

    @Mock
    private ApplicationContext context;

    @Mock
    private Environment environment;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDetectSpringdocDependency() throws Exception {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("test-service");
        when(environment.getProperty("route.scanner.swagger.include-webjars")).thenReturn("true");
        when(environment.getProperty("route.scanner.swagger.include-swagger-resources")).thenReturn("true");

        // Mock Class.forName to simulate springdoc presence
        try (MockedStatic<Class> classMock = mockStatic(Class.class, CALLS_REAL_METHODS)) {
            classMock.when(() -> Class.forName("org.springdoc.core.configuration.SpringDocConfiguration"))
                    .thenReturn(Object.class);

            // When
            SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate);

            // Then
            verify(kafkaTemplate, atLeastOnce()).send(eq("gateway-route-config"), eq("test"), anyString());
        }
    }

    @Test
    void shouldDetectSpringfoxDependency() throws Exception {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("test-service");
        when(environment.getProperty("route.scanner.swagger.include-webjars")).thenReturn("true");
        when(environment.getProperty("route.scanner.swagger.include-swagger-resources")).thenReturn("true");

        // Mock Class.forName to simulate springfox presence (springdoc not present)
        try (MockedStatic<Class> classMock = mockStatic(Class.class, CALLS_REAL_METHODS)) {
            classMock.when(() -> Class.forName("org.springdoc.core.configuration.SpringDocConfiguration"))
                    .thenThrow(new ClassNotFoundException());
            classMock.when(() -> Class.forName("springfox.documentation.spring.web.plugins.Docket"))
                    .thenReturn(Object.class);

            // When
            SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate);

            // Then
            verify(kafkaTemplate, atLeastOnce()).send(eq("gateway-route-config"), eq("test"), anyString());
        }
    }

    @Test
    void shouldNotScanWhenSwaggerDependenciesNotPresent() throws Exception {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("test-service");

        // Mock Class.forName to simulate no Swagger dependencies
        try (MockedStatic<Class> classMock = mockStatic(Class.class, CALLS_REAL_METHODS)) {
            classMock.when(() -> Class.forName("org.springdoc.core.configuration.SpringDocConfiguration"))
                    .thenThrow(new ClassNotFoundException());
            classMock.when(() -> Class.forName("springfox.documentation.spring.web.plugins.Docket"))
                    .thenThrow(new ClassNotFoundException());

            // When
            SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate);

            // Then
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }
    }

    @Test
    void shouldGenerateCorrectRouteConfigMessagesForStandardPaths() throws Exception {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("test-service");
        when(environment.getProperty("route.scanner.swagger.include-webjars")).thenReturn("true");
        when(environment.getProperty("route.scanner.swagger.include-swagger-resources")).thenReturn("true");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        // Mock Class.forName to simulate springdoc presence
        try (MockedStatic<Class> classMock = mockStatic(Class.class, CALLS_REAL_METHODS)) {
            classMock.when(() -> Class.forName("org.springdoc.core.configuration.SpringDocConfiguration"))
                    .thenReturn(Object.class);

            // When
            SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate);

            // Then
            verify(kafkaTemplate, atLeastOnce()).send(
                    eq("gateway-route-config"),
                    keyCaptor.capture(),
                    messageCaptor.capture()
            );

            List<String> messages = messageCaptor.getAllValues();
            assertThat(messages).isNotEmpty();

            // Parse and verify route configurations
            boolean hasSwaggerUI = false;
            boolean hasSwaggerUIIndex = false;
            boolean hasApiDocs = false;

            for (String message : messages) {
                RouteConfigMessage route = objectMapper.readValue(message, RouteConfigMessage.class);

                // Check for Swagger UI route
                if (route.getRouteId().equals("test-swagger-ui")) {
                    hasSwaggerUI = true;
                    assertThat(route.getUri()).isEqualTo("lb://test-service");
                    assertThat(route.getPredicates()).contains("Path=/swagger-ui/**");
                    assertThat(route.getFilters()).contains("StripPrefix=0");
                    assertThat(route.getOrderNum()).isEqualTo(1);
                    assertThat(route.getEnabled()).isTrue();
                    assertThat(route.getServiceName()).isEqualTo("test");
                    assertThat(route.getDescription()).contains("Swagger UI");
                }

                // Check for Swagger UI index route
                if (route.getRouteId().equals("test-swagger-ui-index")) {
                    hasSwaggerUIIndex = true;
                    assertThat(route.getUri()).isEqualTo("lb://test-service");
                    assertThat(route.getPredicates()).contains("Path=/swagger-ui.html");
                    assertThat(route.getFilters()).contains("StripPrefix=0");
                    assertThat(route.getOrderNum()).isEqualTo(2);
                    assertThat(route.getEnabled()).isTrue();
                    assertThat(route.getServiceName()).isEqualTo("test");
                }

                // Check for API docs route
                if (route.getRouteId().equals("test-api-docs")) {
                    hasApiDocs = true;
                    assertThat(route.getUri()).isEqualTo("lb://test-service");
                    assertThat(route.getPredicates()).contains("Path=/v3/api-docs/**");
                    assertThat(route.getFilters()).contains("StripPrefix=0");
                    assertThat(route.getOrderNum()).isEqualTo(3);
                    assertThat(route.getEnabled()).isTrue();
                    assertThat(route.getServiceName()).isEqualTo("test");
                    assertThat(route.getDescription()).contains("OpenAPI documentation");
                }
            }

            assertThat(hasSwaggerUI).isTrue();
            assertThat(hasSwaggerUIIndex).isTrue();
            assertThat(hasApiDocs).isTrue();
        }
    }

    @Test
    void shouldGenerateAllStandardSwaggerRoutes() throws Exception {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("my-service");
        when(environment.getProperty("route.scanner.swagger.include-webjars")).thenReturn("true");
        when(environment.getProperty("route.scanner.swagger.include-swagger-resources")).thenReturn("true");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Mock Class.forName to simulate springdoc presence
        try (MockedStatic<Class> classMock = mockStatic(Class.class, CALLS_REAL_METHODS)) {
            classMock.when(() -> Class.forName("org.springdoc.core.configuration.SpringDocConfiguration"))
                    .thenReturn(Object.class);

            // When
            SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate);

            // Then
            verify(kafkaTemplate, times(6)).send(
                    eq("gateway-route-config"),
                    eq("my"),
                    messageCaptor.capture()
            );

            List<String> messages = messageCaptor.getAllValues();

            // Verify all expected routes are present
            List<String> routeIds = messages.stream()
                    .map(msg -> {
                        try {
                            return objectMapper.readValue(msg, RouteConfigMessage.class).getRouteId();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            assertThat(routeIds).containsExactlyInAnyOrder(
                    "my-swagger-ui",
                    "my-swagger-ui-index",
                    "my-api-docs",
                    "my-swagger-resources",
                    "my-webjars",
                    "my-swagger-config"
            );
        }
    }

    @Test
    void shouldRespectConfigurationForOptionalRoutes() throws Exception {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("test-service");
        when(environment.getProperty("route.scanner.swagger.include-webjars")).thenReturn("false");
        when(environment.getProperty("route.scanner.swagger.include-swagger-resources")).thenReturn("false");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Mock Class.forName to simulate springdoc presence
        try (MockedStatic<Class> classMock = mockStatic(Class.class, CALLS_REAL_METHODS)) {
            classMock.when(() -> Class.forName("org.springdoc.core.configuration.SpringDocConfiguration"))
                    .thenReturn(Object.class);

            // When
            SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate);

            // Then - should only have 4 routes (excluding webjars and swagger-resources)
            verify(kafkaTemplate, times(4)).send(
                    eq("gateway-route-config"),
                    eq("test"),
                    messageCaptor.capture()
            );

            List<String> messages = messageCaptor.getAllValues();
            List<String> routeIds = messages.stream()
                    .map(msg -> {
                        try {
                            return objectMapper.readValue(msg, RouteConfigMessage.class).getRouteId();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            // Should NOT contain webjars or swagger-resources routes
            assertThat(routeIds).doesNotContain("test-webjars", "test-swagger-resources");
            // Should contain these core routes
            assertThat(routeIds).contains(
                    "test-swagger-ui",
                    "test-swagger-ui-index",
                    "test-api-docs",
                    "test-swagger-config"
            );
        }
    }
}
