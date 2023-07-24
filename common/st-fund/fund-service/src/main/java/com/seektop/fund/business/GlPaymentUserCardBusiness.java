package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlPaymentUserCardMapper;
import com.seektop.fund.model.GlPaymentUserCard;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;


/**
 * Created by CodeGenerator on 2018/12/19.
 */
@Component
public class GlPaymentUserCardBusiness extends AbstractBusiness<GlPaymentUserCard> {

    @Resource
    private GlPaymentUserCardMapper glPaymentUsercardMapper;


    public List<String> findUserCardList(Integer userId) {
        return glPaymentUsercardMapper.findUserCardNoList(userId);
    }

    public List<String> findUserNameList(Integer userId) {
        return glPaymentUsercardMapper.findUserCardNameList(userId);
    }

    public void deleteByUserId(Integer userId) {
        glPaymentUsercardMapper.deleteByUserId(userId);
    }

    public GlPaymentUserCard findUserCard(Integer userId, String cardNo) {
        return glPaymentUsercardMapper.findUserCard(userId, cardNo);
    }

    public GlPaymentUserCard findUserCardByName(Integer userId, String cardUserName) {
        return glPaymentUsercardMapper.findUserCardByName(userId, cardUserName);
    }
}
