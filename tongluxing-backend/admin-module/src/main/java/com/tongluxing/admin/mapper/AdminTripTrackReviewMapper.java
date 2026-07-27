package com.tongluxing.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.admin.vo.AdminTripTrackAnomalyVO;
import com.tongluxing.admin.vo.AdminTripTrackMemberVO;
import com.tongluxing.admin.vo.AdminTripTrackPointVO;
import com.tongluxing.admin.vo.AdminTripTrackReviewSummaryVO;

/** 轨迹异常后台只读查询 Mapper。 */
@Mapper
public interface AdminTripTrackReviewMapper {

    @Select("""
            select s.trip_id, t.title trip_title, s.primary_user_id,
                   coalesce(p.nickname, ms.nickname_snapshot, '') captain_nickname,
                   s.raw_distance_meters, s.filtered_distance_meters, s.approved_distance_meters,
                   s.total_point_count, s.valid_point_count, s.invalid_point_count,
                   s.location_gap_count, s.warning_count, s.hard_anomaly_count,
                   s.risk_score, s.risk_level, s.settlement_status, s.review_reason,
                   s.reviewer_id, s.reviewed_at, s.updated_at
            from trip_track_summary s
            join trip t on t.id=s.trip_id and t.deleted=0
            left join user_profile p on p.user_id=s.primary_user_id and p.deleted=0
            left join trip_member_snapshot ms on ms.trip_id=s.trip_id
                 and ms.user_id=s.primary_user_id and ms.join_status='OWNER'
            where s.deleted=0
              and (#{riskLevel} is null or #{riskLevel}='' or s.risk_level=#{riskLevel})
              and (#{status} is null or #{status}='' or s.settlement_status=#{status})
            order by case when s.settlement_status='MANUAL_REVIEW' then 0 else 1 end,
                     s.risk_score desc, s.updated_at desc
            limit #{offset}, #{size}
            """)
    List<AdminTripTrackReviewSummaryVO> page(
            @Param("riskLevel") String riskLevel,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            select count(*) from trip_track_summary s
            where s.deleted=0
              and (#{riskLevel} is null or #{riskLevel}='' or s.risk_level=#{riskLevel})
              and (#{status} is null or #{status}='' or s.settlement_status=#{status})
            """)
    long count(@Param("riskLevel") String riskLevel, @Param("status") String status);

    @Select("""
            select s.trip_id, t.title trip_title, s.primary_user_id,
                   coalesce(p.nickname, ms.nickname_snapshot, '') captain_nickname,
                   s.raw_distance_meters, s.filtered_distance_meters, s.approved_distance_meters,
                   s.total_point_count, s.valid_point_count, s.invalid_point_count,
                   s.location_gap_count, s.warning_count, s.hard_anomaly_count,
                   s.risk_score, s.risk_level, s.settlement_status, s.review_reason,
                   s.reviewer_id, s.reviewed_at, s.updated_at
            from trip_track_summary s
            join trip t on t.id=s.trip_id and t.deleted=0
            left join user_profile p on p.user_id=s.primary_user_id and p.deleted=0
            left join trip_member_snapshot ms on ms.trip_id=s.trip_id
                 and ms.user_id=s.primary_user_id and ms.join_status='OWNER'
            where s.trip_id=#{tripId} and s.deleted=0
            limit 1
            """)
    AdminTripTrackReviewSummaryVO detail(Long tripId);

    @Select("""
            select ms.user_id, ms.member_role, ms.join_status,
                   coalesce(p.nickname, ms.nickname_snapshot, '') nickname,
                   coalesce(sum(r.distance_from_prev),0) distance_meters,
                   count(r.id) total_point_count,
                   coalesce(sum(case when r.valid_point=1 then 1 else 0 end),0) valid_point_count
            from trip_member_snapshot ms
            left join user_profile p on p.user_id=ms.user_id and p.deleted=0
            left join driver_track_record r on r.trip_id=ms.trip_id
                 and r.driver_id=ms.user_id and r.deleted=0
            where ms.trip_id=#{tripId} and ms.join_status in ('OWNER','APPROVED')
            group by ms.user_id, ms.member_role, ms.join_status,
                     coalesce(p.nickname, ms.nickname_snapshot, '')
            order by case when ms.join_status='OWNER' then 0 else 1 end, ms.joined_at
            """)
    List<AdminTripTrackMemberVO> members(Long tripId);

    @Select("""
            select id, user_id, previous_point_id, current_point_id, anomaly_type,
                   risk_score, cast(detail_json as char) detail_json, occurred_at
            from trip_track_anomaly
            where trip_id=#{tripId}
            order by occurred_at desc
            limit 500
            """)
    List<AdminTripTrackAnomalyVO> anomalies(Long tripId);

    @Select("""
            select id, driver_id user_id, sequence_no, longitude, latitude,
                   accuracy accuracy_meters, calculated_speed_kmh,
                   raw_distance_from_prev raw_distance_meters,
                   distance_from_prev accepted_distance_meters,
                   point_status, risk_score, risk_flags, reject_reason,
                   mock_location, record_time location_time
            from driver_track_record
            where trip_id=#{tripId} and deleted=0
            order by record_time, sequence_no
            limit 5000
            """)
    List<AdminTripTrackPointVO> points(Long tripId);
}
