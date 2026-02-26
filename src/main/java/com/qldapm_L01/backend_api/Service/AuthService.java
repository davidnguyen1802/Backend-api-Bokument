package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Payload.Request.LoginRequest;
import com.qldapm_L01.backend_api.Payload.Request.RegisterRequest;
import com.qldapm_L01.backend_api.Payload.Response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
