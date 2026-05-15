package com.cqie.deepcover.agent.internal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Agent 内部接口鉴权。
 *
 * <p>MVP 使用共享密钥，后续部署到公网时可以替换为网关鉴权或 mTLS。</p>
 */
@Service
public class AgentInternalAuthService {
    private final String internalSecret;

    public AgentInternalAuthService(
            @Value("${deep-cover.agent.internal-secret:dev-agent-secret}") String internalSecret
    ) {
        this.internalSecret = internalSecret;
    }

    public void requireAuthorized(String providedSecret) {
        if (providedSecret == null || !providedSecret.equals(internalSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent internal secret.");
        }
    }
}
