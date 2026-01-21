package com.isums.userservice.exceptions;

import com.isums.userservice.domains.dtos.ApiError;
import com.isums.userservice.domains.dtos.ApiResponse;
import com.isums.userservice.domains.dtos.ApiResponses;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDb(DataAccessException ex) {
        ex.getMostSpecificCause();
        String detail = ex.getMostSpecificCause().getMessage();

        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Database error",
                List.of(ApiError.builder()
                        .code("DB_ERROR")
                        .message(detail)
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                List.of(ApiError.builder()
                        .code("BAD_REQUEST")
                        .message(ex.getMessage())
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                List.of(ApiError.builder()
                        .code("INTERNAL_ERROR")
                        .message(ex.getMessage())
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
