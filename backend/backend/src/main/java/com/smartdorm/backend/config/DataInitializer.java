package com.smartdorm.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.entity.SysUserAdmin;
import com.smartdorm.backend.entity.User;
import com.smartdorm.backend.mapper.SysUserAdminMapper;
import com.smartdorm.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final SysUserAdminMapper sysUserAdminMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.init-admin-username:admin}")
    private String initAdminUsername;

    @Value("${app.auth.init-admin-password:admin123456}")
    private String initAdminPassword;

    @Value("${app.auth.init-admin-name:系统管理员}")
    private String initAdminName;

    @Override
    public void run(String... args) {
        Long adminRows = sysUserAdminMapper.selectCount(null);
        if (adminRows != null && adminRows > 0) {
            return;
        }

        for (User u : userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getUserType, UserType.SYS_ADMIN.name()))) {
            if (sysUserAdminMapper.selectById(u.getId()) == null) {
                SysUserAdmin profile = new SysUserAdmin();
                profile.setUserId(u.getId());
                sysUserAdminMapper.insert(profile);
            }
        }
        Long afterBackfill = sysUserAdminMapper.selectCount(null);
        if (afterBackfill != null && afterBackfill > 0) {
            return;
        }

        User user = new User();
        user.setUsername(initAdminUsername);
        user.setPasswordHash(passwordEncoder.encode(initAdminPassword));
        user.setRealName(initAdminName);
        user.setUserNo("SYS0001");
        user.setUserType(UserType.SYS_ADMIN.name());
        user.setGender("MALE");
        user.setEnabled(true);
        userMapper.insert(user);

        SysUserAdmin profile = new SysUserAdmin();
        profile.setUserId(user.getId());
        sysUserAdminMapper.insert(profile);
    }
}
