package pl.radarpracy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RadarPracyApplication {

    public static void main(String[] args) {
        SpringApplication.run(RadarPracyApplication.class, args);
    }
}
