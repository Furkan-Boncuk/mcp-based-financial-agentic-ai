package com.financialagent.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.GlobalExceptionHandler;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.domain.AuthTokens;
import com.financialagent.auth.domain.RefreshResult;
import com.financialagent.auth.dto.AuthResponse;
import com.financialagent.auth.dto.RefreshResponse;
import com.financialagent.auth.dto.UserProfileResponse;
import com.financialagent.auth.service.AuthService;
import com.financialagent.auth.service.JwtTokenService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;
  @Mock private JwtTokenService jwtTokenService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AuthController(authService, jwtTokenService, Duration.ofDays(7)))
            .setValidator(validator)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void registerReturnsAccessTokenAndSetsRefreshCookie() throws Exception {
    UUID userId = UUID.randomUUID();
    when(authService.register(any(), any()))
        .thenReturn(new AuthTokens(new AuthResponse("access-token", 900, userId), "refresh-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "correlation-id")
                .content(
                    """
                    {
                      "email": "user@example.com",
                      "password": "Strong1!",
                      "name": "User"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")));
  }

  @Test
  void registerWeakPasswordIsRejectedBeforeServiceCall() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "user@example.com",
                      "password": "weak",
                      "name": "User"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerDuplicateEmailReturnsConflict() throws Exception {
    when(authService.register(any(), any()))
        .thenThrow(new ServiceException(ErrorCode.AUTH_EMAIL_EXISTS));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "user@example.com",
                      "password": "Strong1!",
                      "name": "User"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("AUTH_EMAIL_EXISTS"));
  }

  @Test
  void loginReturnsAccessTokenAndSetsRefreshCookie() throws Exception {
    UUID userId = UUID.randomUUID();
    when(authService.login(any(), any()))
        .thenReturn(new AuthTokens(new AuthResponse("access-token", 900, userId), "refresh-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "user@example.com",
                      "password": "Strong1!"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")));
  }

  @Test
  void loginInvalidCredentialsReturnUnauthorized() throws Exception {
    when(authService.login(any(), any()))
        .thenThrow(new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "user@example.com",
                      "password": "Wrong1!"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void refreshReadsTokenFromCookieAndReturnsNewAccessTokenOnly() throws Exception {
    when(authService.refresh(eq("old-refresh-token"), any()))
        .thenReturn(
            new RefreshResult(new RefreshResponse("new-access-token", 900), "new-refresh-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "old-refresh-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            header()
                .string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-refresh-token")));
  }

  @Test
  void logoutUsesBearerUserAndClearsRefreshCookie() throws Exception {
    UUID userId = UUID.randomUUID();
    when(jwtTokenService.requireUserId("Bearer access-token")).thenReturn(userId);

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .cookie(new Cookie("refreshToken", "refresh-token")))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refreshToken", 0))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    verify(authService).logout(eq(userId), eq("refresh-token"), any());
  }

  @Test
  void meReturnsProfileWithoutMessageCount() throws Exception {
    UUID userId = UUID.randomUUID();
    when(jwtTokenService.requireUserId("Bearer access-token")).thenReturn(userId);
    when(authService.me(userId))
        .thenReturn(
            new UserProfileResponse(
                userId, "user@example.com", "User", "free", Instant.parse("2026-05-09T10:00:00Z")));

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.tier").value("free"))
        .andExpect(jsonPath("$.messageCount").doesNotExist());
  }

  @Test
  void meRequiresAuthorizationHeader() throws Exception {
    when(jwtTokenService.requireUserId(null))
        .thenThrow(new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void jwksReturnsPublicKeysOnly() throws Exception {
    when(jwtTokenService.publicJwks())
        .thenReturn(Map.of("keys", List.of(Map.of("kid", "test-key", "kty", "RSA"))));

    mockMvc
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kid").value("test-key"))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("refreshToken"))));
  }
}
