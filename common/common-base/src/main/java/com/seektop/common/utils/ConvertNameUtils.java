package com.seektop.common.utils;


import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
//TODO:待优化  建议分拆到各个模块

/**
 * Created by Tyler on 2019/7/8.
 */
public class ConvertNameUtils {

    public static String convertOsTypeName(Integer oSType) {
        if(ObjectUtils.isEmpty(oSType)){
            return "无";
        }
        switch (oSType.intValue()) {
            case 0:
                return "PC";
            case 1:
                return "H5";
            case 2:
                return "安卓";
            case 3:
                return "IOS";
            default:
                return "PC";
        }
    }

    public static String convertAppTypeName(Integer appType) {
        if(ObjectUtils.isEmpty(appType)){
            return "无";
        }
        switch (appType.intValue()) {
            case 0:
                return "现金网";
            case 1:
                return "体育";
            case 2:
                return "代理";

            default:
                return "PC";
        }
    }

    public static String getAppendRiskName(List<Integer> riskList) {
        StringBuffer sb = new StringBuffer();
        if(ObjectUtils.isEmpty(riskList)){
            sb.append("无");
            return sb.toString();
        }
        for(Integer risk: riskList){
            sb.append(convertRiskTypeName(risk));
            if(!riskList.get(riskList.size() - 1).equals(risk)){
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static String convertRiskTypeName(Integer riskType){
        if(ObjectUtils.isEmpty(riskType)){
            return "无";
        }
        switch (riskType) {
            case 0:
                return "正常提现";
            case 1:
                return "单日首提过大";
            case 2:
                return "单笔金额过大";
            case 3:
                return "当日金额过大";
            case 4:
                return "频繁提现";
            case 5:
                return "利润异常";
            case 6:
                return "7日累积金额过大";
            case 7:
                return "提现IP冲突";
            case 8:
                return "提现设备冲突";
            case 9:
                return "钱包负数";
            case 10:
                return "大额提现";
            case 11:
                return "新会员";
            case 12:
                return "首提金额过大";
            case 13:
                return "转账异常监控";
            default:
                return "无";
        }
    }

    public static String convertCheckStatusName(Integer status){
        if(ObjectUtils.isEmpty(status)){
            return "无";
        }
        switch (status) {
            case -4:
                return "风险审核搁置";
            case -3:
                return "风险待审核";
            case -2:
                return "风险审核拒绝";
            default:
                return "风险审核通过";
        }
    }

}
