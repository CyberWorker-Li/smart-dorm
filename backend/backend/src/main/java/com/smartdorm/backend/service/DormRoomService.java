package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.entity.DormBuilding;
import com.smartdorm.backend.entity.DormFloor;
import com.smartdorm.backend.entity.DormManagerScope;
import com.smartdorm.backend.entity.DormRoom;
import com.smartdorm.backend.mapper.DormBuildingMapper;
import com.smartdorm.backend.mapper.DormFloorMapper;
import com.smartdorm.backend.mapper.DormManagerScopeMapper;
import com.smartdorm.backend.mapper.DormRoomMapper;
import com.smartdorm.backend.vo.DormRoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DormRoomService {

    private final DormRoomMapper dormRoomMapper;
    private final DormFloorMapper dormFloorMapper;
    private final DormBuildingMapper dormBuildingMapper;
    private final DormManagerScopeMapper dormManagerScopeMapper;

    public record RoomChainInfo(Long roomId,
                                Integer capacity,
                                Boolean roomValid,
                                Boolean floorValid,
                                Boolean buildingValid,
                                String buildingGender,
                                Long buildingId,
                                String buildingName,
                                Integer floorNo,
                                Integer roomNo) {
    }

    /** 管理员：全部房间（含无效） */
    public List<DormRoomVO> listRooms() {
        return dormRoomMapper.selectList(
                        new LambdaQueryWrapper<DormRoom>().orderByAsc(DormRoom::getFloorId, DormRoom::getRoomNo))
                .stream().map(this::toVO).toList();
    }

    /** 分配算法：仅楼房+楼层+房间链全部有效，并带上楼房性别 */
    public List<DormRoom> listAssignableRooms() {
        return listAssignableRooms(null);
    }

    /**
     * @param buildingId 若非空，仅返回该楼下、且楼房/楼层/房间均有效的房间
     */
    public List<DormRoom> listAssignableRooms(Long buildingId) {
        List<DormRoom> rooms = dormRoomMapper.selectList(null);
        List<DormFloor> floors = dormFloorMapper.selectList(null);
        List<DormBuilding> buildings = dormBuildingMapper.selectList(null);
        Map<Long, DormFloor> fmap = floors.stream().collect(Collectors.toMap(DormFloor::getId, f -> f, (a, b) -> a));
        Map<Long, DormBuilding> bmap = buildings.stream().collect(Collectors.toMap(DormBuilding::getId, b -> b, (a, b) -> a));
        List<DormRoom> out = new ArrayList<>();
        for (DormRoom r : rooms) {
            DormFloor f = fmap.get(r.getFloorId());
            if (f == null || !Boolean.TRUE.equals(f.getValid())) {
                continue;
            }
            if (buildingId != null && !buildingId.equals(f.getBuildingId())) {
                continue;
            }
            DormBuilding b = bmap.get(f.getBuildingId());
            if (b == null || !Boolean.TRUE.equals(b.getValid())) {
                continue;
            }
            if (!Boolean.TRUE.equals(r.getValid())) {
                continue;
            }
            r.setBuildingGender(b.getGender());
            out.add(r);
        }
        return out;
    }

    /** 房间所在楼房 ID；无效链时返回 null */
    public Long getBuildingIdForRoom(Long roomId) {
        DormRoom r = dormRoomMapper.selectById(roomId);
        if (r == null) return null;
        DormFloor f = dormFloorMapper.selectById(r.getFloorId());
        return f != null ? f.getBuildingId() : null;
    }

    public DormBuilding getBuildingOrThrow(Long buildingId) {
        DormBuilding b = dormBuildingMapper.selectById(buildingId);
        if (b == null) throw new BusinessException("楼房不存在");
        return b;
    }

    public String resolveBuildingGender(Long roomId) {
        DormRoom r = dormRoomMapper.selectById(roomId);
        if (r == null) return null;
        DormFloor f = dormFloorMapper.selectById(r.getFloorId());
        if (f == null) return null;
        DormBuilding b = dormBuildingMapper.selectById(f.getBuildingId());
        return b != null ? b.getGender() : null;
    }

    /** 宿管：按管辖范围（整楼或某层）列出房间 */
    public List<DormRoomVO> listRoomsByManager(Long managerId) {
        List<DormManagerScope> scopes = dormManagerScopeMapper.selectList(
                new LambdaQueryWrapper<DormManagerScope>().eq(DormManagerScope::getManagerId, managerId));
        if (scopes.isEmpty()) {
            return List.of();
        }
        Set<Long> floorIds = new HashSet<>();
        for (DormManagerScope s : scopes) {
            if (s.getFloorId() != null) {
                floorIds.add(s.getFloorId());
            }
            if (s.getBuildingId() != null) {
                dormFloorMapper.selectList(new LambdaQueryWrapper<DormFloor>()
                                .eq(DormFloor::getBuildingId, s.getBuildingId()))
                        .forEach(f -> floorIds.add(f.getId()));
            }
        }
        if (floorIds.isEmpty()) {
            return List.of();
        }
        return dormRoomMapper.selectList(new LambdaQueryWrapper<DormRoom>()
                        .in(DormRoom::getFloorId, floorIds)
                        .orderByAsc(DormRoom::getFloorId, DormRoom::getRoomNo))
                .stream().map(this::toVO).toList();
    }

    public DormRoom getOrThrow(Long id) {
        DormRoom room = dormRoomMapper.selectById(id);
        if (room == null) throw new BusinessException("房间不存在");
        return room;
    }

    public Map<Long, RoomChainInfo> getRoomChainInfo(Set<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Map.of();
        }
        List<DormRoom> rooms = dormRoomMapper.selectList(new LambdaQueryWrapper<DormRoom>().in(DormRoom::getId, roomIds));
        Set<Long> floorIds = rooms.stream().map(DormRoom::getFloorId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<DormFloor> floors = floorIds.isEmpty()
                ? List.of()
                : dormFloorMapper.selectList(new LambdaQueryWrapper<DormFloor>().in(DormFloor::getId, floorIds));
        Set<Long> buildingIds = floors.stream().map(DormFloor::getBuildingId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<DormBuilding> buildings = buildingIds.isEmpty()
                ? List.of()
                : dormBuildingMapper.selectList(new LambdaQueryWrapper<DormBuilding>().in(DormBuilding::getId, buildingIds));

        Map<Long, DormFloor> fmap = floors.stream().collect(Collectors.toMap(DormFloor::getId, f -> f, (a, b) -> a));
        Map<Long, DormBuilding> bmap = buildings.stream().collect(Collectors.toMap(DormBuilding::getId, b -> b, (a, b) -> a));

        Map<Long, RoomChainInfo> out = new LinkedHashMap<>();
        for (DormRoom r : rooms) {
            DormFloor f = fmap.get(r.getFloorId());
            DormBuilding b = f != null ? bmap.get(f.getBuildingId()) : null;
            out.put(r.getId(), new RoomChainInfo(
                    r.getId(),
                    r.getCapacity(),
                    r.getValid(),
                    f != null ? f.getValid() : null,
                    b != null ? b.getValid() : null,
                    b != null ? b.getGender() : null,
                    b != null ? b.getId() : null,
                    b != null ? b.getName() : null,
                    f != null ? f.getFloorNo() : null,
                    r.getRoomNo()
            ));
        }
        return out;
    }

    public DormRoomVO toVO(DormRoom room) {
        DormFloor f = dormFloorMapper.selectById(room.getFloorId());
        DormBuilding b = f != null ? dormBuildingMapper.selectById(f.getBuildingId()) : null;
        DormRoomVO vo = new DormRoomVO();
        vo.setId(room.getId());
        vo.setFloorId(room.getFloorId());
        vo.setRoomNo(room.getRoomNo());
        vo.setBuilding(room.getBuilding());
        vo.setCapacity(room.getCapacity());
        vo.setValid(room.getValid());
        if (f != null) {
            vo.setFloorNo(f.getFloorNo());
            vo.setFloorValid(f.getValid());
            vo.setBuildingId(f.getBuildingId());
        }
        if (b != null) {
            vo.setBuildingName(b.getName());
            vo.setGender(b.getGender());
            vo.setBuildingValid(b.getValid());
        }
        return vo;
    }

    public String formatRoomDisplay(DormRoom room) {
        DormFloor f = dormFloorMapper.selectById(room.getFloorId());
        int fn = f != null ? f.getFloorNo() : 0;
        return fn + "层-" + room.getRoomNo() + "号";
    }
}
