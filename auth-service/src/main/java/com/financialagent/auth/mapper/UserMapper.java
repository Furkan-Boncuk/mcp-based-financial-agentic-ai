package com.financialagent.auth.mapper;

import com.financialagent.auth.domain.GeneratedAccessToken;
import com.financialagent.auth.domain.User;
import com.financialagent.auth.dto.AuthResponse;
import com.financialagent.auth.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public AuthResponse toAuthResponse(GeneratedAccessToken accessToken, User user) {
    return new AuthResponse(accessToken.token(), accessToken.expiresIn(), user.id());
  }

  public UserProfileResponse toProfileResponse(User user) {
    return new UserProfileResponse(
        user.id(), user.email(), user.name(), user.tier(), user.createdAt());
  }
}
