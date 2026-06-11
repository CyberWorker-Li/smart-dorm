package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.entity.DormBuilding;
import com.smartdorm.backend.entity.DormFloor;
import com.smartdorm.backend.entity.DormRoom;
import com.smartdorm.backend.service.DormSettingsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dorm/settings")
@RequiredArgsConstructor
@CrossOrigin
public class DormSettingsController {

    private final DormSettingsService dormSettingsService;

    @PatchMapping("/building/{id}/valid")
    public Result<Void> setBuildingValid(@PathVariable Long id, @RequestParam boolean valid) {
        dormSettingsService.setBuildingValid(id, valid);
        return Result.success();
    }

    @PatchMapping("/floor/{id}/valid")
    public Result<Void> setFloorValid(@PathVariable Long id, @RequestParam boolean valid) {
        dormSettingsService.setFloorValid(id, valid);
        return Result.success();
    }

    @PatchMapping("/room/{id}/valid")
    public Result<Void> setRoomValid(@PathVariable Long id, @RequestParam boolean valid) {
        dormSettingsService.setRoomValid(id, valid);
        return Result.success();
    }

    @PostMapping("/batch/floor-valid")
    public Result<Void> batchFloor(@RequestBody BatchFloorValidBody body) {
        dormSettingsService.batchFloorValidRange(body.getBuildingId(), body.getFromFloor(), body.getToFloor(), body.isValid());
        return Result.success();
    }

    @PostMapping("/batch/room-valid")
    public Result<Void> batchRoom(@RequestBody BatchRoomValidBody body) {
        dormSettingsService.batchRoomValidRange(body.getFloorId(), body.getFromRoom(), body.getToRoom(), body.isValid());
        return Result.success();
    }

    @GetMapping("/search/buildings")
    public Result<List<DormBuilding>> searchBuildings(@RequestParam(required = false) String name) {
        return Result.success(dormSettingsService.searchBuildings(name));
    }

    @GetMapping("/search/floors")
    public Result<List<DormFloor>> searchFloors(@RequestParam Long buildingId,
                                                @RequestParam(required = false) Integer floorNoFrom,
                                                @RequestParam(required = false) Integer floorNoTo) {
        return Result.success(dormSettingsService.searchFloors(buildingId, floorNoFrom, floorNoTo));
    }

    @GetMapping("/search/rooms")
    public Result<List<DormRoom>> searchRooms(@RequestParam Long floorId,
                                              @RequestParam(required = false) Integer roomNoFrom,
                                              @RequestParam(required = false) Integer roomNoTo) {
        return Result.success(dormSettingsService.searchRooms(floorId, roomNoFrom, roomNoTo));
    }

    @PutMapping("/building/{id}/display-name")
    public Result<Void> updateDisplayName(@PathVariable Long id, @RequestParam String name) {
        dormSettingsService.updateBuildingDisplayName(id, name);
        return Result.success();
    }

    @Data
    public static class BatchFloorValidBody {
        private Long buildingId;
        private int fromFloor;
        private int toFloor;
        private boolean valid;
    }

    @Data
    public static class BatchRoomValidBody {
        private Long floorId;
        private int fromRoom;
        private int toRoom;
        private boolean valid;
    }
}
