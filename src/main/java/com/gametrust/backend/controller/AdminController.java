package com.gametrust.backend.controller;

import com.gametrust.backend.dto.auth.UserResponse;
import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserResponse>> users() {
        return ApiResponse.success(userRepository.findAll().stream().map(UserResponse::fromUser).toList());
    }
}
