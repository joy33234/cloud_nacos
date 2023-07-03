package com.seektop.fund.customer;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawMerchantAccountBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawTransactionalBusiness;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.report.fund.WithdrawMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Service
public class WithdrawReceiver {

    @Resource
    private WithdrawSender withdrawSender;

    @Resource
    private GlWithdrawBusiness withdrawBusiness;
    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountService;
    @Resource
    private GlWithdrawTransactionalBusiness glWithdrawTransactionalBusiness;

    /**
     * DIRECT模式.
     *
     * @param message the message
     * @param channel the channel
     * @throws IOException the io exception  这里异常需要处理
     */
    @RabbitListener(queues = {"${withdraw.queue}"}, containerFactory = "withdrawRabbitListener")
    public void message(Message message, Channel channel) throws Exception {
        byte[] receiveBytes = message.getBody();
        String receiveStr = new String(receiveBytes);

        WithdrawMessage rbMessage = JSON.parseObject(receiveStr, WithdrawMessage.class);
        log.info("receiveStr =  " + rbMessage.toString());
        try {
            GlWithdraw withdraw = withdrawBusiness.findById(rbMessage.getTradeId());
            if (withdraw == null) {
                log.info("自动出款订单数据异常:{}", rbMessage.getTradeId());
                if (rbMessage.getRetries() == null || rbMessage.getRetries() < 5) {
                    rbMessage.setRetries(rbMessage.getRetries() == null ? 1 : (rbMessage.getRetries() + 1));
                    withdrawSender.sendWithdrawMsg(rbMessage);
                }
                return;
            }
            if (DigitalCoinEnum.getCoinList().contains(withdraw.getCoin())) {
                log.info("自动出款订单是数字货币，不执行出款:{}", rbMessage.getTradeId());
                return;
            }
            if (withdraw.getStatus() != FundConstant.WithdrawStatus.PENDING) {
                log.info("自动出款订单状态异常:{}", JSON.toJSONString(withdraw));
                if (rbMessage.getRetries() == null || rbMessage.getRetries() < 5) {
                    rbMessage.setRetries(rbMessage.getRetries() == null ? 1 : (rbMessage.getRetries() + 1));
                    withdrawSender.sendWithdrawMsg(rbMessage);
                }
                return;
            }
            GlWithdrawMerchantAccount merchantAccount = glWithdrawMerchantAccountService.findById(rbMessage.getMerchantId());
            if (merchantAccount == null || merchantAccount.getStatus() != 0) {
                log.info("自动出款商户异常:{}", null != merchantAccount ? JSON.toJSONString(merchantAccount) : withdraw.getMerchantId());
                withdrawBusiness.doWithdrawApiFail(withdraw.getOrderId(), merchantAccount, "通道不存在或已关闭", "admin");
            }
            glWithdrawTransactionalBusiness.doWithdrawApi(withdraw, merchantAccount, "admin", "三方自动出款");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IllegalArgumentException(e);
        } finally {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }

}