package com.seektop.common.utils;

import java.math.BigDecimal;

public class RedEnvelopeUtils {

    /**
     * 红包的最小金额
     */
    private final BigDecimal minMoney = BigDecimal.valueOf(0.01);

    /**
     * 随机生成红包
     *
     * @param amount    红包总金额
     * @param count     红包总数
     * @return
     */
    public BigDecimal[] getRandomRedEnvelope(BigDecimal amount, Integer count) {
        RedPackage redPackage = new RedPackage();
        redPackage.remainMoney = amount;
        redPackage.remainSize = count;
        BigDecimal[] result = new BigDecimal[count];
        for (int i = 0; i < count; i++) {
            result[i] = getRandomMoney(redPackage);
        }
        return result;
    }

    private BigDecimal getRandomMoney(RedPackage redPackage) {
        // 如果只剩最后一个，直接返回剩余金额
        if (redPackage.remainSize == 1) {
            redPackage.remainSize--;
            return redPackage.remainMoney.setScale(2, BigDecimal.ROUND_DOWN);
        }
        BigDecimal random = BigDecimal.valueOf(Math.random());

        BigDecimal halfRemainSize = BigDecimal.valueOf(redPackage.remainSize).divide(new BigDecimal(2), BigDecimal.ROUND_UP);
        BigDecimal max1 = redPackage.remainMoney.divide(halfRemainSize, BigDecimal.ROUND_DOWN);
        BigDecimal minRemainAmount = minMoney.multiply(BigDecimal.valueOf(redPackage.remainSize - 1)).setScale(2, BigDecimal.ROUND_DOWN);
        BigDecimal max2 = redPackage.remainMoney.subtract(minRemainAmount);
        BigDecimal max = (max1.compareTo(max2) < 0) ? max1 : max2;

        BigDecimal money = random.multiply(max).setScale(2, BigDecimal.ROUND_DOWN);
        money = money.compareTo(minMoney) < 0 ? minMoney: money;

        redPackage.remainSize--;
        redPackage.remainMoney = redPackage.remainMoney.subtract(money).setScale(2, BigDecimal.ROUND_DOWN);;
        return money;
    }

    public class RedPackage {
        Integer remainSize;
        BigDecimal remainMoney;
    }

    private static final RedEnvelopeUtils instance = new RedEnvelopeUtils();

    private RedEnvelopeUtils() {

    }

    public static RedEnvelopeUtils getInstance() {
        return instance;
    }

}