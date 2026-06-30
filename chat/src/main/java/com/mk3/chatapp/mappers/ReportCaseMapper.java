package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.ReportCaseDTO;
import com.mk3.chatapp.dtos.responses.ReportCaseSummaryDTO;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.utils.DateTimeMapperUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {UserMapper.class, MessageMapper.class, ReportMapper.class, DateTimeMapperUtil.class, AuditLogCustomMapper.class}
)
public interface ReportCaseMapper {

    @Mapping(target = "message", source = "message")
    @Mapping(target = "reports", source = "reports")
    @Mapping(target = "resolver", source = "resolver")
    @Mapping(target = "needsAttentionAt", source = "needsAttentionAt", qualifiedByName = "instantToString")
    @Mapping(target = "resolutionDate", source = "resolutionDate", qualifiedByName = "instantToString")
    @Mapping(target = "csamCase", source = "csamCase")
    ReportCaseDTO toDto(ReportCase reportCase);

    @Mapping(target = "message", source = "message")
    @Mapping(target = "reportCount", expression = "java(reportCase.getReports() != null ? reportCase.getReports().size() : 0)")
    @Mapping(target = "resolver", source = "resolver")
    @Mapping(target = "needsAttentionAt", source = "needsAttentionAt", qualifiedByName = "instantToString")
    @Mapping(target = "resolutionDate", source = "resolutionDate", qualifiedByName = "instantToString")
    @Mapping(target = "csamCase", source = "csamCase")
    ReportCaseSummaryDTO toSummaryDto(ReportCase reportCase);

    List<ReportCaseSummaryDTO> toSummaryDtos(List<ReportCase> reportCases);

    List<ReportCaseDTO> toDtos(List<ReportCase> reportCases);
}