package ec.com.ecommerce;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

import static ec.com.ecommerce.scanner.ControllerScanner.scanController;
import static ec.com.ecommerce.scanner.SwaggerScanner.scanSwaggerRoutes;

@Slf4j
@Component
public class AppScannerComponent {
    private final ApplicationContext context;
    // Use ObjectProvider to make KafkaTemplate optional
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    @Value("${route.scanner.swagger.enabled:true}")
    private boolean swaggerScanningEnabled;

    @Value("${route.scanner.controller.enabled:true}")
    private boolean controllerScanningEnabled;

    @Autowired
    public AppScannerComponent(ApplicationContext context, ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
        this.context = context;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate bean not found. Route scanning will be skipped. To enable, add spring-kafka and a KafkaTemplate bean to the application context.");
            return;
        }
        if (controllerScanningEnabled) scanController(context, kafkaTemplate);
        if (swaggerScanningEnabled) scanSwaggerRoutes(context, kafkaTemplate);
    }
}
