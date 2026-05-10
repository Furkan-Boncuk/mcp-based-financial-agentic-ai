package com.financialagent.conversation.common.web;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthenticatedUserIdArgumentResolver implements HandlerMethodArgumentResolver {

  public static final String AUTH_USER_ID_HEADER = "X-Auth-UserId";

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(AuthenticatedUserId.class)
        && UUID.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    String userId = webRequest.getHeader(AUTH_USER_ID_HEADER);
    if (userId == null || userId.isBlank()) {
      throw new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    try {
      return UUID.fromString(userId);
    } catch (IllegalArgumentException exception) {
      throw new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
  }
}
