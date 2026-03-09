package com.qldapm_L01.backend_api.Exception;

import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class CentralException {

    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<?> handleDataNotFoundException(DataNotFoundException e) {
        BaseResponse response = new BaseResponse();
        response.setStatusCode(404);
        response.setMessage(e.getMessage());
        response.setData(null);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException e) {
        BaseResponse response = new BaseResponse();
        response.setStatusCode(401);
        response.setMessage("Invalid username or password");
        response.setData(null);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        BaseResponse response = new BaseResponse();
        response.setStatusCode(400);
        response.setMessage(e.getMessage());
        response.setData(null);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        BaseResponse response = new BaseResponse();
        response.setStatusCode(400);
        response.setMessage(e.getMessage());
        response.setData(null);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        BaseResponse response = new BaseResponse();
        response.setStatusCode(400);
        response.setMessage(message);
        response.setData(null);
        return ResponseEntity.ok(response);
    }
}
