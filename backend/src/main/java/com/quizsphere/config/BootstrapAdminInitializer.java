package com.quizsphere.config;

import com.quizsphere.entity.Role;
import com.quizsphere.repository.UserRepository;
import com.quizsphere.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD when no admin
 * exists. Nothing is created when those variables are unset: there are no built-in credentials.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AppProperties props;
    private final UserRepository userRepository;
    private final AuthService authService;

    public BootstrapAdminInitializer(AppProperties props, UserRepository userRepository, AuthService authService) {
        this.props = props;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin cfg = props.bootstrapAdmin();
        if (cfg == null || cfg.email() == null || cfg.email().isBlank()
                || cfg.password() == null || cfg.password().isBlank()) {
            return;
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (cfg.password().length() < 8) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD must be at least 8 characters; bootstrap admin not created");
            return;
        }
        authService.createAdmin(cfg.name() == null || cfg.name().isBlank() ? "Administrator" : cfg.name(),
                cfg.email().trim(), cfg.password());
        log.info("Bootstrap admin account created for {}", cfg.email().trim());
    }
}
