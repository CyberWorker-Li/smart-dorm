package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.GenerateBuildingRequest;
import com.smartdorm.backend.dto.InsertFloorRequest;
import com.smartdorm.backend.dto.InsertRoomRequest;
import com.smartdorm.backend.entity.DormBuilding;
import com.smartdorm.backend.entity.DormFloor;
import com.smartdorm.backend.entity.DormRoom;
import com.smartdorm.backend.service.DormStructureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dorm/structure")
@RequiredArgsConstructor
@CrossOrigin
public class DormStructureController {

    private final DormStructureService dormStructureService;

    @PostMapping("/buildings/generate")
    public Result<DormBuilding> generateBuilding(@Valid @RequestBody GenerateBuildingRequest body) {
        return Result.success(dormStructureService.generateBuilding(
                body.getName(),
                body.getGender(),
                body.getTotalFloors(),
                body.getRoomsPerFloor()));
    }

    @GetMapping("/buildings")
    public Result<List<DormBuilding>> listBuildings(@RequestParam(required = false) String name) {
        return Result.success(dormStructureService.listBuildings(name));
    }

    @DeleteMapping("/buildings/{id}")
    public Result<Void> deleteBuilding(@PathVariable Long id) {
        dormStructureService.deleteBuilding(id);
        return Result.success();
    }

    @PostMapping("/floors")
    public Result<DormFloor> insertFloor(@Valid @RequestBody InsertFloorRequest body) {
        boolean mv = body.getMainValid() == null || Boolean.TRUE.equals(body.getMainValid());
        return Result.success(dormStructureService.insertFloor(
                body.getBuildingId(), body.getFloorNo(), body.getMaxRooms(), mv));
    }

    @DeleteMapping("/floors")
    public Result<Void> deleteFloorsFrom(@RequestParam Long buildingId, @RequestParam int fromFloorNo) {
        dormStructureService.deleteFloorsFrom(buildingId, fromFloorNo);
        return Result.success();
    }

    @GetMapping("/floors")
    public Result<List<DormFloor>> listFloors(@RequestParam Long buildingId,
                                              @RequestParam(required = false) Integer floorNo) {
        return Result.success(dormStructureService.listFloors(buildingId, floorNo));
    }

    @PostMapping("/rooms")
    public Result<DormRoom> insertRoom(@Valid @RequestBody InsertRoomRequest body) {
        boolean mv = body.getMainValid() == null || Boolean.TRUE.equals(body.getMainValid());
        return Result.success(dormStructureService.insertRoom(
                body.getFloorId(),
                body.getRoomNo(),
                body.getCapacity(),
                body.getBuilding(),
                mv));
    }

    @DeleteMapping("/rooms")
    public Result<Void> deleteRoomsFrom(@RequestParam Long floorId, @RequestParam int fromRoomNo) {
        dormStructureService.deleteRoomsFrom(floorId, fromRoomNo);
        return Result.success();
    }

    @GetMapping("/rooms")
    public Result<List<DormRoom>> listRooms(@RequestParam Long floorId,
                                            @RequestParam(required = false) Integer roomNoFrom,
                                            @RequestParam(required = false) Integer roomNoTo) {
        return Result.success(dormStructureService.listRooms(floorId, roomNoFrom, roomNoTo));
    }
}
