package ec.com.ecommerce.scanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for route configuration messages sent to Kafka
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteConfigMessage {
    private String routeId;
    private String uri;
    private List<String> predicates;
    private List<String> filters;
    private Integer orderNum;
    private String description;
    private Boolean enabled;
    private String serviceName;
}

