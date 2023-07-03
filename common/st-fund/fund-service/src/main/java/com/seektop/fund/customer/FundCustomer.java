package com.seektop.fund.customer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.seektop.activity.service.GlActivityTransactionService;
import com.seektop.constant.HandlerResponseCode;
import com.seektop.enumerate.fund.FundReportEvent;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundReportRecordBusiness;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.handler.FundReportHandler;
import com.seektop.fund.model.GlFundReportRecord;
import com.seektop.report.fund.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@DependsOn(value = {"rabbitmqQueueConfiguration"})
public class FundCustomer {

    @Resource
    private Map<String, FundReportHandler> fundReportHandlerMap;
    @Resource
    private GlFundReportRecordBusiness glFundReportRecordBusiness;
    @Reference(timeout = 3000, retries = 3)
    private GlActivityTransactionService glActivityTransactionService;
    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @RabbitListener(queues = {"${global.fund.queue}"}, containerFactory = "defaultRabbitListener")
    public void receiveFund(Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] data = message.getBody();
        if (data == null || data.length <= 0) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        String stringData = new String(data);
        log.info("handler data:{}", stringData);
        try {
            JSONObject reportObj = JSON.parseObject(stringData);
            if (reportObj.containsKey("event") == false) {
                channel.basicAck(deliveryTag, false);
                log.info("没有Event属性,不进行处理");
                return;
            }
            int event = reportObj.getIntValue("event");
            if(handlerTransferEvent(event, reportObj)){
                channel.basicAck(deliveryTag, false);
                log.info("gametransfer report, end .");
                return;
            }
            String transactionId = reportObj.getString("transactionId");
            FundReportHandler handler = getHandler(event);
            if (ObjectUtils.isEmpty(handler)) {
                channel.basicAck(deliveryTag, false);
                log.info("对应事件的处理器不存在,不进行处理");
                return;
            }
            //兼容不同环境
            if(!preHandlerMessage(transactionId, stringData, channel, deliveryTag)){
                channel.basicAck(deliveryTag, false);
                return;
            }
            HandlerResponse handlerResponse = doHandlerMessage(handler, event, stringData);
            postHandlerMessage(stringData, handlerResponse, true, channel, deliveryTag);
        } catch (Exception ex) {
            log.error("report handler error, message={}",stringData);
            log.error("FundCustomer receiveFund error", ex);
        }
    }

    private HandlerResponse doHandlerMessage(FundReportHandler handler, int event, String stringData) throws GlobalException, IOException {
        HandlerResponse handlerResponse = null;
        switch (event) {
            case 3000 :
                UserAccountCreateReport userAccountCreateReport = JSON.parseObject(stringData, UserAccountCreateReport.class);
                try {
                    handlerResponse = handler.handleFund(userAccountCreateReport);
                    handlerResponse.setAttachment(userAccountCreateReport.getAttachment());
                }catch (GlobalException e){
                    return HandlerResponse.generateByFundBaseReport(userAccountCreateReport, HandlerResponseCode.FAIL.getCode(), "userAccount != null");
                }
                break;
            case 3001 :
                ActivityBonusReport activityBonusReport = JSON.parseObject(stringData, ActivityBonusReport.class);
                handlerResponse =  handler.handleFund(activityBonusReport);
                handlerResponse.setAttachment(activityBonusReport.getAttachment());
                break;
        }
        return handlerResponse;
    }

    private void postHandlerMessage(String stringData, HandlerResponse handlerResponse, boolean isNew, Channel channel, long deliveryTag) throws IOException {
        log.info("post handler message, message = {}, handlerResponse = {}, isNew = {}",
            stringData, JSON.toJSON(handlerResponse), isNew);
        if(handlerResponse == null){
            log.error("stringData = {}, not handlerResponse ", stringData);
            if(isNew){
                channel.basicAck(deliveryTag, false);
            }
            return;
        }
        if(isNew){
            GlFundReportRecord glFundReportRecord = new GlFundReportRecord();
            glFundReportRecord.setTransactionId(handlerResponse.getTransactionId());
            glFundReportRecord.setCallbackDate(new Date());
            glFundReportRecord.setCallbackText(JSON.toJSONString(handlerResponse));
            glFundReportRecord.setStatus(handlerResponse.getCode());
            glFundReportRecord.setEvent(handlerResponse.getEvent());
            glFundReportRecord.setLastupdate(new Date());
            glFundReportRecordBusiness.updateByPrimaryKeySelective(glFundReportRecord);
        }
        try{
            if(isNew){
                channel.basicAck(deliveryTag, false);
            }
        }catch (Exception e){
            log.info("tradeId ack error,id={}", handlerResponse.getTransactionId());
            log.error(e.getMessage(), e);
        }
        Integer event = handlerResponse.getEvent();
        if(event == 3000 ){
            log.info("start event=3000 callback handlerResponse = {}",handlerResponse);
            FundBaseReport fundBaseReport = JSON.parseObject(stringData, FundBaseReport.class);
            handlerResponse.setAttachment(fundBaseReport.getAttachment());
            glActivityTransactionService.callBack(handlerResponse);
        }
        else if(event == 3001){
            log.info("start event=3001 callback handlerResponse = {}",handlerResponse);
            Object rpcResponse = null;
            FundBaseReport fundBaseReport = JSON.parseObject(stringData, FundBaseReport.class);
            handlerResponse.setAttachment(fundBaseReport.getAttachment());
            rpcResponse = glActivityTransactionService.callBack(handlerResponse);
            log.info("callcack event = 3000, handlerResponse = {}, rpcResponse = {}", handlerResponse, rpcResponse);
        }
    }

    private boolean preHandlerMessage(String transactionId, String message, Channel channel, long deliveryTag) throws IOException {
        log.info("pre handler message :{}",message );
        GlFundReportRecord glFundReportRecord = glFundReportRecordBusiness.findById(transactionId);
        //如果已经处理过了，直接给发回调
        if(glFundReportRecord != null){
            log.info("transaction id = {} has handlered.",transactionId);
            HandlerResponse hr = JSON.parseObject(glFundReportRecord.getCallbackText(), HandlerResponse.class);
            postHandlerMessage(message, hr, false, channel, deliveryTag);
            return false;
        }

        JSONObject reportObj = JSON.parseObject(message);
        int event = reportObj.getIntValue("event");
        Long timestamp = reportObj.getLongValue("timestamp");
        glFundReportRecord = new GlFundReportRecord();
        glFundReportRecord.setTransactionId(transactionId);
        glFundReportRecord.setStatus(0);
        glFundReportRecord.setReportDate(timestamp);
        glFundReportRecord.setEvent(event);
        glFundReportRecord.setLastupdate(new Date());
        glFundReportRecordBusiness.save(glFundReportRecord);
        return true;
    }

    private FundReportHandler getHandler(int event) {
        return fundReportHandlerMap.get("fundReportHandler" + event);
    }

    private boolean handlerTransferEvent(Integer event, JSONObject reportObj){
        TransferReport report = reportObj.toJavaObject(TransferReport.class);
        if(event == FundReportEvent.TRANSFER.value()){
            try {
                glFundUserAccountBusiness.transfer(report.getUserId(), report.getTransactionId(), report.getAmount(), report.getChangeType(), report.getRemark(), report.getNegative());
            } catch (GlobalException e) {
                log.error(e.getExtraMessage(), e);
                return false;
            }
            return true;
        }
        if(event == FundReportEvent.TRANSFER_ROLLBACK.value()){
            try {
                glFundUserAccountBusiness.transferRecover(report.getTransactionId(), report.getNegative());
            } catch (GlobalException e) {
                log.error(e.getExtraMessage(), e);
                return false;
            }
            return true;
        }
        return false;
    }
}
