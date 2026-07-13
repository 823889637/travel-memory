package com.travelmemory.config;

import com.travelmemory.service.AppUserService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class InitialAdminBootstrapRunner implements CommandLineRunner {
    private final AppUserService users;
    public InitialAdminBootstrapRunner(AppUserService users) { this.users = users; }
    @Override public void run(String... args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String username = input.readLine();
        String password = input.readLine();
        users.bootstrapInitialAdmin(username, password);
        System.out.println("Initial administrator activated.");
        System.exit(0);
    }
}
