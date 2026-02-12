package ec.com.ecommerce.scanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for security module configuration messages sent to Kafka
 * Represents modules, submodules, actions, and permissions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityModuleConfigMessage {
    /**
     * Type: MODULE, SUBMODULE, ACTION, PERMISSION
     */
    private String type;
    
    /**
     * Name of the module/submodule/action/permission
     */
    private String name;
    
    /**
     * Description
     */
    private String description;
    
    /**
     * For submodules: parent module name
     * For actions: parent submodule name
     * For permissions: action name
     */
    private String parent;
    
    /**
     * For actions: full API path (e.g., /api/roles/retrieve)
     */
    private String path;
    
    /**
     * Base service/module name
     */
    private String serviceName;
}
