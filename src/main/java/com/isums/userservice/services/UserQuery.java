package com.isums.userservice.services;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserQuery {
    private final UserRepository userRepository;

    @Cacheable("allUsers")
    public List<User> getAllUsersCached() {
        return userRepository.findAll();
    }
}
