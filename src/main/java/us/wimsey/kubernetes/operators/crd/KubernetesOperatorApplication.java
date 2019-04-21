package us.wimsey.kubernetes.operators.crd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

//@SpringBootApplication(scanBasePackages = {"us.wimsey.kubernetes.operators.crd"})
@SpringBootApplication()
public class KubernetesOperatorApplication {
	private static final Logger LOG = LoggerFactory.getLogger(KubernetesOperatorApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(KubernetesOperatorApplication.class, args);
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}
}
