package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Payload.Request.LoginRequest;
import com.qldapm_L01.backend_api.Payload.Request.RegisterRequest;
import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String token = authService.register(request);
        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Registration successful");
        response.setData(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Login successful");
        response.setData(token);
        return ResponseEntity.ok(response);
    }
}
