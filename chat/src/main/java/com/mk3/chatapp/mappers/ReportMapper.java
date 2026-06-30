package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.ReportDTO;
import com.mk3.chatapp.models.Report;
import com.mk3.chatapp.utils.DateTimeMapperUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, DateTimeMapperUtil.class})
public interface ReportMapper {
    @Mapping(target = "reportCaseId", source = "report.reportCase.id")
    @Mapping(target = "messageId", source = "report.message.id")
    @Mapping(target = "reportedUserId", source = "report.reportedUser.id")
    @Mapping(target = "createdAt", source = "report.createdAt", qualifiedByName = "instantToString")
    ReportDTO toDto(Report report);
}

