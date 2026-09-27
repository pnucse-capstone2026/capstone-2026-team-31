package com.ai_got_this.pnl_service.web;

import com.ai_got_this.pnl_service.service.PnlReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pnl")
public class PnlController {
    private final PnlReadService pnlReadService;

    public PnlController(PnlReadService pnlReadService) {
        this.pnlReadService = pnlReadService;
    }

    @GetMapping
    public PnlReadService.PortfolioPnl portfolio() {
        return pnlReadService.portfolio();
    }
}
