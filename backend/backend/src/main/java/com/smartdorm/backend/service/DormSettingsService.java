package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.entity.DormBuilding;
import com.smartdorm.backend.entity.DormFloor;
import com.smartdorm.backend.entity.DormRoom;
import com.smartdorm.backend.mapper.DormBuildingMapper;
import com.smartdorm.backend.mapper.DormFloorMapper;
import com.smartdorm.backend.mapper.DormRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 宿舍有效性维护：级联无效/子级有效则上级有效；按层号、房间号范围批量设置。
 */
@Service
@RequiredArgsConstructor
public class DormSettingsService {

    private final DormBuildingMapper buildingMapper;
    private final DormFloorMapper floorMapper;
    private final DormRoomMapper roomMapper;

    @Transactional
    public void setBuildingValid(Long buildingId, boolean valid) {
        DormBuilding b = buildingMapper.selectById(buildingId);
        if (b == null) throw new BusinessException("楼房不存在");
        b.setValid(valid);
        buildingMapper.updateById(b);
        if (!valid) {
            invalidateFloorsAndRoomsUnderBuilding(buildingId);
        }
    }

    @Transactional
    public void setFloorValid(Long floorId, boolean valid) {
        DormFloor f = floorMapper.selectById(floorId);
        if (f == null) throw new BusinessException("楼层不存在");
        f.setValid(valid);
        floorMapper.updateById(f);
        if (!valid) {
            invalidateRoomsUnderFloor(floorId);
        } else {
            ensureBuildingValid(f.getBuildingId());
        }
    }

    @Transactional
    public void setRoomValid(Long roomId, boolean valid) {
        DormRoom r = roomMapper.selectById(roomId);
        if (r == null) throw new BusinessException("房间不存在");
        r.setValid(valid);
        roomMapper.updateById(r);
        if (valid) {
            ensureParentsForRoom(r);
        }
    }

    @Transactional
    public void batchFloorValidRange(Long buildingId, int fromFloor, int toFloor, boolean valid) {
        if (fromFloor > toFloor) throw new BusinessException("起始层不能大于结束层");
        List<DormFloor> floors = floorMapper.selectList(new LambdaQueryWrapper<DormFloor>()
                .eq(DormFloor::getBuildingId, buildingId)
                .between(DormFloor::getFloorNo, fromFloor, toFloor));
        for (DormFloor f : floors) {
            setFloorValidInternal(f.getId(), valid);
        }
    }

    @Transactional
    public void batchRoomValidRange(Long floorId, int fromRoom, int toRoom, boolean valid) {
        if (fromRoom > toRoom) throw new BusinessException("起始房间号不能大于结束房间号");
        List<DormRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getFloorId, floorId)
                .between(DormRoom::getRoomNo, fromRoom, toRoom));
        for (DormRoom r : rooms) {
            setRoomValidInternal(r.getId(), valid);
        }
    }

    public List<DormBuilding> searchBuildings(String nameLike) {
        var q = new LambdaQueryWrapper<DormBuilding>().orderByAsc(DormBuilding::getId);
        if (StringUtils.hasText(nameLike)) {
            q.like(DormBuilding::getName, nameLike.trim());
        }
        return buildingMapper.selectList(q);
    }

    public List<DormFloor> searchFloors(Long buildingId, Integer floorNoFrom, Integer floorNoTo) {
        var q = new LambdaQueryWrapper<DormFloor>().eq(DormFloor::getBuildingId, buildingId)
                .orderByAsc(DormFloor::getFloorNo);
        if (floorNoFrom != null) {
            q.ge(DormFloor::getFloorNo, floorNoFrom);
        }
        if (floorNoTo != null) {
            q.le(DormFloor::getFloorNo, floorNoTo);
        }
        return floorMapper.selectList(q);
    }

    public List<DormRoom> searchRooms(Long floorId, Integer roomNoFrom, Integer roomNoTo) {
        var q = new LambdaQueryWrapper<DormRoom>().eq(DormRoom::getFloorId, floorId).orderByAsc(DormRoom::getRoomNo);
        if (roomNoFrom != null) {
            q.ge(DormRoom::getRoomNo, roomNoFrom);
        }
        if (roomNoTo != null) {
            q.le(DormRoom::getRoomNo, roomNoTo);
        }
        return roomMapper.selectList(q);
    }

    @Transactional
    public void updateBuildingDisplayName(Long buildingId, String newName) {
        if (!StringUtils.hasText(newName)) throw new BusinessException("名称不能为空");
        DormBuilding b = buildingMapper.selectById(buildingId);
        if (b == null) throw new BusinessException("楼房不存在");
        b.setName(newName.trim());
        buildingMapper.updateById(b);
        List<DormFloor> floors = floorMapper.selectList(new LambdaQueryWrapper<DormFloor>()
                .eq(DormFloor::getBuildingId, buildingId));
        for (DormFloor f : floors) {
            List<DormRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getFloorId, f.getId()));
            for (DormRoom r : rooms) {
                r.setBuilding(newName.trim());
                roomMapper.updateById(r);
            }
        }
    }

    private void setFloorValidInternal(Long floorId, boolean valid) {
        DormFloor f = floorMapper.selectById(floorId);
        if (f == null) return;
        f.setValid(valid);
        floorMapper.updateById(f);
        if (!valid) {
            invalidateRoomsUnderFloor(floorId);
        } else {
            ensureBuildingValid(f.getBuildingId());
        }
    }

    private void setRoomValidInternal(Long roomId, boolean valid) {
        DormRoom r = roomMapper.selectById(roomId);
        if (r == null) return;
        r.setValid(valid);
        roomMapper.updateById(r);
        if (valid) {
            ensureParentsForRoom(r);
        }
    }

    private void ensureBuildingValid(Long buildingId) {
        DormBuilding b = buildingMapper.selectById(buildingId);
        if (b != null && !Boolean.TRUE.equals(b.getValid())) {
            b.setValid(true);
            buildingMapper.updateById(b);
        }
    }

    private void ensureParentsForRoom(DormRoom r) {
        DormFloor f = floorMapper.selectById(r.getFloorId());
        if (f == null) return;
        if (!Boolean.TRUE.equals(f.getValid())) {
            f.setValid(true);
            floorMapper.updateById(f);
        }
        ensureBuildingValid(f.getBuildingId());
    }

    private void invalidateFloorsAndRoomsUnderBuilding(Long buildingId) {
        List<DormFloor> floors = floorMapper.selectList(new LambdaQueryWrapper<DormFloor>()
                .eq(DormFloor::getBuildingId, buildingId));
        for (DormFloor f : floors) {
            f.setValid(false);
            floorMapper.updateById(f);
            invalidateRoomsUnderFloor(f.getId());
        }
    }

    private void invalidateRoomsUnderFloor(Long floorId) {
        List<DormRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getFloorId, floorId));
        for (DormRoom r : rooms) {
            r.setValid(false);
            roomMapper.updateById(r);
        }
    }

}
