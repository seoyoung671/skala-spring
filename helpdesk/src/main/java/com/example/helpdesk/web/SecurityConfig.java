package com.example.helpdesk.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** 로컬 실습에서 Swagger로 API를 호출하기 위한 테스트용 인증 설정이다. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Swagger에서 POST API를 호출할 수 있도록 API 경로만 CSRF 검사에서 제외한다.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        // 정적 화면과 API 문서만 익명 접근을 허용한다. 실제 채팅·관리 API는
                        // 아래 anyRequest().authenticated()에 의해 Basic 인증이 필요하다.
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> { })
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // 실습 편의를 위한 메모리 계정이다. 운영 환경에서는 {noop} 비밀번호 대신
        // PasswordEncoder와 DB/외부 인증 서버를 사용해야 한다.
        return new InMemoryUserDetailsManager(
                User.withUsername("user1")
                        .password("{noop}1234")
                        .roles("USER", "ADMIN")
                        .build(),
                User.withUsername("user2")
                        .password("{noop}password2")
                        .roles("USER")
                        .build(),
                User.withUsername("admin")
                        .password("{noop}admin123")
                        .roles("ADMIN")
                        .build());
    }
}
