package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.domains.mapper.UserMapper;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserQuery {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "allUsers", sync = true)
    public List<UserDto> getAllUsersCached() {
        List<User> users = userRepository.findAll();
        return userMapper.mapUsers(users);
    }

    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

}
