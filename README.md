# Component Scan Library | Librería de Escaneo de Componentes

[![Java](https://img.shields.io/badge/Java-24-orange)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-blue)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 🇺🇸 English | [🇪🇸 Español](#-español)

### Overview

The **Component Scan Library** is a Spring Boot auto-configuration library that automatically discovers and registers REST controller routes and Swagger/OpenAPI documentation endpoints in a microservices architecture. It publishes route configurations to Apache Kafka for dynamic gateway routing.

### Key Features

- 🔍 **Automatic Controller Discovery**: Scans all `@RestController` annotated classes within the base package
- 📚 **Swagger/OpenAPI Integration**: Automatically detects and configures documentation routes
- 🚀 **Dynamic Route Publishing**: Sends route configurations to Kafka for gateway consumption
- ⚙️ **Configurable Scanning**: Enable/disable controller and Swagger scanning independently
- 🎯 **Gateway Integration**: Perfect for Spring Cloud Gateway implementations
- 📊 **Load Balancer Support**: Generates routes with `lb://` URIs for service discovery

### Architecture

```text
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Microservice  │    │  Component Scan  │    │   API Gateway   │
│                 │    │     Library      │    │                 │
│ @RestController │───▶│                  │───▶│ Dynamic Routes  │
│ @SwaggerConfig  │    │ ControllerScanner│    │                 │
│                 │    │ SwaggerScanner   │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                               │
                               ▼
                       ┌──────────────┐
                       │     Kafka    │
                       │ Route Topic  │
                       └──────────────┘
```

### Installation

Add the dependency to your `build.gradle`:

```gradle
dependencies {
    implementation project(":libs:component-scan")
    // or from repository
    implementation 'ec.com.ecommerce:component-scan:1.0.0'
}
```

### Configuration

Add these properties to your `application.yml` or `application.properties`:

```yaml
# Enable/disable scanning features
route:
  scanner:
    controller:
      enabled: true  # Enable controller scanning
    swagger:
      enabled: true  # Enable Swagger scanning
      include-webjars: true  # Include webjars routes
      include-swagger-resources: true  # Include swagger resources

# Kafka configuration (required)
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

# Service name (required for route generation)
spring:
  application:
    name: user-service
```

### Usage

#### 1. Basic Controller Setup

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        // Implementation
    }
    
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        // Implementation
    }
}
```

**Generated Routes:**

- Route ID: `user-usercontroller`
- URI: `lb://user-service`
- Predicates: `Path=/api/users/**`

#### 2. Swagger Integration

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management")
@OpenAPIDefinition(info = @Info(title = "User API", version = "1.0"))
public class UserController {
    
