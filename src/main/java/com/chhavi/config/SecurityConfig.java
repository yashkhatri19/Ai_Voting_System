package com.chhavi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.chhavi.security.CustomUserDetailsService;
import com.chhavi.security.JwtAuthenticationFilter;
import com.chhavi.security.JwtUtils;

import jakarta.servlet.http.Cookie;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtUtils jwtUtils;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtUtils jwtUtils) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtUtils = jwtUtils;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/health",
                        "/register",
                        "/login",
                        "/verify-email-otp",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/resend-verification-otp",
                        "/forgot-password",
                        "/verify-otp",
                        "/reset-password",
                        "/about",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/voter/**").hasRole("VOTER")
                .requestMatchers("/ai/**").hasRole("VOTER")
                    .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    String email = authentication.getName();
                    String role = authentication.getAuthorities().iterator().next().getAuthority();
                    
                    // Generate JWT Access & Refresh tokens
                    String accessToken = jwtUtils.generateAccessToken(email, role);
                    String refreshToken = jwtUtils.generateRefreshToken(email);
                    
                    // Access Token Cookie (1 hour)
                    Cookie accessCookie = new Cookie("accessToken", accessToken);
                    accessCookie.setHttpOnly(true);
                    accessCookie.setSecure(request.isSecure());
                    accessCookie.setPath("/");
                    accessCookie.setMaxAge(60 * 60);
                    response.addCookie(accessCookie);
                    
                    // Refresh Token Cookie (7 days)
                    Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
                    refreshCookie.setHttpOnly(true);
                    refreshCookie.setSecure(request.isSecure());
                    refreshCookie.setPath("/");
                    refreshCookie.setMaxAge(7 * 24 * 60 * 60);
                    response.addCookie(refreshCookie);
                    
                    response.sendRedirect(request.getContextPath() + "/login-success");
                })
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    // Delete Access Token Cookie
                    Cookie accessCookie = new Cookie("accessToken", null);
                    accessCookie.setHttpOnly(true);
                    accessCookie.setPath("/");
                    accessCookie.setMaxAge(0);
                    response.addCookie(accessCookie);
                    
                    // Delete Refresh Token Cookie
                    Cookie refreshCookie = new Cookie("refreshToken", null);
                    refreshCookie.setHttpOnly(true);
                    refreshCookie.setPath("/");
                    refreshCookie.setMaxAge(0);
                    response.addCookie(refreshCookie);
                    
                    response.sendRedirect(request.getContextPath() + "/login?logout=true");
                })
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .headers(headers -> headers
                // Disable client side caching to prevent back button security bypass
                .cacheControl(cache -> cache.disable())
            );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}