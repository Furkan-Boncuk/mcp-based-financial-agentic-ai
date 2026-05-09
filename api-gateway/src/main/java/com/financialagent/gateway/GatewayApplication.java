package com.financialagent.gateway;

import com.financialagent.gateway.config.GatewayCorsProperties;
import com.financialagent.gateway.config.GatewayJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GatewayJwtProperties.class, GatewayCorsProperties.class})
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
