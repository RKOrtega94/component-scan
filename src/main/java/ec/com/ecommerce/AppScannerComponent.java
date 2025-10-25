package ec.com.ecommerce;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static ec.com.ecommerce.scanner.ControllerScanner.scanController;
import static ec.com.ecommerce.scanner.SwaggerScanner.scanSwaggerRoutes;

@Slf4j
@Component
public class AppScannerComponent {
    private final ApplicationContext context;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${route.scanner.swagger.enabled:true}")
    private boolean swaggerScanningEnabled;
    
    @Value("${route.scanner.controller.enabled:true}")
    private boolean controllerScanningEnabled;

    @Autowired
    public AppScannerComponent(ApplicationContext context, KafkaTemplate<String, String> kafkaTemplate) {
        this.context = context;
        this.kafkaTemplate = kafkaTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting route scanning for application: {}", 
                context.getEnvironment().getProperty("spring.application.name"));
        
        // Scan regular REST controllers
        if (controllerScanningEnabled) {
            log.info("Controller scanning is enabled");
            scanController(context, kafkaTemplate);
        } else {
            log.info("Controller scanning is disabled");
        }
        
        // Scan Swagger/OpenAPI routes
        if (swaggerScanningEnabled) {
            log.info("Swagger scanning is enabled");
            scanSwaggerRoutes(context, kafkaTemplate);
        } else {
            log.info("Swagger scanning is disabled");
        }
        
        log.info("Route scanning completed");
    }
}