    @GetMapping
    @Operation(summary = "Get all users")
    public ResponseEntity<List<User>> getAllUsers() {
        // Implementation
    }
}
```

**Generated Swagger Routes:**

- Swagger UI: `Path=/swagger-ui/**`
- API Docs: `Path=/v3/api-docs/**`
- Webjars: `Path=/webjars/**`

#### 3. Route Configuration Message

The library publishes `RouteConfigMessage` objects to Kafka:

```json
{
  "routeId": "user-usercontroller",
  "uri": "lb://user-service",
  "predicates": ["Path=/api/users/**"],
  "filters": ["StripPrefix=0"],
  "orderNum": 0,
  "description": "Auto-generated route for user service - UserController",
  "enabled": true,
  "serviceName": "user"
}
```

### Advanced Configuration

#### Custom Scanner Configuration

```java
@Configuration
public class ScannerConfig {
    
    @Bean
    @ConditionalOnProperty(name = "route.scanner.custom.enabled", havingValue = "true")
    public CustomScanner customScanner() {
        return new CustomScanner();
    }
}
```

#### Gateway Aggregated Routes

```java
// For gateway service to aggregate all service documentation
List<String> services = Arrays.asList("user", "order", "product");
List<RouteConfigMessage> aggregatedRoutes = 
    SwaggerScanner.generateAggregatedSwaggerRoutes(services);
```

### Monitoring and Debugging

Enable debug logging to see scanning details:

```yaml
logging:
  level:
    ec.com.ecommerce.scanner: DEBUG
    ec.com.ecommerce.AppScannerComponent: DEBUG
```

Example debug output:
```
2024-10-25 10:30:15.123 INFO  [AppScannerComponent] - Starting route scanning for application: user-service
2024-10-25 10:30:15.125 INFO  [ControllerScanner] - Scanning controllers for service: user
2024-10-25 10:30:15.130 INFO  [ControllerScanner] - Sent route configuration: user-usercontroller -> [Path=/api/users/**]
2024-10-25 10:30:15.135 INFO  [SwaggerScanner] - Sent Swagger route configuration: user-swagger-ui -> [Path=/swagger-ui/**]
```

### Integration with Gateway

Your API Gateway should consume these Kafka messages to dynamically register routes:

```java
@KafkaListener(topics = "gateway-route-config")
public void handleRouteConfig(String key, String routeConfigJson) {
    RouteConfigMessage config = objectMapper.readValue(routeConfigJson, RouteConfigMessage.class);
    // Register route with Spring Cloud Gateway
    routeDefinitionRepository.save(convertToRouteDefinition(config));
}
```

---

## 🇪🇸 Español

### Descripción General

La **Librería de Escaneo de Componentes** es una librería de auto-configuración de Spring Boot que descubre y registra automáticamente rutas de controladores REST y endpoints de documentación Swagger/OpenAPI en una arquitectura de microservicios. Publica configuraciones de rutas a Apache Kafka para enrutamiento dinámico del gateway.

### Características Principales

- 🔍 **Descubrimiento Automático de Controladores**: Escanea todas las clases anotadas con `@RestController` dentro del paquete base
- 📚 **Integración Swagger/OpenAPI**: Detecta y configura automáticamente rutas de documentación
- 🚀 **Publicación Dinámica de Rutas**: Envía configuraciones de rutas a Kafka para consumo del gateway
- ⚙️ **Escaneo Configurable**: Habilita/deshabilita el escaneo de controladores y Swagger independientemente
- 🎯 **Integración con Gateway**: Perfecto para implementaciones de Spring Cloud Gateway
- 📊 **Soporte Load Balancer**: Genera rutas con URIs `lb://` para descubrimiento de servicios

### Arquitectura

```text
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  Microservicio  │    │ Librería de      │    │  API Gateway    │
│                 │    │ Escaneo de       │    │                 │
│ @RestController │───▶│ Componentes      │───▶│ Rutas Dinámicas │
│ @SwaggerConfig  │    │ ControllerScanner│    │                 │
│                 │    │ SwaggerScanner   │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                               │
                               ▼
                       ┌──────────────┐
                       │     Kafka    │
                       │ Tópico Rutas │
                       └──────────────┘
```

### Instalación

Añade la dependencia a tu `build.gradle`:

```gradle
dependencies {
    implementation project(":libs:component-scan")
    // o desde repositorio
    implementation 'ec.com.ecommerce:component-scan:1.0.0'
}
```

### Configuración

Añade estas propiedades a tu `application.yml` o `application.properties`:

```yaml
# Habilitar/deshabilitar características de escaneo
route:
  scanner:
    controller:
      enabled: true  # Habilitar escaneo de controladores
    swagger:
      enabled: true  # Habilitar escaneo de Swagger
      include-webjars: true  # Incluir rutas webjars
      include-swagger-resources: true  # Incluir recursos swagger

# Configuración de Kafka (requerida)
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

# Nombre del servicio (requerido para generación de rutas)
spring:
  application:
    name: user-service
```

### Uso

#### 1. Configuración Básica de Controlador

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        // Implementación
    }
    
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        // Implementación
    }
}
```

**Rutas Generadas:**

- ID de Ruta: `user-usercontroller`
- URI: `lb://user-service`
- Predicados: `Path=/api/users/**`

#### 2. Integración con Swagger

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "Gestión de Usuarios")
@OpenAPIDefinition(info = @Info(title = "API de Usuarios", version = "1.0"))
public class UserController {
    
    @GetMapping
    @Operation(summary = "Obtener todos los usuarios")
    public ResponseEntity<List<User>> getAllUsers() {
        // Implementación
    }
}
```

**Rutas de Swagger Generadas:**

- Swagger UI: `Path=/swagger-ui/**`
- Documentos API: `Path=/v3/api-docs/**`
- Webjars: `Path=/webjars/**`

#### 3. Mensaje de Configuración de Ruta

La librería publica objetos `RouteConfigMessage` a Kafka:

```json
{
  "routeId": "user-usercontroller",
  "uri": "lb://user-service",
  "predicates": ["Path=/api/users/**"],
  "filters": ["StripPrefix=0"],
  "orderNum": 0,
  "description": "Ruta auto-generada para servicio user - UserController",
  "enabled": true,
  "serviceName": "user"
}
```

### Configuración Avanzada

#### Configuración de Escáner Personalizado

```java
@Configuration
public class ScannerConfig {
    
    @Bean
    @ConditionalOnProperty(name = "route.scanner.custom.enabled", havingValue = "true")
    public CustomScanner customScanner() {
        return new CustomScanner();
    }
}
```

#### Rutas Agregadas del Gateway

```java
// Para el servicio gateway para agregar toda la documentación de servicios
List<String> services = Arrays.asList("user", "order", "product");
List<RouteConfigMessage> aggregatedRoutes = 
    SwaggerScanner.generateAggregatedSwaggerRoutes(services);
```

### Monitoreo y Depuración

Habilita logging de depuración para ver detalles del escaneo:

```yaml
logging:
  level:
    ec.com.ecommerce.scanner: DEBUG
    ec.com.ecommerce.AppScannerComponent: DEBUG
```

Ejemplo de salida de depuración:

```log
2024-10-25 10:30:15.123 INFO  [AppScannerComponent] - Iniciando escaneo de rutas para aplicación: user-service
2024-10-25 10:30:15.125 INFO  [ControllerScanner] - Escaneando controladores para servicio: user
2024-10-25 10:30:15.130 INFO  [ControllerScanner] - Enviada configuración de ruta: user-usercontroller -> [Path=/api/users/**]
2024-10-25 10:30:15.135 INFO  [SwaggerScanner] - Enviada configuración de ruta Swagger: user-swagger-ui -> [Path=/swagger-ui/**]
```

### Integración con Gateway

Tu API Gateway debe consumir estos mensajes de Kafka para registrar rutas dinámicamente:

```java
@KafkaListener(topics = "gateway-route-config")
public void handleRouteConfig(String key, String routeConfigJson) {
    RouteConfigMessage config = objectMapper.readValue(routeConfigJson, RouteConfigMessage.class);
    // Registrar ruta con Spring Cloud Gateway
    routeDefinitionRepository.save(convertToRouteDefinition(config));
}
```

---

## 📋 Requirements | Requisitos

- Java 24+
- Spring Boot 3.5.6+
- Apache Kafka
- Spring Cloud Gateway (for consuming routes | para consumir rutas)

## 🤝 Contributing | Contribuir

1. Fork the repository | Haz fork del repositorio
2. Create a feature branch | Crea una rama de característica
3. Commit your changes | Confirma tus cambios
4. Push to the branch | Empuja a la rama
5. Create a Pull Request | Crea un Pull Request

## 📄 License | Licencia

This project is licensed under the MIT License.
Este proyecto está licenciado bajo la Licencia MIT.

## 📞 Support | Soporte

For support, please create an issue in the repository.
Para soporte, por favor crea un issue en el repositorio.

---

Made with ❤️ by the E-commerce Team | Hecho con ❤️ por el Equipo de E-commerce
