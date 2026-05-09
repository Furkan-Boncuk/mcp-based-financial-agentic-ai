package com.financialagent.gateway.security;

public final class GatewayAuthHeaders {

  public static final String USER_ID = "X-Auth-UserId";
  public static final String EMAIL = "X-Auth-Email";
  public static final String ROLES = "X-Auth-Roles";
  public static final String JTI = "X-Auth-Jti";

  private GatewayAuthHeaders() {}
}
