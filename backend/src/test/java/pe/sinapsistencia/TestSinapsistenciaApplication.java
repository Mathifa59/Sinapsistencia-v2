package pe.sinapsistencia;

import org.springframework.boot.SpringApplication;

public class TestSinapsistenciaApplication {

	public static void main(String[] args) {
		SpringApplication.from(SinapsistenciaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
