package com.spot.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.account.entity.OperationLogEntity;
import com.spot.account.repo.OperationLogRepo;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {
    private final OperationLogRepo repo;
    private final ObjectMapper mapper;

    public OperationLogService(OperationLogRepo repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void log(UUID userId, String type, String ip, String deviceId, Map<String, Object> detail) {
        OperationLogEntity e = new OperationLogEntity();
        e.setUserId(userId);
        e.setType(type);
        e.setIp(ip);
        e.setDeviceId(deviceId);
        e.setDetailJson(toJson(detail));
        repo.save(e);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return mapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
