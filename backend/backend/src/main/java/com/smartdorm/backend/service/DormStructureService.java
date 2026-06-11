package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.GenderUtil;
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
 * 楼房 / 楼层 / 房间的结构维护：批量生成、级联插入与删除规则。
 */
@Service
@RequiredArgsConstructor
public class DormStructureService {

    private final DormBuildingMapper buildingMapper;
    private final DormFloorMapper floorMapper;
    private final DormRoomMapper roomMapper;

    // ---------- 批量生成整栋楼（全部有效）----------

    @Transactional
    public DormBuilding generateBuilding(String name, String gender,
                                         Integer totalFloors, Integer roomsPerFloor) {
        if (!StringUtils.hasText(name)) throw new BusinessException("楼房名称不能为空");
        GenderUtil.requireCode(gender);
        if (roomsPerFloor != null && roomsPerFloor > 0 && (totalFloors == null || totalFloors <= 0)) {
            throw new BusinessException("不能只填写每层房间数而不填写层数");
        }

        DormBuilding b = new DormBuilding();
        b.setName(name.trim());
        b.setGender(gender.trim().toUpperCase());
        b.setValid(true);

        int floors = totalFloors != null && totalFloors > 0 ? totalFloors : 0;
        int per = roomsPerFloor != null && roomsPerFloor > 0 ? roomsPerFloor : 0;
        if (floors > 0 && per <= 0) {
            per = 10;
        }
        b.setFloorCount(floors);
        buildingMapper.insert(b);

        if (floors <= 0) {
            return b;
        }
        for (int fn = 1; fn <= floors; fn++) {
            DormFloor f = new DormFloor();
            f.setBuildingId(b.getId());
            f.setSeqNo(fn);
            f.setFloorNo(fn);
            f.setMaxRooms(per);
            f.setValid(true);
            floorMapper.insert(f);
            if (per > 0) {
                for (int rn = 1; rn <= per; rn++) {
                    DormRoom r = new DormRoom();
                    r.setFloorId(f.getId());
                    r.setBuilding(b.getName());
                    r.setRoomNo(rn);
                    r.setCapacity(4);
                    r.setValid(true);
                    roomMapper.insert(r);
                }
            }
        }
        return buildingMapper.selectById(b.getId());
    }

    // ---------- 楼房 CRUD ----------

    public List<DormBuilding> listBuildings(String nameLike) {
        var q = new LambdaQueryWrapper<DormBuilding>().orderByAsc(DormBuilding::getId);
        if (StringUtils.hasText(nameLike)) {
            q.like(DormBuilding::getName, nameLike.trim());
        }
        return buildingMapper.selectList(q);
    }

    @Transactional
    public void deleteBuilding(Long id) {
        DormBuilding b = buildingMapper.selectById(id);
        if (b == null) throw new BusinessException("楼房不存在");
        buildingMapper.deleteById(id);
    }

    // ---------- 楼层：插入捎带更低层（低层默认无效）；删除捎带更高层 ----------

    @Transactional
    public DormFloor insertFloor(Long buildingId, int floorNo, int maxRooms, boolean mainValid) {
        DormBuilding b = buildingMapper.selectById(buildingId);
        if (b == null) throw new BusinessException("楼房不存在");
        if (floorNo <= 0) throw new BusinessException("层号须从 1 开始");
        if (floorNo > b.getFloorCount()) {
            b.setFloorCount(floorNo);
            buildingMapper.updateById(b);
        }
        if (maxRooms <= 0) throw new BusinessException("本层最多房间数须大于 0");

        for (int k = 1; k < floorNo; k++) {
            if (floorMapper.selectCount(new LambdaQueryWrapper<DormFloor>()
                    .eq(DormFloor::getBuildingId, buildingId)
                    .eq(DormFloor::getFloorNo, k)) > 0) {
                continue;
            }
            DormFloor low = new DormFloor();
            low.setBuildingId(buildingId);
            low.setSeqNo(k);
            low.setFloorNo(k);
            low.setMaxRooms(maxRooms);
            low.setValid(false);
            floorMapper.insert(low);
        }

        Long exists = floorMapper.selectCount(new LambdaQueryWrapper<DormFloor>()
                .eq(DormFloor::getBuildingId, buildingId)
                .eq(DormFloor::getFloorNo, floorNo));
        if (exists != null && exists > 0) {
            throw new BusinessException("该层已存在");
        }
        DormFloor f = new DormFloor();
        f.setBuildingId(buildingId);
        f.setSeqNo(floorNo);
        f.setFloorNo(floorNo);
        f.setMaxRooms(maxRooms);
        f.setValid(mainValid);
        floorMapper.insert(f);
        return f;
    }

