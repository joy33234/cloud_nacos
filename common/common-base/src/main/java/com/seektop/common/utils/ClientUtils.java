package com.seektop.common.utils;

import com.seektop.constant.ProjectConstant;

import java.util.Optional;

public class ClientUtils {

    /**
     * 新活动娱乐端APP
     */
    private static final int NEW_FUN = 5;
    /**
     * 新活动体育端APP
     */
    private static final int NEW_SPORT = 6;

    /**
     * 老活动PC
     */
    private static final int OLD_PC = 0;

    /**
     * 老活动现金网
     */
    private static final int OLD_CASH = 1;
    /**
     * 老活动体育
     */
    private static final int OLD_SPORT = 2;


    /**
     * 获取新活动显示端
     * 显示端 0PC，1H5 ， 5娱乐，6体育 见头部常量
     */
    public static int getActivityType(Integer os_type, Integer app) {
        if (os_type == null) {
            os_type = 0;
        }
        if (app == null) {
            app = 0;
        }
        int productType = 0;
        if (os_type == ProjectConstant.OSType.PC) {
            productType = 0;
        } else if (os_type == ProjectConstant.OSType.H5) {
            productType = 1;
        }
        //现金网 娱乐端
        if (app == ProjectConstant.APPType.CASH) {
            if (os_type == ProjectConstant.OSType.IOS || os_type == ProjectConstant.OSType.ANDROID) {
                productType = 5;
            }
        } else if (app == ProjectConstant.APPType.SPORT) {
            if (os_type == ProjectConstant.OSType.IOS || os_type == ProjectConstant.OSType.ANDROID) {
                productType = 6;
            }
        }
        return productType;
    }

    /**
     * 兼容新老活动
     * 老活动显示端：0现金网PC，1现金网APP，2体育AP; 见头部常量
     */
    public static int compatible(int showClient) {
        if (showClient == NEW_FUN) {
            return OLD_CASH;
        }
        if (showClient == NEW_SPORT) {
            return OLD_SPORT;
        }
        return showClient;
    }

    /**
     * 产品端维护类型(1:会员-PC端,2:会员-IOS端,3:会员-ANDROID端,4:代理-PC端,5:代理-IOS端,6:代理-ANDROID端,7:体育-IOS端,8:体育-ANDROID端,9:会员-H5端)
     *             (10:代理-H5,11:体育-H5)
     * @param os_type -1ALL, 0PC，1H5，2安卓，3IOS，4PAD
     * @param app     -1ALL, 0现金网，1体育，2代理
     * @return int
     * @author Chaims
     * @date 2019/3/29 11:06
     */
    public static int getProductType(Integer os_type, Integer app) {
        if (os_type == null) {
            os_type = 0;
        }
        if (app == null) {
            app = 0;
        }
        int productType = 0;
        //娱乐/会员/现金网
        if (app == ProjectConstant.APPType.CASH||app == ProjectConstant.APPType.SPORT) {
            if (os_type == ProjectConstant.OSType.PC) {
                productType = 1;
            } else if (os_type == ProjectConstant.OSType.IOS) {
                productType = 2;
            } else if (os_type == ProjectConstant.OSType.ANDROID) {
                productType = 3;
            } else if (os_type == ProjectConstant.OSType.H5) {
                productType = 9;
            }
        } else if (app == ProjectConstant.APPType.PROXY) {
            if (os_type == ProjectConstant.OSType.PC) {
                productType = 4;
            } else if (os_type == ProjectConstant.OSType.IOS) {
                productType = 5;
            } else if (os_type == ProjectConstant.OSType.ANDROID) {
                productType = 6;
            }else if (os_type == ProjectConstant.OSType.H5){
                productType = 10;
            }
        }
        //体育和现金网合并，维护信息只算一个
//        else if (app == ProjectConstant.APPType.SPORT) {
//            if (os_type == ProjectConstant.OSType.IOS) {
//                productType = 7;
//            } else if (os_type == ProjectConstant.OSType.ANDROID) {
//                productType = 8;
//            }else if(os_type == ProjectConstant.OSType.H5){
//                productType = 11;
//            }
//        }
        return productType;
    }

    /**
     * 获取客户端逻辑类型
     * @param os_type
     * @param app
     * @return 显示端：0现金网PC，1现金网APP，2体育APP，3代理PC，4代理APP
     */
    public static int getShowClient(Integer os_type, Integer app) {
        int showClient = 0;
        if (app == ProjectConstant.APPType.CASH) {
            if (os_type == ProjectConstant.OSType.PC) {
                showClient = 0;
            } else {
                showClient = 1;
            }
        } else if (app == ProjectConstant.APPType.SPORT) {
//            showClient = 2;
            showClient = 1;//APP合并
        } else if (app == ProjectConstant.APPType.PROXY) {
            if (os_type == ProjectConstant.OSType.PC) {
                showClient = 3;
            } else {
                showClient = 4;
            }
        }
        return showClient;
    }
    /**
     * 获取客户端Name
     * @param os_type
     */
    public static String getOsName(Integer os_type) {
        switch (Optional.ofNullable(os_type).orElse(-1)){
            case ProjectConstant.OSType.PC: return "PC";
            case ProjectConstant.OSType.H5: return "H5";
            case ProjectConstant.OSType.ANDROID: return "ANDROID";
            case ProjectConstant.OSType.IOS: return "IOS";
            case ProjectConstant.OSType.PAD: return "PAD";
            default: return "未知";
        }
    }
}
