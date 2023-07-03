package com.seektop.common.nacos;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.seektop.dto.proxy.ProxyBackendLoginConfigDO;
import com.seektop.enumerate.ExportConfigEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class DynamicKey {

    public static class Key {
        /**
         * 产品端维护功能是否启用
         */
        public final static String PRODUCT_MAINTAIN_ENABLE = "PRODUCT_MAINTAIN_ENABLE";

        /**
         * 代理端是否开启多端登录
         */
        public final static String PROXY_MULTI_LOGIN_ENABLE = "PROXY_MULTI_LOGIN_ENABLE";

        /**
         * 会员端是否开启多端登录
         */
        public final static String MEMBER_MULTI_LOGIN_ENABLE = "MEMBER_MULTI_LOGIN_ENABLE";

        /**
         * 是否返回验证码
         */
        public final static String RETURN_CODE_ENABLE = "RETURN_CODE_ENABLE";

        /**
         * 是否启用NEC滑动验证码验证
         */
        public final static String NEC_VALIDATE_ENABLE = "NEC_VALIDATE_ENABLE";

        /**
         * 是否进行签名验证
         */
        public final static String SIGN_VALIDATE_ENABLE = "SIGN_VALIDATE_ENABLE";

        /**
         * 登录锁定解锁时跳过检查最后锁定记录
         */
        public final static String LOGIN_LOCKCHECK_SKIP_USERMANAGELOG = "LOGIN_LOCKCHECK_SKIP_USERMANAGELOG";

        /**
         * 邮件服务器配置
         */
        public final static String EMAIL_SMTP_CONFIG = "EMAIL_SMTP_CONFIG";

        /**
         * 应用名称
         */
        public final static String APP_NAME = "APP_NAME";

        /**
         * 游戏跳转路径配置
         */
        public final static String GAME_JUMP_CONFIG = "GAME_JUMP_CONFIG";

        /**
         * 直播推流平台配置参数
         */
        public final static String LIVE_CT_CONFIG = "LIVE_CT_CONFIG";

        /**
         * 直播间聊天室配置参数
         */
        public final static String LIVE_CHAT_CONFIG = "LIVE_CHAT_CONFIG";

        /**
         * 直播间晒单配置参数
         */
        public final static String LIVE_SHOW_CONFIG = "LIVE_SHOW_CONFIG";

        /**
         * 手机号码支持的国际区号
         */
        public final static String MOBILE_SUPPORT_AREA_CODE = "MOBILE_SUPPORT_AREA_CODE";

        /**
         * 是否开启黑名单检查
         */
        public final static String BLACKLIST_CHECK = "BLACKLIST_CHECK";

        /**
         * 后台用户登录-验证码 是否开启
         */
        public final static String OPEN_GOOGLE_AUTHENTICATOR = "OPEN_GOOGLE_AUTHENTICATOR";

        /**
         * IOS包,ICON下载路径
         */
        public final static String MEMBER_ICON_DISPLAY = "MEMBER_ICON_DISPLAY";

        public final static String MEMBER_ICON_FULL = "MEMBER_ICON_FULL";

        public final static String SPORT_ICON_DISPLAY = "SPORT_ICON_DISPLAY";

        public final static String SPROT_ICON_FULL = "SPROT_ICON_FULL";

        public final static String PROXY_ICON_DISPLAY = "PROXY_ICON_DISPLAY";

        public final static String PROXY_ICON_FULL = "PROXY_ICON_FULL";

        /**
         * IOS打包临时文件目录
         */
        public final static String TEMP_FILE_PATH = "TEMP_FILE_PATH";

        /**
         * SEO域名列表
         */
        public final static String SEO_DOMAIN_CONFIG = "SEO_DOMAIN_CONFIG";
        /**
         * SEO邀请
         */
        public final static String SEO_INVITE_AGENT_ID = "SEO_INVITE_AGENT_ID";

        /**
         * 黑名单检测配置
         */
        public final static String BLACK_LIST_CHECK_CONFIG = "BLACK_LIST_CHECK_CONFIG";

        /**
         * 主播工具管理员账号ID
         */
        public final static String HOST_TOOLS_ADMIN_ACCOUNT_ID = "HOST_TOOLS_ADMIN_ACCOUNT_ID";

        /**
         * 主播管理员是否允许发红包
         */
        public final static String HOST_ADMIN_SEND_RED_ENVELOPE = "HOST_ADMIN_SEND_RED_ENVELOPE";

        /**
         * 代理端Token复用
         */
        public final static String PROXY_TOKEN_REUSE_ENABLE = "PROXY_TOKEN_REUSE_ENABLE";

        /**
         * Aws 访问配置
         */
        public final static String AWS_S3_OSS_CONFIG = "AWS_S3_OSS_CONFIG";

        /**
         * 主播热度虚拟翻倍基数
         */
        public final static String HOST_HEAT_DOUBLE_RATE = "HOST_HEAT_DOUBLE_RATE";

        /**
         * 流失会员统计导出密码
         */
        public final static String USER_LOSE_EXPORT_PASSWORD = "USER_LOSE_EXPORT_PASSWORD";
        /**
         * 娱乐app下载包名
         */
        public final static String CASH_APP_FILE_NAME = "CASH_APP_FILE_NAME";

        /**
         * 体育app下载包名
         */
        public final static String SPORT_APP_FILE_NAME = "SPORT_APP_FILE_NAME";

        /**
         * 代理app下载包名
         */
        public final static String PROXY_APP_FILE_NAME = "PROXY_APP_FILE_NAME";

        /**
         * 代理自动生成会员帐号的前缀
         */
        public final static String PROXY_GENERATE_USERNAME_PREFIX = "PROXY_GENERATE_USERNAME_PREFIX";
        /**
         * 代理端报表菜单权限
         */
        public final static String PROXY_REPORT_MENUS = "PROXY_REPORT_MENUS";
        /**
         * 代理端展示部分数据权限
         */
        public final static String PROXY_HIDE_DATA_PERMISSION = "PROXY_HIDE_DATA_PERMISSION";

        /**
         * 域名检测账号配置
         */
        public final static String USER_DOMAIN_RATE_CHECK = "USER_DOMAIN_RATE_CHECK";

        /**
         * 代理账号是否可以在会员端登录
         */
        public final static String PROXY_LOGIN_IN_MEMBER_APP = "PROXY_LOGIN_IN_MEMBER_APP";

        /**
         * 代理登录需要排除易盾验证的账号名单
         */
        public final static String PROXY_LOGIN_EXCLUDE_NECAPTCHA = "PROXY_LOGIN_EXCLUDE_NECAPTCHA";

        /**
         * 用户异常设备登录检查开关
         */
        public final static String USER_ABNORMAL_DEVICE_LOGIN_CHECK = "USER_ABNORMAL_DEVICE_LOGIN_CHECK";

        /**
         * 导出数据限制配置
         */
        public final static String EXPORT_LIMIT_CONFIG = "EXPORT_LIMIT_CONFIG";

        /**
         * 获取火币网USDT汇率买入地址
         */
//        public final static String HUOBI_USDT_URL_BUY = "HUOBI_USDT_URL_BUY";
        /**
         * 获取火币网USDT汇率买出地址
         */
//        public final static String HUOBI_USDT_URL_SELL = "HUOBI_USDT_URL_SELL";

        /**
         * 获取欧易USDT汇率地址
         */
        public final static String OKEX_USDT_URL = "OKEX_USDT_URL";

        /**
         * 易盾设备号参数配置
         */
        public final static String NE_DEVICE_PARAM_CONFIG = "NE_DEVICE_PARAM_CONFIG";

        /**
         * redis 是否读写分离
         */
        public final static String REDIS_RW_SEPARATION = "REDIS_RW_SEPARATION";

        /**
         * 代理SEO后台登陆相关配置
         */
        public final static String PROXY_BACKEND_LOGIN_CONFIG = "PROXY_BACKEND_LOGIN_CONFIG";

        /**
         * IP138解析配置
         */
        public final static String IP_138_CONFIG = "IP_138_CONFIG";

        /**
         * C2C充提相关配置
         */
        public final static String C2C_CONFIG = "C2C_CONFIG";

        /**
         * C2C充值彩蛋活动Id
         */
        public final static String C2C_EASTER_EGG_RECHARGE_ACTID = "C2C_EASTER_EGG_RECHARGE_ACTID";
        /**
         * C2C提现彩蛋活动Id
         */
        public final static String C2C_EASTER_EGG_WITHDRAW_ACTID = "C2C_EASTER_EGG_WITHDRAW_ACTID";

        /**
         * 用户识别码有效期(单位：秒)
         */
        public final static String USER_VALID_CODE_TTL = "USER_VALID_CODE_TTL";

        /**
         * 游戏IM是否开启原生
         */
        public final static String IM_SPORTS_ENABLE_NATIVE = "IM_SPORTS_ENABLE_NATIVE";

        /**
         * IM体育缓存是否自动刷新
         */
        public final static String IM_SPORTS_CACHE_ENABLE_AUTO_REFRESH = "IM_SPORTS_CACHE_ENABLE_AUTO_REFRESH";

        /**
         * 游戏IM是否开启原生
         */
        public final static String IM_SPORTS_ENABLE_NATIVE_VERSION = "IM_SPORTS_ENABLE_NATIVE_VERSION";

        /**
         * 游戏OB SPORT是否开启原生
         */
        public final static String OB_SPORTS_ENABLE_NATIVE_VERSION = "OB_SPORTS_ENABLE_NATIVE_VERSION";

        /**
         * 删除银行卡需要的VIP等级
         */
        public final static String USER_BANKCARD_DELETE_VIP_LEVEL = "USER_BANKCARD_DELETE_VIP_LEVEL";

        /**
         * 中心钱包ICON访问域名
         */
        public final static String SHARE_WALLET_COIN_DOMAIN = "SHARE_WALLET_COIN_DOMAIN";

        /**
         * 根据dsl导出文件
         */
        public final static String EXPORT_ES_DATA_DSL_INFO = "EXPORT_ES_DATA_DSL_INFO";

        /**
         * 赛事开始和推送的极光推送是否打开
         */
        public final static String MATCH_EVENT_JPUSH_ENABLE = "MATCH_EVENT_JPUSH_ENABLE";
        /**
         * 极速体育三方app-api域名
         */
        public final static String ST_SPORT_API_URL = "ST_SPORT_API_URL";
    }

    private final static String dataId = "dynamic-key";

    @Autowired
    private NacosConfigProperties properties;

    private Map<String, Object> dynamicMap = new HashMap<>();

    /**
     * 是否返回验证码到客户端
     *
     * @return
     */
    public Boolean isReturnCode() {
        return getDynamicValue(Key.RETURN_CODE_ENABLE, Boolean.class);
    }

    /**
     * 获取导出数据限制
     *
     * @param exportConfigEnum
     * @return
     */
    public long getExportLimit(ExportConfigEnum exportConfigEnum) {
        JSONObject configObj = getDynamicValue(Key.EXPORT_LIMIT_CONFIG, JSONObject.class);
        if (ObjectUtils.isEmpty(configObj)) {
            return 0;
        }
        if (configObj.containsKey(exportConfigEnum.getConfigName()) == false) {
            return 0;
        }
        return configObj.getLong(exportConfigEnum.getConfigName());
    }

    /**
     * 代理是否允许在会员端登录
     *
     * @return
     */
    public boolean checkProxyLoginInMemberApp() {
        if (dynamicMap.containsKey(Key.PROXY_LOGIN_IN_MEMBER_APP) == false) {
            return false;
        }
        return getDynamicValue(Key.PROXY_LOGIN_IN_MEMBER_APP, Boolean.class);
    }

    /**
     * 检查是否是SEO域名
     *
     * @param domain
     * @return
     */
    public boolean checkSeoDomain(final String domain) {
        JSONArray array = getDynamicValue(Key.SEO_DOMAIN_CONFIG, JSONArray.class);
        if (CollectionUtils.isEmpty(array)) {
            return false;
        } else {
            return array.contains(domain);
        }
    }
    /**
     * 获取SEO 邀请代理
     *
     * @return
     */
    public Integer getSeoInviteAgentId() {
        return getDynamicValue(Key.SEO_INVITE_AGENT_ID, Integer.class);
    }

    /**
     * 系统支持的国际区号
     *
     * @param telArea
     * @return
     */
    public boolean checkSupportAreaCode(final String telArea) {
        JSONArray array = getDynamicValue(Key.MOBILE_SUPPORT_AREA_CODE, JSONArray.class);
        if (CollectionUtils.isEmpty(array)) {
            return true;
        } else {
            return array.contains(telArea);
        }
    }

    /**
     * 获取请求的URL
     *
     * @return
     */
    public JSONObject getIp138Config() {
        return getDynamicValue(Key.IP_138_CONFIG, JSONObject.class);
    }

    /**
     * 获取AppName
     *
     * @return
     */
    public String getAppName() {
        return getDynamicValue(Key.APP_NAME, String.class);
    }

    /**
     * 获取配置设置的值
     *
     * @param key
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T getDynamicValue(final String key, Class<T> clazz) {
        if (dynamicMap.containsKey(key) == false) {
            return null;
        }
        Object obj = dynamicMap.get(key);
        if (ObjectUtils.isEmpty(obj)) {
            return null;
        }
        return clazz.cast(obj);
    }

    public Boolean redisRwSeparation(){
        Boolean dynamicValue = getDynamicValue(Key.REDIS_RW_SEPARATION, Boolean.class);
        if(null == dynamicValue){
            return true;
        }else {
            return dynamicValue;
        }
    }

    public ProxyBackendLoginConfigDO getProxyLoginConfig(){
        JSONObject dynamicValue = getDynamicValue(Key.PROXY_BACKEND_LOGIN_CONFIG, JSONObject.class);
        if(null == dynamicValue){
            return new ProxyBackendLoginConfigDO();
        }
        return dynamicValue.toJavaObject(ProxyBackendLoginConfigDO.class);
    }
    @PostConstruct
    private void init() {
        try {
            ConfigService configService = NacosFactory.createConfigService(properties.getServerAddr());
            configService.addListener(dataId, properties.getGroup(), new DataSourceListener());
            String source = configService.getConfig(dataId, properties.getGroup(), properties.getTimeout());
            setDataSource(source);
        } catch (NacosException e) {
            log.error("DynamicKey.init()", e);
        }
    }

    class DataSourceListener implements Listener {

        @Override
        public Executor getExecutor() {
            return null;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            setDataSource(configInfo);
        }

    }


    private void setDataSource(final String source) {
        if (StringUtils.isEmpty(source)) {
            return;
        }
        JSONArray dataArray = JSON.parseArray(source);
        synchronized (dynamicMap) {
            dynamicMap.clear();
            for (int i = 0, len = dataArray.size(); i < len; i++) {
                JSONObject dataObj = dataArray.getJSONObject(i);
                if (dataObj.containsKey("key") == false) {
                    continue;
                }
                String key = dataObj.getString("key");
                dynamicMap.put(key, dataObj.get("value"));
            }
        }
    }

    public Integer getC2cRechargeEasterEggActId(){
        return getDynamicValue(Key.C2C_EASTER_EGG_RECHARGE_ACTID, Integer.class);
    }
    public Integer getC2cWithdrawEasterEggActId(){
        return getDynamicValue(Key.C2C_EASTER_EGG_WITHDRAW_ACTID, Integer.class);
    }
}