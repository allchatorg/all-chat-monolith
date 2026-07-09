package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.BanAppealRequestDTO;
import com.mk3.chatapp.dtos.requests.BanAppealResolutionRequestDTO;
import com.mk3.chatapp.dtos.responses.BanAppealAdminDetailDTO;
import com.mk3.chatapp.dtos.responses.BanAppealAdminListDTO;
import com.mk3.chatapp.dtos.responses.BanAppealUserViewDTO;
import com.mk3.chatapp.dtos.responses.MyBanContextDTO;
import com.mk3.chatapp.enums.BanAppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public interface BanAppealService {

    MyBanContextDTO getMyBanContext();

    BanAppealUserViewDTO submitAppeal(BanAppealRequestDTO request);

    BanAppealUserViewDTO getMyAppeal();

    Page<BanAppealAdminListDTO> listAppeals(BanAppealStatus status, boolean openOnly, int page, int pageSize);

    BanAppealAdminDetailDTO getAppeal(Long appealId);

    BanAppealAdminDetailDTO claimAppeal(Long appealId);

    BanAppealAdminDetailDTO resolveAppeal(Long appealId, BanAppealResolutionRequestDTO request);
}
