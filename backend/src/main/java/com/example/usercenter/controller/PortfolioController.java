package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.dto.portfolio.PortfolioCreateRequest;
import com.example.usercenter.model.dto.portfolio.PortfolioUpdateRequest;
import com.example.usercenter.model.vo.PortfolioVO;
import com.example.usercenter.service.LoginUserService;
import com.example.usercenter.service.PortfolioPdfExportService;
import com.example.usercenter.service.PortfolioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/portfolios")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final PortfolioPdfExportService portfolioPdfExportService;
    private final LoginUserService loginUserService;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioPdfExportService portfolioPdfExportService,
            LoginUserService loginUserService
    ) {
        this.portfolioService = portfolioService;
        this.portfolioPdfExportService = portfolioPdfExportService;
        this.loginUserService = loginUserService;
    }

    @PostMapping
    public BaseResponse<PortfolioVO> create(@Valid @RequestBody PortfolioCreateRequest body, HttpServletRequest request) {
        return ResultUtils.success(portfolioService.createOwned(loginUserService.requireUserId(request), body));
    }

    @GetMapping
    public BaseResponse<List<PortfolioVO>> list(HttpServletRequest request) {
        return ResultUtils.success(portfolioService.listOwned(loginUserService.requireUserId(request)));
    }

    @GetMapping("/{portfolioId}")
    public BaseResponse<PortfolioVO> detail(@PathVariable Long portfolioId, HttpServletRequest request) {
        return ResultUtils.success(portfolioService.getOwned(loginUserService.requireUserId(request), portfolioId));
    }

    @GetMapping(value = "/{portfolioId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long portfolioId, HttpServletRequest request) {
        byte[] pdf = portfolioPdfExportService.exportOwned(loginUserService.requireUserId(request), portfolioId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=photography-archive-portfolio-" + portfolioId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @PatchMapping("/{portfolioId}")
    public BaseResponse<PortfolioVO> update(
            @PathVariable Long portfolioId,
            @Valid @RequestBody PortfolioUpdateRequest body,
            HttpServletRequest request
    ) {
        return ResultUtils.success(portfolioService.updateOwned(loginUserService.requireUserId(request), portfolioId, body));
    }

    @PostMapping("/{portfolioId}/share")
    public BaseResponse<PortfolioVO> enableSharing(@PathVariable Long portfolioId, HttpServletRequest request) {
        return ResultUtils.success(portfolioService.enableSharing(loginUserService.requireUserId(request), portfolioId));
    }

    @DeleteMapping("/{portfolioId}/share")
    public BaseResponse<PortfolioVO> disableSharing(@PathVariable Long portfolioId, HttpServletRequest request) {
        return ResultUtils.success(portfolioService.disableSharing(loginUserService.requireUserId(request), portfolioId));
    }

    @DeleteMapping("/{portfolioId}")
    public BaseResponse<Boolean> delete(@PathVariable Long portfolioId, HttpServletRequest request) {
        return ResultUtils.success(portfolioService.deleteOwned(loginUserService.requireUserId(request), portfolioId));
    }
}
