package com.seektop.fund.handler;

import com.seektop.dto.UserVIPCache;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.report.BaseReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;

@Component
@Slf4j
public class ReportExtendHandler {

    @Resource
    private UserVipUtils userVipUtils;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    public void extendReport(BaseReport data) {
        Integer uid = (Integer) data.get("uid");
        data.setUid(uid);

        UserVIPCache vipCache = userVipUtils.getUserVIPCache(uid);
        data.setVipLevel(ObjectUtils.isEmpty(vipCache) ? -1 : vipCache.getVipLevel());

        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(uid);
        if (!ObjectUtils.isEmpty(userlevel)) {
            data.setUserLevel(userlevel.getLevelId());
            data.setUserLevelName(userlevel.getName());
        }
    }

}