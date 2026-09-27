package com.ai_got_this.direction_evaluation_service.web;

import com.ai_got_this.direction_evaluation_service.domain.ConfidenceBand;
import com.ai_got_this.direction_evaluation_service.service.DirectionEvaluationReadService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/direction-evaluations")
public class DirectionEvaluationController {
    private final DirectionEvaluationReadService readService;

    public DirectionEvaluationController(DirectionEvaluationReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/summary")
    public DirectionEvaluationReadService.Summary summary() { return readService.summary(); }

    @GetMapping("/by-ticker")
    public Map<String, DirectionEvaluationReadService.Summary> byTicker() { return readService.byTicker(); }

    @GetMapping("/by-confidence-band")
    public Map<ConfidenceBand, DirectionEvaluationReadService.Summary> byConfidenceBand() {
        return readService.byConfidenceBand();
    }

    @GetMapping("/pending")
    public List<DirectionEvaluationReadService.PendingEvaluation> pending() { return readService.pending(); }
}
