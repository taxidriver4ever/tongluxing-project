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
                   s.track_quality, s.raw_point_count, s.uploaded_point_count,
                   s.compressed_point_count, s.client_degraded_segment_count,
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
                   s.track_quality, s.raw_point_count, s.uploaded_point_count,
                   s.compressed_point_count, s.client_degraded_segment_count,
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
                   case when ms.user_id=s.primary_user_id then coalesce((
                       select sum(r.distance_from_prev)
                       from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id
                         and r.valid_point=1 and r.deleted=0
                   ),0) else 0 end distance_meters,
                   case when ms.user_id=s.primary_user_id then (
                       select count(*) from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id and r.deleted=0
                   ) else 0 end total_point_count,
                   case when ms.user_id=s.primary_user_id then (
                       select count(*) from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id
                         and r.valid_point=1 and r.deleted=0
                   ) else 0 end valid_point_count,
                   case when ms.user_id=s.primary_user_id then (
                       select r.longitude from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id and r.deleted=0 and r.valid_point=1
                       order by r.record_time desc, r.sequence_no desc limit 1
                   ) else ml.longitude end latest_longitude,
                   case when ms.user_id=s.primary_user_id then (
                       select r.latitude from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id and r.deleted=0 and r.valid_point=1
                       order by r.record_time desc, r.sequence_no desc limit 1
                   ) else ml.latitude end latest_latitude,
                   case when ms.user_id=s.primary_user_id then (
                       select cast(r.accuracy as signed) from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id and r.deleted=0 and r.valid_point=1
                       order by r.record_time desc, r.sequence_no desc limit 1
                   ) else cast(ml.accuracy as signed) end latest_accuracy_meters,
                   case when ms.user_id=s.primary_user_id then (
                       select r.mock_location from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id and r.deleted=0 and r.valid_point=1
                       order by r.record_time desc, r.sequence_no desc limit 1
                   ) else ml.mock_location end mock_location,
                   case when ms.user_id=s.primary_user_id then (
                       select r.record_time from driver_track_record r
                       where r.trip_id=ms.trip_id and r.driver_id=ms.user_id and r.deleted=0 and r.valid_point=1
                       order by r.record_time desc, r.sequence_no desc limit 1
                   ) else ml.record_time end latest_location_time
            from trip_member_snapshot ms
            join trip_track_summary s on s.trip_id=ms.trip_id and s.deleted=0
            left join user_profile p on p.user_id=ms.user_id and p.deleted=0
            left join trip_member_latest_location ml on ml.trip_id=ms.trip_id
                 and ml.member_user_id=ms.user_id and ml.deleted=0
            where ms.trip_id=#{tripId} and ms.join_status in ('OWNER','APPROVED')
            order by case when ms.user_id=s.primary_user_id then 0 else 1 end, ms.joined_at
            """)
    List<AdminTripTrackMemberVO> members(Long tripId);

    @Select("""
            select a.id, a.user_id, a.previous_point_id, a.current_point_id, a.anomaly_type,
                   a.risk_score, cast(a.detail_json as char) detail_json, a.occurred_at
            from trip_track_anomaly a
            join trip_track_summary s on s.trip_id=a.trip_id and s.deleted=0
            where a.trip_id=#{tripId} and a.user_id=s.primary_user_id
            order by a.occurred_at desc
            limit 500
            """)
    List<AdminTripTrackAnomalyVO> anomalies(Long tripId);

    @Select("""
            select r.id, r.driver_id user_id, r.sequence_no, r.longitude, r.latitude,
                   r.accuracy accuracy_meters, r.calculated_speed_kmh,
                   r.raw_distance_from_prev raw_distance_meters,
                   r.distance_from_prev accepted_distance_meters,
                   r.point_status, r.risk_score, r.risk_flags, r.reject_reason,
                   r.mock_location, r.record_time location_time
            from driver_track_record r
            join trip_track_summary s on s.trip_id=r.trip_id and s.primary_user_id=r.driver_id and s.deleted=0
            where r.trip_id=#{tripId} and r.deleted=0
            order by r.record_time, r.sequence_no
            limit 5000
            """)
    List<AdminTripTrackPointVO> points(Long tripId);
}
