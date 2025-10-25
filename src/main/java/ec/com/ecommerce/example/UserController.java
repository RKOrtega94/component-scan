package ec.com.ecommerce.example;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Ejemplo de controlador que será escaneado automáticamente
 * tanto para rutas REST como para documentación Swagger
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Operations related to user management")
@OpenAPIDefinition(info = @Info(title = "User Service API", version = "1.0", description = "User management service"))
public class UserController {

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves a list of all users in the system")
    public ResponseEntity<List<User>> getAllUsers() {
        // Esto generará automáticamente las rutas:
        // - Path=/api/users/** (para el controlador)
        // - Path=/swagger-ui/** (para Swagger UI)
        // - Path=/v3/api-docs/** (para OpenAPI docs)
        List<User> users = Arrays.asList(
            new User(1L, "John Doe", "john@example.com"),
            new User(2L, "Jane Smith", "jane@example.com")
        );
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a specific user by their ID")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = new User(id, "John Doe", "john@example.com");
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @Operation(summary = "Create new user", description = "Creates a new user in the system")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        // Simulate user creation
        user.setId(System.currentTimeMillis());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates an existing user")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Deletes a user from the system")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        return ResponseEntity.ok().build();
    }

    // Clase User interna para el ejemplo
    public static class User {
        private Long id;
        private String name;
        private String email;

        public User() {}

        public User(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        // Getters y setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}

/*
RUTAS GENERADAS AUTOMÁTICAMENTE:

=== CONTROLADOR REST ===
✅ user-usercontroller
   URI: lb://user-service
   Predicates: Path=/api/users/**
   Filters: StripPrefix=0
   Description: Auto-generated route for user service - UserController

=== SWAGGER/OPENAPI ===
✅ user-swagger-ui
   URI: lb://user-service
   Predicates: Path=/swagger-ui/**
   Description: Swagger UI for user service

✅ user-swagger-ui-index
   URI: lb://user-service
   Predicates: Path=/swagger-ui.html
   Description: Swagger UI index page for user service

✅ user-api-docs
   URI: lb://user-service
   Predicates: Path=/v3/api-docs/**
   Description: OpenAPI documentation for user service

✅ user-webjars
   URI: lb://user-service
   Predicates: Path=/webjars/**
   Description: Webjars assets for user service

=== ACCESO VIA GATEWAY ===
- API REST: http://localhost:8080/api/users
- Swagger UI: http://localhost:8080/docs/user/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/docs/user/v3/api-docs
- Agregador: http://localhost:8080/swagger-aggregator
*/