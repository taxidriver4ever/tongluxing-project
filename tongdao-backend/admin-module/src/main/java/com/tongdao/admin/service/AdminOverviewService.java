package com.tongdao.admin.service;

import java.time.LocalDateTime;

import com.tongdao.admin.vo.AdminOperationOverviewVO;

public interface AdminOverviewService {

    AdminOperationOverviewVO overview(LocalDateTime startTime, LocalDateTime endTime);
}
