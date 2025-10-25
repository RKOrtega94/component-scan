package ec.com.ecommerce;

import ec.com.ecommerce.scanner.ControllerScanner;
import ec.com.ecommerce.scanner.SwaggerScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

/**
 * Unit tests for AppScannerComponent
 */
@ExtendWith(MockitoExtension.class)
class AppScannerComponentTest {

    @Mock
    private ApplicationContext context;

    @Mock
    private Environment environment;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private AppScannerComponent appScannerComponent;

    @Test
    void shouldCallBothScannersWhenBothEnabled() {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name")).thenReturn("test-service");
        
        // Set both scanners to enabled
        ReflectionTestUtils.setField(appScannerComponent, "swaggerScanningEnabled", true);
        ReflectionTestUtils.setField(appScannerComponent, "controllerScanningEnabled", true);

        // When
        try (MockedStatic<ControllerScanner> controllerScannerMock = mockStatic(ControllerScanner.class);
             MockedStatic<SwaggerScanner> swaggerScannerMock = mockStatic(SwaggerScanner.class)) {
            
            appScannerComponent.onApplicationReady();

            // Then
            controllerScannerMock.verify(() -> ControllerScanner.scanController(context, kafkaTemplate), times(1));
            swaggerScannerMock.verify(() -> SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate), times(1));
        }
    }

    @Test
    void shouldSkipControllerScannerWhenDisabled() {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name")).thenReturn("test-service");
        
        // Set controller scanner to disabled, swagger scanner to enabled
        ReflectionTestUtils.setField(appScannerComponent, "swaggerScanningEnabled", true);
        ReflectionTestUtils.setField(appScannerComponent, "controllerScanningEnabled", false);

        // When
        try (MockedStatic<ControllerScanner> controllerScannerMock = mockStatic(ControllerScanner.class);
             MockedStatic<SwaggerScanner> swaggerScannerMock = mockStatic(SwaggerScanner.class)) {
            
            appScannerComponent.onApplicationReady();

            // Then
            controllerScannerMock.verify(() -> ControllerScanner.scanController(context, kafkaTemplate), never());
            swaggerScannerMock.verify(() -> SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate), times(1));
        }
    }

    @Test
    void shouldSkipSwaggerScannerWhenDisabled() {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name")).thenReturn("test-service");
        
        // Set swagger scanner to disabled, controller scanner to enabled
        ReflectionTestUtils.setField(appScannerComponent, "swaggerScanningEnabled", false);
        ReflectionTestUtils.setField(appScannerComponent, "controllerScanningEnabled", true);

        // When
        try (MockedStatic<ControllerScanner> controllerScannerMock = mockStatic(ControllerScanner.class);
             MockedStatic<SwaggerScanner> swaggerScannerMock = mockStatic(SwaggerScanner.class)) {
            
            appScannerComponent.onApplicationReady();

            // Then
            controllerScannerMock.verify(() -> ControllerScanner.scanController(context, kafkaTemplate), times(1));
            swaggerScannerMock.verify(() -> SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate), never());
        }
    }

    @Test
    void shouldSkipBothScannersWhenBothDisabled() {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name")).thenReturn("test-service");
        
        // Set both scanners to disabled
        ReflectionTestUtils.setField(appScannerComponent, "swaggerScanningEnabled", false);
        ReflectionTestUtils.setField(appScannerComponent, "controllerScanningEnabled", false);

        // When
        try (MockedStatic<ControllerScanner> controllerScannerMock = mockStatic(ControllerScanner.class);
             MockedStatic<SwaggerScanner> swaggerScannerMock = mockStatic(SwaggerScanner.class)) {
            
            appScannerComponent.onApplicationReady();

            // Then
            controllerScannerMock.verify(() -> ControllerScanner.scanController(context, kafkaTemplate), never());
            swaggerScannerMock.verify(() -> SwaggerScanner.scanSwaggerRoutes(context, kafkaTemplate), never());
        }
    }
}
