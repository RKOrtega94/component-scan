package ec.com.ecommerce.scanner;

import ec.com.ecommerce.scanner.dto.RouteConfigMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerScannerTest {

    @Mock
    private ApplicationContext context;

    @Mock
    private Environment environment;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldScanControllersAndSendToKafka() {
        // Given
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn("test-service");
        when(context.getBeanNamesForAnnotation(RestController.class)).thenReturn(new String[]{"testController"});
        when(context.getBean("testController")).thenReturn(new TestController());

        // When
        ControllerScanner.scanController(context, kafkaTemplate);

        // Then
        verify(kafkaTemplate, atLeastOnce()).send(eq("gateway-route-config"), anyString(), anyString());
    }

    @RestController
    @RequestMapping("/api/test")
    static class TestController {

        @GetMapping("/users")
        public String getUsers() {
            return "users";
        }

        @GetMapping("/orders")
        public String getOrders() {
            return "orders";
        }
    }
}