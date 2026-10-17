package com.dev.julian09.financeserver.sync.api;

import com.dev.julian09.financeserver.shared.web.CurrentUser;
import com.dev.julian09.financeserver.sync.dto.PullResponse;
import com.dev.julian09.financeserver.sync.service.PullService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final PullService pull;

    public SyncController(PullService pull) {
        this.pull = pull;
    }

    /** Changes with version > since, oldest first (REQ-SYN-01, REQ-AUT-05). */
    @GetMapping("/pull")
    public PullResponse pull(CurrentUser user,
                             @RequestParam @Min(0) long since,
                             @RequestParam(defaultValue = "500") @Min(1) @Max(500) int limit) {
        return pull.pull(user.id(), since, limit);
    }
}
