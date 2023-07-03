package com.seektop.common.elasticsearch.base;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONValidator;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.seektop.common.elasticsearch.dto.BaseQueryDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.DoubleTerms;
import org.elasticsearch.search.aggregations.bucket.terms.InternalTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;

@Slf4j
public abstract class ElasticsearchBaseDao {

    @Autowired
    public RestHighLevelClient esClient;

    // 缁便垹绱╅崥宥囆�
    protected final static String INDEX = "report-*";
    public final static String WITHDRAW = "report-withdraw-20*";
    public final static String WITHDRAW_RETURN = "report-withdraw-return-*";
    public final static String WITHDRAW_PARENT = "report-withdraw-parent-order-*";
    public final static String UP_AMOUNT = "report-up-amount-*";
    public final static String UP_AMOUNT_BALANCE = "report-up-amount-balance-*";
    public final static String UP_AMOUNT_GRANT = "report-up-amount-grant-*";
    public final static String SUB_AMOUNT = "report-sub-amount-*";
    public final static String TRANSFER = "report-transfer-*";
    //b项目放弃 SUB_COIN 请用新的 DIGITAL_SUB_COIN
    @Deprecated()
    public final static String SUB_COIN = "report-sub-coin-*";
    public final static String DIGITAL_SUB_COIN = "report-digital-subtract-coin*";
    public final static String REGISTER = "report-register-*";
    public final static String RECHARGE = "report-recharge-*";
    public final static String REBATE = "report-rebate-*";
    public final static String ONLINE_COUNT = "report-online-count-*";
    public final static String LOGIN = "report-login-*";
    public final static String GAME_USER_BALANCE = "report-game-user-balance-*";
    public final static String BONUS = "report-bonus-*";
    public final static String BETTING = "report-betting-2*";
    public final static String BETTING_BALANCE = "report-betting-balance-*";
    //b项目放弃 ADD_COIN 请用新的 DIGITAL_ADD_COIN
    @Deprecated()
    public final static String ADD_COIN = "report-add-coin-*";
    public final static String DIGITAL_ADD_COIN = "report-digital-add-coin*";
    public final static String USER = "gl_user";
    public final static String USER_BALANCE_REPORT = "report-user-balance-collect-20*";
    public final static String GL_USER_OPERATION = "gl_user_operation";
    public final static String BALANCE_RECORD = "report-balance-record-20*";
    public final static String VIP_RECORD = "report-vip-points-20*";
    public final long MC_THRESHOLD = 500000000;
    public final String YYYY_MM = "yyyy/MM";
    public final TimeZone SHANGHAITZ = TimeZone.getTimeZone("Asia/Shanghai");
    // 缂佺喕顓搁崺鐑樻殶缁儳瀹冲ù瀣槸閻滎垰顣ㄩ崣锟�1000,閻㈢喍楠囬悳顖氼暔閸欐牠绮拋銈咃拷锟�
    public final static int precisionThreshold = 3000;
    // 閺冭泛灏�
    public final static ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");
    // 閺冦儲婀￠弫蹇斿妳閻ㄥ嚍ateHistogramInterval
    public static List<DateHistogramInterval> calendarIntervals = Arrays.asList(DateHistogramInterval.MONTH, DateHistogramInterval.WEEK, DateHistogramInterval.YEAR);

    public InternalTerms<?, ?> EMPTY_LONGTERMS = new DoubleTerms(null, null, 0, 0L, Collections.emptyList(), null, null, 0, false, 0L, Collections.emptyList(), 0);
    public DateHistogramAggregationBuilder dateHistogram(String name, DateHistogramInterval interval) {
        DateHistogramAggregationBuilder result = AggregationBuilders.dateHistogram(name);
        if (interval != null) {
            if (calendarIntervals.contains(interval)) {
                result.calendarInterval(interval);
            } else {
                result.fixedInterval(interval);
            }
        }
        return result;
    }
    /**
     * 閸旂喕鍏橀幓蹇氬牚: 閺嶈宓侀弮鍫曟？閼惧嘲褰噄ndexName
     */
    public String getIndexByEvent(int event,long stime,long etime) {
        return "";
    }

    /**
     * 閸╄櫣顢呴惃鍕叀鐠囥垺娼禒锟�
     *
     * @param baseQueryDto
     * @return
     */
    public List<QueryBuilder> getBaseQuerBuilders(BaseQueryDto baseQueryDto) {
        return getBaseQuerBuilders(baseQueryDto,"timestamp");
    }
    /**
     * 閸╄櫣顢呴惃鍕叀鐠囥垺娼禒锟�
     *
     * @param baseQueryDto
     * @return
     */
    public List<QueryBuilder> getBaseQuerBuilders(BaseQueryDto baseQueryDto,boolean buildCoin) {
        return getBaseQuerBuilders(baseQueryDto,"timestamp",buildCoin);
    }

