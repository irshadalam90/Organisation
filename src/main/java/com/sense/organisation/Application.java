package com.sense.organisation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@SpringBootApplication
@EnableNeo4jRepositories("com.sense.sensemodel.repository")
@EntityScan(basePackages = ("com.sense"))
public class Application {
	public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
