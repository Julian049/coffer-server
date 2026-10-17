package com.dev.julian09.financeserver.sync.api;

import com.dev.julian09.financeserver.shared.web.CurrentUser;
import com.dev.julian09.financeserver.sync.dto.PullResponse;
import com.dev.julian09.financeserver.sync.dto.PushRequest;
import com.dev.julian09.financeserver.sync.dto.PushResponse;
import com.dev.julian09.financeserver.sync.service.PullService;
import com.dev.julian09.financeserver.sync.service.PushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final PullService pull;
    private final PushService push;

    public SyncController(PullService pull, PushService push) {
        this.pull = pull;
        this.push = push;
    }

    /** Applies a batch; one result per change, a rejected change is not an HTTP error (REQ-SYN-02, 04). */
    @PostMapping("/push")
    public PushResponse push(CurrentUser user, @Valid @RequestBody PushRequest request) {
        return push.push(user.id(), request.changes());
    }

    /** Changes with version > since, oldest first (REQ-SYN-01, REQ-AUT-05). */
    @GetMapping("/pull")
    public PullResponse pull(CurrentUser user,
                             @RequestParam @Min(0) long since,
                             @RequestParam(defaultValue = "500") @Min(1) @Max(500) int limit) {
        return pull.pull(user.id(), since, limit);
    }
}
