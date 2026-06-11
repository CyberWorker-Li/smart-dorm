package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.service.DormPublicService;
import com.smartdorm.backend.vo.DormPublicRoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dorm-public")
@RequiredArgsConstructor
@CrossOrigin
public class DormPublicController {

    private final DormPublicService dormPublicService;

    @GetMapping("/rooms")
    public Result<List<DormPublicRoomVO>> rooms(@RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) String gender,
                                                @RequestParam(required = false) Long buildingId,
                                                @RequestParam(required = false) Integer floorNo,
                                                @RequestParam(required = false) Boolean hasVacancy,
                                                @RequestParam(required = false) Boolean hasSwapIntent) {
        return Result.success(dormPublicService.listRooms(batchId, gender, buildingId, floorNo, hasVacancy, hasSwapIntent));
    }
}

