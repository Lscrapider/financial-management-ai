package com.scrapider.finance.domain.dto;

import java.time.LocalDateTime;

public class SceneAnalysisReportTargetDTO {

    private String targetType;
    private String targetCode;
    private String targetName;
    private Long latestReportId;
    private String latestTaskNo;
    private String latestStatus;
    private String latestReportType;
    private String latestGenerationType;
    private Integer latestVersionNo;
    private String latestModel;
    private String latestReportText;
    private LocalDateTime latestGeneratedAt;
    private LocalDateTime latestCreatedAt;
    private Long reportCount;

    public String getTargetType() {
        return this.targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetCode() {
        return this.targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public String getTargetName() {
        return this.targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public Long getLatestReportId() {
        return this.latestReportId;
    }

    public void setLatestReportId(Long latestReportId) {
        this.latestReportId = latestReportId;
    }

    public String getLatestTaskNo() {
        return this.latestTaskNo;
    }

    public void setLatestTaskNo(String latestTaskNo) {
        this.latestTaskNo = latestTaskNo;
    }

    public String getLatestStatus() {
        return this.latestStatus;
    }

    public void setLatestStatus(String latestStatus) {
        this.latestStatus = latestStatus;
    }

    public String getLatestReportType() {
        return this.latestReportType;
    }

    public void setLatestReportType(String latestReportType) {
        this.latestReportType = latestReportType;
    }

    public String getLatestGenerationType() {
        return this.latestGenerationType;
    }

    public void setLatestGenerationType(String latestGenerationType) {
        this.latestGenerationType = latestGenerationType;
    }

    public Integer getLatestVersionNo() {
        return this.latestVersionNo;
    }

    public void setLatestVersionNo(Integer latestVersionNo) {
        this.latestVersionNo = latestVersionNo;
    }

    public String getLatestModel() {
        return this.latestModel;
    }

    public void setLatestModel(String latestModel) {
        this.latestModel = latestModel;
    }

    public String getLatestReportText() {
        return this.latestReportText;
    }

    public void setLatestReportText(String latestReportText) {
        this.latestReportText = latestReportText;
    }

    public LocalDateTime getLatestGeneratedAt() {
        return this.latestGeneratedAt;
    }

    public void setLatestGeneratedAt(LocalDateTime latestGeneratedAt) {
        this.latestGeneratedAt = latestGeneratedAt;
    }

    public LocalDateTime getLatestCreatedAt() {
        return this.latestCreatedAt;
    }

    public void setLatestCreatedAt(LocalDateTime latestCreatedAt) {
        this.latestCreatedAt = latestCreatedAt;
    }

    public Long getReportCount() {
        return this.reportCount;
    }

    public void setReportCount(Long reportCount) {
        this.reportCount = reportCount;
    }
}
