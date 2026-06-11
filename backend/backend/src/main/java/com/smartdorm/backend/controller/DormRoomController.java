package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.service.DormRoomService;
import com.smartdorm.backend.vo.DormRoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dorm-room")
@RequiredArgsConstructor
@CrossOrigin
public class DormRoomController {

    private final DormRoomService dormRoomService;

    /** 管理员：查询所有房间 */
    @GetMapping
    public Result<List<DormRoomVO>> listRooms() {
        return Result.success(dormRoomService.listRooms());
    }

    /** 宿管：查询自己负责的房间 */
    @GetMapping("/my-rooms")
    public Result<List<DormRoomVO>> listMyRooms(@RequestParam Long managerId) {
        return Result.success(dormRoomService.listRoomsByManager(managerId));
    }

}
