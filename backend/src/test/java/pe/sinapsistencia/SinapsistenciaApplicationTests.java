package pe.sinapsistencia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
class SinapsistenciaApplicationTests {

	@Test
	void contextLoads() {
	}

}
