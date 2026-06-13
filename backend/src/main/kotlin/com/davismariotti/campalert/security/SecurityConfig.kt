package com.davismariotti.campalert.security

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
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
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
    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider()
        provider.setUserDetailsService(userDetailsService)
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
    fun rememberMeServices(): PersistentTokenBasedRememberMeServices {
        val services =
            PersistentTokenBasedRememberMeServices(rememberMeKey, userDetailsService, persistentTokenRepository())
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
                    .ignoringRequestMatchers("/api/sms/webhook")
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/sms/webhook").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                    .anyRequest().authenticated()
            }
            .rememberMe { rm -> rm.rememberMeServices(rememberMeServices()) }
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
