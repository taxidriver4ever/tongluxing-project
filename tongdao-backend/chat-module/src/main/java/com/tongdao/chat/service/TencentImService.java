package com.tongdao.chat.service;

import com.tongdao.chat.vo.ImUserSigResponse;

public interface TencentImService {

    String generateUserSig(String userId);

    ImUserSigResponse generateCurrentUserSig();

    void createGroup(String groupId, String ownerUserId, String groupName);

    void destroyGroup(String groupId);

    void addGroupMember(String groupId, String userId);

    void removeGroupMember(String groupId, String userId);
}