    /**
     * 鐠佸墽鐤嗘妯款吇Time
     *
     * @param dto
     * @return
     */
    public BaseQueryDto initBaseQueryDtoTime(BaseQueryDto dto,long stime,long etime,boolean compelInit) {
        dto = Optional.ofNullable(dto).orElseGet(BaseQueryDto::new);
        if (compelInit){
            dto.setStime(stime);
            dto.setEtime(etime);
        } else {
            dto.putStartTimeIfNull(stime).putEndTimeIfNull(etime);
        }
        return dto;
    }

    /**
     * 閸╄櫣顢呴惃鍕叀鐠囥垺娼禒锟�
     *
     * @param dto
     * @return
     */
    public List<QueryBuilder> getBaseQuerBuilders(BaseQueryDto dto, String timeField) {
        return getBaseQuerBuilders(dto,timeField,false);
    }

    /**
     * 閸╄櫣顢呴惃鍕叀鐠囥垺娼禒锟�
     *
     * @param dto
     * @return
     */
    public List<QueryBuilder> getBaseQuerBuilders(BaseQueryDto dto, String timeField,boolean buildCoin) {
        dto = Optional.ofNullable(dto).orElseGet(BaseQueryDto::new);
        List<QueryBuilder> builders = new ArrayList<>();
        timeField = Optional.ofNullable(timeField).orElse("timestamp");
        builders.add(new RangeQueryBuilder(timeField).from(dto.getStime(0)).to(dto.getEtime(System.currentTimeMillis())));
        if (dto.getIsFake() >= 0) {
            builders.add(new TermQueryBuilder("isFake", dto.getIsFake()));
        }
        if (buildCoin&&StringUtils.isNotBlank(dto.getCoinCode())&&!("-1".equals(dto.getCoinCode()))) {
            builders.add(new TermQueryBuilder("coin", dto.getCoinCode()));
        }
        return builders;
    }
    /**
     * 基础条件构造
     *
     * @param dto
     * @return
     */
    public List<QueryBuilder> getBaseQuerBuilders(BaseQueryDto dto, String timeField,Collection<String> coin) {
        List<QueryBuilder> builders = getBaseQuerBuilders(dto,timeField);
        if (coin!=null&&coin.size()>0){
            builders.add(QueryBuilders.termsQuery("coin",coin));
        }
        return builders;
    }

    /**
     * 基础条件构造
     *
     * @param dto
     * @return
     */
    public List<QueryBuilder> getBaseQuerBuilders(BaseQueryDto dto, Collection<String> coin) {
        return getBaseQuerBuilders(dto,null,coin);
    }
    /**
     * 閸╄櫣顢呴惃鍕叀鐠囥垺娼禒锟�
     *
     * @param dto
     * @return
     */
    public RangeQueryBuilder getRegTimeQueryBuilders(BaseQueryDto dto, String timeField) {
        dto = Optional.ofNullable(dto).orElseGet(BaseQueryDto::new);
        timeField = Optional.ofNullable(timeField).orElse("regTime");
        return new RangeQueryBuilder(timeField).from(dto.getRegStartTime(0)).to(dto.getRegEndTime(System.currentTimeMillis()));
    }

    /**
     * 閺冨爼妫跨捄銊ュ閺勵垰鎯侀幐澶娿亯
     *
     * @param dto
     * @return
     */
    public boolean isByDay(BaseQueryDto dto) {
        return dto.getEtime() - dto.getStime() > 86399999;
    }

    /**
     * 婢舵矮閲滈弶鈥叉閸氬牆鑻�
     *
     * @param queryBuilders
     * @param queryBuilder
     * @return
     */
    public List<QueryBuilder> mergeQueryBuilder(List<QueryBuilder> queryBuilders, QueryBuilder... queryBuilder) {
        List<QueryBuilder> builders = queryBuilders == null ? new ArrayList<>() : new ArrayList<>(queryBuilders);
        Arrays.stream(Optional.ofNullable(queryBuilder).orElse(new QueryBuilder[0])).filter(Objects::nonNull).forEach(builders::add);
        return builders;
    }

