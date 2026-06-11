package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.dto.LoginRequest;
import com.smartdorm.backend.entity.SysUserStudent;
import com.smartdorm.backend.entity.User;
import com.smartdorm.backend.mapper.SysUserStudentMapper;
import com.smartdorm.backend.mapper.UserMapper;
import com.smartdorm.backend.util.JwtTokenUtil;
import com.smartdorm.backend.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final SysUserStudentMapper sysUserStudentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
                        .last("limit 1")
        );

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        UserType userType = UserType.fromCode(user.getUserType());
        boolean dormLeader = false;
        if (userType == UserType.STUDENT) {
            SysUserStudent stu = sysUserStudentMapper.selectById(user.getId());
            dormLeader = stu != null && Boolean.TRUE.equals(stu.getLeader());
        }

        String token = jwtTokenUtil.generateToken(user, dormLeader);

        String gender = user.getGender() == null ? "" : user.getGender();
        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getUserType(),
                dormLeader,
                gender,
                userType.getPortal()
        );
    }
}
