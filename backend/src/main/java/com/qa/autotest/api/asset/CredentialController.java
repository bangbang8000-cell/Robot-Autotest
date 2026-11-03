package com.qa.autotest.api.asset;

import com.qa.autotest.application.asset.CredentialService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.domain.asset.Credential;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 凭据管理（AST-03）：接口永不回显明文 / 密文。 */
@RestController
@RequestMapping("/api/projects/{projectId}/credentials")
public class CredentialController {

    public record CreateCredentialRequest(String name, String credType, String username,
                                          String secret) {
    }

    public record UpdateCredentialRequest(String name, String username, String secret) {
    }

    private final CredentialService credentials;

    public CredentialController(CredentialService credentials) {
        this.credentials = credentials;
    }

    @GetMapping
    public ApiResponse<List<Credential>> list(@PathVariable long projectId) {
        return ApiResponse.ok(credentials.list(projectId));
    }

    @PostMapping
    public ApiResponse<Credential> create(@PathVariable long projectId,
                                          @RequestBody CreateCredentialRequest request) {
        return ApiResponse.ok(credentials.create(projectId, request.name(), request.credType(),
                request.username(), request.secret()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Credential> update(@PathVariable long projectId, @PathVariable long id,
                                          @RequestBody UpdateCredentialRequest request) {
        return ApiResponse.ok(credentials.update(projectId, id, request.name(),
                request.username(), request.secret()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @PathVariable long id) {
        credentials.delete(projectId, id);
        return ApiResponse.ok(null);
    }
}
