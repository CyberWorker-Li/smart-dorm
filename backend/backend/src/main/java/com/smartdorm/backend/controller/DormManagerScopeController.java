package com.smartdorm.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.ManagerScopeRequest;
import com.smartdorm.backend.entity.DormManagerScope;
import com.smartdorm.backend.mapper.DormManagerScopeMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dorm/manager-scope")
@RequiredArgsConstructor
@CrossOrigin
public class DormManagerScopeController {

    private final DormManagerScopeMapper dormManagerScopeMapper;

    @GetMapping
    public Result<List<DormManagerScope>> list(@RequestParam Long managerId) {
        return Result.success(dormManagerScopeMapper.selectList(
                new LambdaQueryWrapper<DormManagerScope>().eq(DormManagerScope::getManagerId, managerId)));
    }

    @PostMapping
    public Result<DormManagerScope> add(@Valid @RequestBody ManagerScopeRequest body) {
        DormManagerScope s = new DormManagerScope();
        s.setManagerId(body.getManagerId());
        s.setBuildingId(body.getBuildingId());
        s.setFloorId(body.getFloorId());
        dormManagerScopeMapper.insert(s);
        return Result.success(s);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dormManagerScopeMapper.deleteById(id);
        return Result.success();
    }
}
