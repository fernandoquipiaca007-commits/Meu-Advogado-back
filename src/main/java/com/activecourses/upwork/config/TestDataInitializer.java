package com.activecourses.upwork.config;

import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.role.RoleRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            initRoles();
            initVerifiedLawyer();
            initVerifiedClient();
            log.info("[TEST_DATA] Perfis de teste inicializados com sucesso.");
        } catch (Exception e) {
            log.error("[TEST_DATA] Erro ao inicializar perfis de teste: {}", e.getMessage(), e);
        }
    }

    private void initRoles() {
        createRoleIfMissing("ROLE_LAWYER");
        createRoleIfMissing("ROLE_CLIENT");
        createRoleIfMissing("ROLE_ADMIN");
        createRoleIfMissing("ROLE_FREELANCER");
        createRoleIfMissing("ROLE_FIRM");
    }

    private Role createRoleIfMissing(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = Role.builder()
                    .name(name)
                    .createdAt(LocalDateTime.now())
                    .build();
            return roleRepository.save(role);
        });
    }

    private void initVerifiedLawyer() {
        String email = "advogado.teste@legawork.com";
        Role lawyerRole = roleRepository.findByName("ROLE_LAWYER").orElse(null);
        Role freelancerRole = roleRepository.findByName("ROLE_FREELANCER").orElse(null);

        List<Role> roles = new ArrayList<>();
        if (lawyerRole != null) roles.add(lawyerRole);
        if (freelancerRole != null) roles.add(freelancerRole);

        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            user.setAccountEnabled(true);
            user.setAccountLocked(false);
            user.setPassword(passwordEncoder.encode("LWork2026!"));
            user.setRoles(roles);
            if (user.getUserProfile() == null) {
                UserProfile profile = UserProfile.builder()
                        .user(user)
                        .title("Especialista em Direito Empresarial & Contratos")
                        .description("Advogado sênior com mais de 12 anos de experiência em estruturação societária, M&A, contratos comerciais e compliance LGPD.")
                        .oabNumber("412.980")
                        .oabState("SP")
                        .hourlyRate(BigDecimal.valueOf(280.00))
                        .experienceYears(12)
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .location("São Paulo, SP")
                        .phone("(11) 98765-4321")
                        .country("BR")
                        .build();
                user.setUserProfile(profile);
            } else {
                user.getUserProfile().setVerificationStatus(VerificationStatus.VERIFIED);
                user.getUserProfile().setOabNumber("412.980");
                user.getUserProfile().setOabState("SP");
                user.getUserProfile().setTitle("Especialista em Direito Empresarial & Contratos");
                user.getUserProfile().setLocation("São Paulo, SP");
                user.getUserProfile().setPhone("(11) 98765-4321");
                user.getUserProfile().setHourlyRate(BigDecimal.valueOf(280.00));
                user.getUserProfile().setExperienceYears(12);
                user.getUserProfile().setCountry("BR");
            }
            userRepository.save(user);
            log.info("[TEST_DATA] Advogado verificado atualizado: {}", email);
        }, () -> {
            User lawyer = User.builder()
                    .firstName("Rodrigo")
                    .lastName("Silveira")
                    .email(email)
                    .password(passwordEncoder.encode("LWork2026!"))
                    .accountEnabled(true)
                    .accountLocked(false)
                    .roles(roles)
                    .createdAt(LocalDateTime.now())
                    .build();

            UserProfile profile = UserProfile.builder()
                    .user(lawyer)
                    .title("Especialista em Direito Empresarial & Contratos")
                    .description("Advogado sênior com mais de 12 anos de experiência em estruturação societária, M&A, contratos comerciais e compliance LGPD.")
                    .oabNumber("412.980")
                    .oabState("SP")
                    .hourlyRate(BigDecimal.valueOf(280.00))
                    .experienceYears(12)
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .location("São Paulo, SP")
                    .phone("(11) 98765-4321")
                    .country("BR")
                    .build();

            lawyer.setUserProfile(profile);
            userRepository.save(lawyer);
            log.info("[TEST_DATA] Advogado verificado criado: {}", email);
        });
    }

    private void initVerifiedClient() {
        String email = "cliente.teste@legawork.com";
        Role clientRole = roleRepository.findByName("ROLE_CLIENT").orElse(null);

        List<Role> roles = new ArrayList<>();
        if (clientRole != null) roles.add(clientRole);

        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            user.setAccountEnabled(true);
            user.setAccountLocked(false);
            user.setPassword(passwordEncoder.encode("LWork2026!"));
            user.setRoles(roles);
            if (user.getUserProfile() == null) {
                UserProfile profile = UserProfile.builder()
                        .user(user)
                        .title("Diretoria Jurídica")
                        .description("Representante legal da Oliveira Tech Solutions Ltda.")
                        .companyName("Oliveira Tech Solutions Ltda")
                        .clientType("EMPRESARIAL")
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .location("São Paulo, SP")
                        .phone("(11) 91234-5678")
                        .country("BR")
                        .build();
                user.setUserProfile(profile);
            } else {
                user.getUserProfile().setVerificationStatus(VerificationStatus.VERIFIED);
                user.getUserProfile().setTitle("Diretoria Jurídica");
                user.getUserProfile().setCompanyName("Oliveira Tech Solutions Ltda");
                user.getUserProfile().setClientType("EMPRESARIAL");
                user.getUserProfile().setLocation("São Paulo, SP");
                user.getUserProfile().setPhone("(11) 91234-5678");
                user.getUserProfile().setCountry("BR");
            }
            userRepository.save(user);
            log.info("[TEST_DATA] Cliente verificado atualizado: {}", email);
        }, () -> {
            User client = User.builder()
                    .firstName("Mariana")
                    .lastName("Oliveira")
                    .email(email)
                    .password(passwordEncoder.encode("LWork2026!"))
                    .accountEnabled(true)
                    .accountLocked(false)
                    .roles(roles)
                    .createdAt(LocalDateTime.now())
                    .build();

            UserProfile profile = UserProfile.builder()
                    .user(client)
                    .title("Diretoria Jurídica")
                    .description("Representante legal da Oliveira Tech Solutions Ltda.")
                    .companyName("Oliveira Tech Solutions Ltda")
                    .clientType("EMPRESARIAL")
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .location("São Paulo, SP")
                    .phone("(11) 91234-5678")
                    .country("BR")
                    .build();

            client.setUserProfile(profile);
            userRepository.save(client);
            log.info("[TEST_DATA] Cliente verificado criado: {}", email);
        });
    }
}
