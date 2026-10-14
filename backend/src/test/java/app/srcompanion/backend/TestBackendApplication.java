package app.srcompanion.backend;

import org.springframework.boot.SpringApplication;

public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(SrCompanionApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
