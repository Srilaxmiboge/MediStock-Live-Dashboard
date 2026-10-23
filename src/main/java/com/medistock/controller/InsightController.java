package com.medistock.controller;

import com.medistock.dto.InsightDtos.*;
import com.medistock.service.AccountService;
import com.medistock.service.DashboardService;
import com.medistock.service.ReportService;
import com.medistock.service.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InsightController {
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final SettingsService settingsService;
    private final AccountService accountService;

    public InsightController(DashboardService dashboardService, ReportService reportService,
                             SettingsService settingsService, AccountService accountService) {
        this.dashboardService = dashboardService;
        this.reportService = reportService;
        this.settingsService = settingsService;
        this.accountService = accountService;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return dashboardService.getSummary();
    }

    @GetMapping("/activity")
    public List<ActivityView> activity() {
        return dashboardService.getActivity();
    }

    @GetMapping("/reports/summary")
    public ReportSummary report(@RequestParam(name = "from", required = false) String from,
                                @RequestParam(name = "to", required = false) String to) {
        return reportService.summarize(from, to);
    }

    @GetMapping("/settings")
    public SettingsView settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(@RequestBody SettingsRequest request) {
        return settingsService.update(request);
    }

    @GetMapping("/settings/system")
    public SystemInfo systemInfo() {
        return settingsService.systemInfo();
    }

    @GetMapping("/me")
    public ProfileView me(Authentication authentication) {
        return accountService.profile(authentication.getName());
    }

    @PutMapping("/me")
    public ProfileView updateMe(Authentication authentication, @RequestBody ProfileRequest request) {
        return accountService.updateProfile(authentication.getName(), request);
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @RequestBody PasswordRequest request) {
        accountService.changePassword(authentication.getName(), request);
    }
}
