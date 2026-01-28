package com.isums.userservice.domains.mapper;

import com.isums.userservice.domains.dtos.UserDto;
import com.isums.userservice.domains.entities.User;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto mapUser(User user);
    List<UserDto> mapUsers(List<User> users);
}
