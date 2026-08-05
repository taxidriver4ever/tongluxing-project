package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.team.integration.TeamTripPort;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.entity.TripMemberSnapshot;
import com.tongluxing.trip.mapper.TripMapper;
import com.tongluxing.trip.mapper.TripMemberSnapshotMapper;
import com.tongluxing.common.utils.SnowflakeIdGenerator;

import lombok.RequiredArgsConstructor;

/** 车队模块访问行程模块的适配器。 */
@Component
@RequiredArgsConstructor
public class TeamTripAdapter implements TeamTripPort {

    private final TripMapper tripMapper;
    private final TripMemberSnapshotMapper tripMemberSnapshotMapper;

    @Override
    public Long findRunningOwnedTripId(Long userId) {
        return tripMapper.findRunningTripIdByUserId(userId);
    }

    @Override
    public void markMemberExited(
            Long tripId, Long userId, String exitStatus, java.time.LocalDateTime exitedAt) {
        if (tripMemberSnapshotMapper.markExited(tripId, userId, exitStatus, exitedAt) > 0) {
            return;
        }
        // 兼容升级前只写 team_member、未写 trip_member_snapshot 的已入队成员。
        TripMemberSnapshot member = new TripMemberSnapshot();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setTripId(tripId);
        member.setUserId(userId);
        member.setMemberRole("MEMBER");
        member.setJoinStatus(exitStatus);
        member.setNicknameSnapshot("用户" + userId);
        member.setJoinedAt(exitedAt);
        member.setCreatedAt(exitedAt);
        member.setUpdatedAt(exitedAt);
        tripMemberSnapshotMapper.insert(member);
    }

    @Override
    public void addApprovedMember(
            Long tripId, Long userId, Long vehicleId, String nickname,
            String vehicle, java.time.LocalDateTime joinedAt) {
        if (tripMemberSnapshotMapper.reactivateApproved(
                tripId, userId, vehicleId, nickname, vehicle, joinedAt) > 0) {
            return;
        }
        TripMemberSnapshot member = new TripMemberSnapshot();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setTripId(tripId);
        member.setUserId(userId);
        member.setVehicleId(vehicleId);
        member.setMemberRole("MEMBER");
        member.setJoinStatus("APPROVED");
        member.setNicknameSnapshot(nickname);
        member.setVehicleSnapshot(vehicle);
        member.setJoinedAt(joinedAt);
        member.setCreatedAt(joinedAt);
        member.setUpdatedAt(joinedAt);
        tripMemberSnapshotMapper.insert(member);
    }

    @Override
    public TeamTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            return null;
        }
        return new TeamTripDTO(
                trip.getId(),
                trip.getUserId(),
                trip.getCaptainUserId(),
                trip.getVehicleId(),
                trip.getTripType(),
                trip.getStartName(),
                trip.getEndName(),
                trip.getDepartureTime(),
                trip.getStatus());
    }
}