    public List<Integer> getIdsByUserName(String name){
        List<Integer> result = Lists.newArrayList();
        if (org.apache.commons.lang3.StringUtils.isBlank(name)) return result;
        try {
            SearchHit[] hits = esClient.search(new SearchRequest().indices("gl_user").source(SearchSourceBuilder
                            .searchSource()
                            .query(QueryBuilders.termQuery("username.lower",name.trim().toLowerCase()))
                            .size(100)
                            .from(0)
                            .fetchSource(new String[]{"id"}, null)
            ), RequestOptions.DEFAULT).getHits().getHits();
            for (SearchHit hit : hits) {
                result.add(getValFromSearchHit(hit,"id",0));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
    public List<Integer> getIdsByWildcardUserName(String name){
        List<Integer> result = Lists.newArrayList();
        if (org.apache.commons.lang3.StringUtils.isBlank(name)) return result;
        try {
            SearchHit[] hits = esClient.search(new SearchRequest().indices("gl_user").source(SearchSourceBuilder
                            .searchSource()
                            .query(QueryBuilders.wildcardQuery("username.lower","*" + name.trim().toLowerCase() + "*"))
                            .size(100)
                            .from(0)
                            .fetchSource(new String[]{"id"}, null)
            ), RequestOptions.DEFAULT).getHits().getHits();
            for (SearchHit hit : hits) {
                result.add(getValFromSearchHit(hit,"id",0));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 閺嶈宓侀弮鍫曟？閸掓繂顫愰崠锟�
     *
     * @return
     */
    public <T extends InitMapAble> Map<String, T> initMap(Long stime, Long etime, String pattern, int minuteStep, Class<T> tClass) {
        Map<String, T> resultMap = new TreeMap<>();
        for (DateTime i = new DateTime(etime); i.getMillis() >= stime; i = i.minusMinutes(minuteStep)) {
            try {
                T value = tClass.newInstance();
                String key = i.toString(pattern);
                value.setDate(key);
                resultMap.put(key, value);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return resultMap;
    }

    public <V> V getValFromSearchHit(SearchHit hitField, String fieldName, V defaultVal) {
        if (org.apache.commons.lang3.StringUtils.isNotBlank(fieldName) && hitField != null && hitField.hasSource()) {
            String[] split = fieldName.split("\\.");
            Map<String, Object> sourceAsMap = hitField.getSourceAsMap();
            if (split.length > 1) {
                for (int i = 0; i < split.length - 1; i++) {
                    sourceAsMap = (Map<String, Object>) sourceAsMap.getOrDefault(split[i], Collections.emptyMap());
                }
            }
            Object orDefault = sourceAsMap.getOrDefault(split[split.length - 1], defaultVal);
            if (orDefault==null) return (V) orDefault;
            if (defaultVal instanceof String) return (V) orDefault.toString();
            if (defaultVal instanceof Integer) return (V) (Integer)((Number)orDefault).intValue();
            if (defaultVal instanceof Long) return (V) (Long)((Number)orDefault).longValue();
            if (defaultVal instanceof Double) return (V) (Double)((Number)orDefault).doubleValue();
            if (defaultVal instanceof Short) return (V) (Short)((Number)orDefault).shortValue();
            if (defaultVal instanceof Byte) return (V) (Byte)((Number)orDefault).byteValue();
            if (defaultVal instanceof Float) return (V) (Float)((Number)orDefault).floatValue();
            return (V) orDefault;
        }
        return defaultVal;
    }

    public <V> V getValFromSearchHit(SearchHit hitField, String fieldName, V defaultVal, Class<V> type) {
        if (org.apache.commons.lang3.StringUtils.isNotBlank(fieldName) && hitField != null && hitField.hasSource()) {
            String[] split = fieldName.split("\\.");
            Map<String, Object> sourceAsMap = hitField.getSourceAsMap();
            if (split.length > 1) {
                for (int i = 0; i < split.length - 1; i++) {
                    sourceAsMap = (Map<String, Object>) sourceAsMap.getOrDefault(split[i], Collections.emptyMap());
                }
            }
            Object orDefault = sourceAsMap.getOrDefault(split[split.length - 1],defaultVal);
            if (orDefault==null) return (V) orDefault;
            if (type == String.class) return (V) orDefault.toString();
            if (type == Integer.class) return (V) (Integer)((Number)orDefault).intValue();
            if (type == Long.class) return (V) (Long)((Number)orDefault).longValue();
            if (type == Double.class) return (V) (Double)((Number)orDefault).doubleValue();
            if (type == Short.class) return (V) (Short)((Number)orDefault).shortValue();
            if (type == Byte.class) return (V) (Byte)((Number)orDefault).byteValue();
            if (type == Float.class) return (V) (Float)((Number)orDefault).floatValue();
            return (V) orDefault;
        }
        return defaultVal;
    }

    public <V> List<V> getListValFromSearchHit(SearchHit hitField, String fieldName, Class<V> vClass) {
        if (org.apache.commons.lang3.StringUtils.isNotBlank(fieldName) && hitField != null && hitField.hasSource()) {
            String[] split = fieldName.split("\\.");
            Map<String, Object> sourceAsMap = hitField.getSourceAsMap();
            if (split.length > 1) {
                for (int i = 0; i < split.length - 1; i++) {
                    sourceAsMap = (Map<String, Object>) sourceAsMap.getOrDefault(split[i], Collections.emptyMap());
                }
            }
            Object orDefault = sourceAsMap.get(split[split.length - 1]);
            if (orDefault == null) return Collections.EMPTY_LIST;
            List<V> result = JSON.parseArray(JSON.toJSONString(orDefault), vClass);
            return result;
        }
        return Collections.EMPTY_LIST;
    }

    public List<KVResult<String, BigDecimal>> getAmountFromAggregations(Aggregations aggregations) {
        List<KVResult<String,BigDecimal>> result = new ArrayList<>();
        Optional.ofNullable(aggregations).orElse(InternalAggregations.EMPTY).forEach(aggregation -> {
            for (Terms.Bucket bucket : ((Terms) aggregation).getBuckets()) {
                String key = bucket.getKeyAsString();
                KVResult<String,BigDecimal> e = new KVResult<>();
                e.setKey(key);
                for (Aggregation agg:bucket.getAggregations().asList()) {
                    if (agg instanceof Sum) {
                        e.setVal(dealMoney(((Sum) agg).getValue()));
                    } else if (agg instanceof NumericMetricsAggregation.SingleValue) {
                        e.setVal(BigDecimal.valueOf(((NumericMetricsAggregation.SingleValue)agg).value()));
                    }
                }
                result.add(e);
            }
        });
        return result;
    }

    public List<KVResult<String,Long>> getLongFromAggregations(Aggregations aggregations){
        return getAmountFromAggregations(aggregations).stream().map(e -> {
            KVResult<String,Long> kvResult = new KVResult();
            kvResult.setKey(e.getKey());
            kvResult.setVal(e.getVal().longValue());
            return kvResult;
        }).collect(Collectors.toList());
    }

    /**
     * 闁叉垿顤傞弨鎯с亣10000
     *
     * @param val
     * @return
     */
    public BigDecimal multiplyMoney(BigDecimal val) {
        return Optional.ofNullable(val).orElse(BigDecimal.ZERO).movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE);
    }

    /**
     *
     * @param val
     * @return
     */
    public BigDecimal dealMoney(BigDecimal val) {
        return dealMoney(val,null);
    }

    /**
     *
     * @param val
     * @return
     */
    public BigDecimal dealMoney(BigDecimal val,String coin) {
        return Optional.ofNullable(val).orElse(BigDecimal.ZERO).movePointLeft(DIGITAL_REPORT_MULTIPLY_SCALE)
                .setScale("CNY".equals(coin)?4:8,RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /**
     *
     * @param val
     * @return
     */
    public BigDecimal dealMoney(double val) {
        if (Double.isInfinite(val)) return BigDecimal.ZERO;
        return dealMoney(val,null);
    }
    /**
     *
     * @param val
     * @return
     */
    public BigDecimal dealMoney(double val,String coin) {
        if (Double.isInfinite(val)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(val).movePointLeft(DIGITAL_REPORT_MULTIPLY_SCALE)
                .setScale("CNY".equals(coin)?4:8,RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public String getUserNameById(Integer userId){
        String result = null;
        if (userId ==  null) return result;
        try {
            SearchHit[] hits = esClient.search(new SearchRequest().indices("gl_user").source(SearchSourceBuilder
                            .searchSource()
                            .query(QueryBuilders.termQuery("id",userId))
                            .size(100)
                            .from(0)
                            .fetchSource(new String[]{"id","username"}, null)
            ), RequestOptions.DEFAULT).getHits().getHits();
                return getValFromSearchHit(hits[0],"username","");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    public static DateTimeZone SHANGHAI_ZONE = DateTimeZone.forOffsetHours(8);
    public static DateTime getDateTimeFromBucket(Histogram.Bucket bucket) {
        if (bucket==null) return null;
        if(bucket.getKey() instanceof DateTime) return ((DateTime) bucket.getKey()).withZone(SHANGHAI_ZONE);
        String key = bucket.getKeyAsString();
        if (StringUtils.isNumeric(key)){
            return new DateTime(Long.parseLong(key),SHANGHAI_ZONE);
        } else {
            return new DateTime(key,SHANGHAI_ZONE);
        }
    }
    public List<String> getAllIndices(String... indices){
        List<String> result = new ArrayList<>();
        if (indices == null|| indices.length == 0) indices = new String[]{"*"};
        try {
            result.addAll(Arrays.asList(esClient.indices().get(new GetIndexRequest(indices), RequestOptions.DEFAULT).getIndices()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static final XContent JSON_CONTENT = XContentFactory.xContent(XContentType.JSON);
    public static final NamedXContentRegistry NAMED_X_CONTENT_REGISTRY = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, false, Collections.emptyList()).getNamedXContents());
    public static final DeprecationHandler DEPRECATION_HANDLER = new DeprecationHandler() {
        public void usedDeprecatedName(String usedName, String modernName) {}
        public void usedDeprecatedField(String usedName, String replacedWith) {}
    };
    public String searchEs(String dsl,List<String> indices){
        String result = "{}";
        try {
            if (!JSONValidator.from(dsl).validate()&& ObjectUtils.isEmpty(indices)) return "{'error':'dsl语句错误!'}";

            XContentParser parser = JSON_CONTENT.createParser(NAMED_X_CONTENT_REGISTRY,DEPRECATION_HANDLER, dsl);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.parseXContent(parser);
            SearchResponse response = esClient.search(new SearchRequest().indices(indices.toArray(new String[indices.size()]))
                    .source(searchSourceBuilder), RequestOptions.DEFAULT);
            result = response.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
    public ZoneOffset timeZone = ZoneOffset.ofHours(8);
    public int range(LocalDate inputTime,LocalDate currentTime) {
		long days = inputTime.until(currentTime, ChronoUnit.DAYS);
		//≥30  30 ；≥15 15 ；≥7 7
		return days>=30?30:(days>=15?15:(days>=7?7:0));
	}
    /**
     * 半衰减判断（上周指当前时间向前推7天）
     * 投注衰减：上周投注额比上上周投注衰减50%且上周投注额大于2000元
     * 充值衰减：上周充值额比上上周充值衰减50%且上周充值额大于2000元
     * @param lte7daysAmount 小于等于7天金额 （上上周）
     * @param gt7daysAmount 大于7天金额（上周）
     * @param thresholdAmount 阀值金额
     * @return uAmount====>UserAmountInfo(uid=13290, lte7DaysAmount=31654, gt7DaysAmount=10496)
     */
	public boolean isHalfDeacayUser(BigDecimal lte7daysAmount, BigDecimal gt7daysAmount, BigDecimal thresholdAmount) {
		if (!ObjectUtils.isEmpty(lte7daysAmount) && !ObjectUtils.isEmpty(gt7daysAmount)
				&& lte7daysAmount.compareTo(BigDecimal.ZERO) > 0 && gt7daysAmount.compareTo(BigDecimal.ZERO) > 0) {
			if (lte7daysAmount.compareTo(gt7daysAmount) > 0
					&& lte7daysAmount.subtract(gt7daysAmount).divide(lte7daysAmount, 4, RoundingMode.CEILING)
							.compareTo(BigDecimal.valueOf(0.5)) > 0
					&& gt7daysAmount.compareTo(BigDecimal.valueOf(2000)) > 0) {
				return true;
			}
		}
		return false;
	}

    public SearchRequest getSearchRequestFromDsl(String dsl,Collection<String> indices){
        SearchRequest result = null;
        if (!JSONValidator.from(dsl).validate()&&ObjectUtils.isEmpty(indices)) return result;
        try {
            XContentParser parser = JSON_CONTENT.createParser(NAMED_X_CONTENT_REGISTRY,DEPRECATION_HANDLER, dsl);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.parseXContent(parser);
            result = new SearchRequest().indices(indices.toArray(new String[indices.size()]))
                    .source(searchSourceBuilder);
        } catch (Exception e) {
            log.error("getSearchRequestFromDsl",e);
            log.error("getSearchRequestFromDsl-DSL:{},indices:{}",dsl,indices);
        }
        return result;
    }

    public SearchHits getHitsResult(SearchRequest request){
        SearchHits hits = null;
        try {
            hits = esClient.search(request, RequestOptions.DEFAULT).getHits();
        } catch (Exception e) {
            log.error("getHitsResult",e);
            log.error("getHitsResult-DSL:{}",request.source().toString());
        }
        return hits;
    }

    public Aggregations getAggregationsResult(SearchRequest request){
        Aggregations agg = null;
        try {
            agg = esClient.search(request, RequestOptions.DEFAULT).getAggregations();
        } catch (Exception e) {
            log.error("getAggregationsResult",e);
            log.error("getAggregationsResult-DSL:{}",request.source().toString());
        }
        return agg;
    }
}