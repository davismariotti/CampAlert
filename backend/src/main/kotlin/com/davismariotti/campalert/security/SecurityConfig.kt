package com.davismariotti.campalert.security

import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import javax.sql.DataSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val userDetailsService: UserDetailsServiceImpl,
    private val dataSource: DataSource,
    @Value("\${campfinder.security.remember-me-key}")
    private val rememberMeKey: String,
) {
    @PostConstruct
    fun validateRememberMeKey() {
        check(rememberMeKey != "change-me-in-production") {
            "campfinder.security.remember-me-key must be changed from the default value before starting"
        }
    }

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    @Bean
    fun persistentTokenRepository(): PersistentTokenRepository {
        val repo = JdbcTokenRepositoryImpl()
        repo.setDataSource(dataSource)
        return repo
    }

    @Bean
    fun rememberMeServices(): RememberMeServices {
        val services = RememberMeServices(rememberMeKey, userDetailsService, persistentTokenRepository())
        services.setTokenValiditySeconds(30 * 24 * 60 * 60)
        services.setAlwaysRemember(false)
        return services
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/sms/webhook", "/api/email/webhook/bounce")
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/register")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/resend-verification")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/verify-email")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/reset-password")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/sms/webhook")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/email/webhook/bounce")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/info")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.rememberMe { rm -> rm.rememberMeServices(rememberMeServices()) }
            .addFilterAfter(CsrfCookieFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.contentType = "application/json"
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.writer.write("""{"message":"Unauthorized"}""")
                }
            }
        return http.build()
    }
}
