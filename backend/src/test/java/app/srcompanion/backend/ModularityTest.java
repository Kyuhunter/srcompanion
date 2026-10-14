package app.srcompanion.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class ModularityTest {
    ApplicationModules modules = ApplicationModules.of(SrCompanionApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}