    @Transactional
    public void deleteFloorsFrom(Long buildingId, int floorNoFrom) {
        List<DormFloor> list = floorMapper.selectList(new LambdaQueryWrapper<DormFloor>()
                .eq(DormFloor::getBuildingId, buildingId)
                .ge(DormFloor::getFloorNo, floorNoFrom)
                .orderByDesc(DormFloor::getFloorNo));
        for (DormFloor f : list) {
            floorMapper.deleteById(f.getId());
        }
    }

    public List<DormFloor> listFloors(Long buildingId, Integer floorNo) {
        var q = new LambdaQueryWrapper<DormFloor>().eq(DormFloor::getBuildingId, buildingId)
                .orderByAsc(DormFloor::getFloorNo);
        if (floorNo != null) {
            q.eq(DormFloor::getFloorNo, floorNo);
        }
        return floorMapper.selectList(q);
    }

    // ---------- 房间：插入捎带更小房号（默认无效）；删除捎带更大房号 ----------

    @Transactional
    public DormRoom insertRoom(Long floorId, int roomNo, int capacity, String buildingDisplay, boolean mainValid) {
        DormFloor f = floorMapper.selectById(floorId);
        if (f == null) throw new BusinessException("楼层不存在");
        if (roomNo <= 0) throw new BusinessException("房间序号须从 1 开始");
        if (roomNo > f.getMaxRooms()) {
            throw new BusinessException("房间序号不能超过本层最大房间数 " + f.getMaxRooms());
        }
        DormBuilding b = buildingMapper.selectById(f.getBuildingId());
        String display = StringUtils.hasText(buildingDisplay) ? buildingDisplay.trim() : b.getName();

        for (int k = 1; k < roomNo; k++) {
            if (roomMapper.selectCount(new LambdaQueryWrapper<DormRoom>()
                    .eq(DormRoom::getFloorId, floorId)
                    .eq(DormRoom::getRoomNo, k)) > 0) {
                continue;
            }
            DormRoom low = new DormRoom();
            low.setFloorId(floorId);
            low.setBuilding(display);
            low.setRoomNo(k);
            low.setCapacity(capacity);
            low.setValid(false);
            roomMapper.insert(low);
        }

        Long exists = roomMapper.selectCount(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getFloorId, floorId)
                .eq(DormRoom::getRoomNo, roomNo));
        if (exists != null && exists > 0) {
            throw new BusinessException("该房间序号已存在");
        }
        DormRoom r = new DormRoom();
        r.setFloorId(floorId);
        r.setBuilding(display);
        r.setRoomNo(roomNo);
        r.setCapacity(capacity);
        r.setValid(mainValid);
        roomMapper.insert(r);
        return r;
    }

    @Transactional
    public void deleteRoomsFrom(Long floorId, int roomNoFrom) {
        List<DormRoom> list = roomMapper.selectList(new LambdaQueryWrapper<DormRoom>()
                .eq(DormRoom::getFloorId, floorId)
                .ge(DormRoom::getRoomNo, roomNoFrom)
                .orderByDesc(DormRoom::getRoomNo));
        for (DormRoom r : list) {
            roomMapper.deleteById(r.getId());
        }
    }

    public List<DormRoom> listRooms(Long floorId, Integer roomNoFrom, Integer roomNoTo) {
        var q = new LambdaQueryWrapper<DormRoom>().eq(DormRoom::getFloorId, floorId).orderByAsc(DormRoom::getRoomNo);
        if (roomNoFrom != null) {
            q.ge(DormRoom::getRoomNo, roomNoFrom);
        }
        if (roomNoTo != null) {
            q.le(DormRoom::getRoomNo, roomNoTo);
        }
        return roomMapper.selectList(q);
    }
}
