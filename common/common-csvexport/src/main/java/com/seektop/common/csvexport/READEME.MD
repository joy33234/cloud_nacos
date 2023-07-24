### 使用方式
在Application入口添加注解 @EnAbleExportUtils
### 使用案例

1. 推荐使用 一个字段出错，不影响整行

```
List<FieldMap> fieldMaps = Arrays.asList(
                // <T> 是需要导出字段的类型，指定泛型后，使用parse比较方便
                FieldMap.<Date>builder()
                        // 需要导出字段的名字
                        .fieldName("createTime")
                        // 列标题
                        .fieldTitle("转账时间")
                        // 对该字段的值做一些操作，转化等
                        .parse(v -> {
                            // 做一些数据转换的操作
                            return DateUtils.format(v, DateUtils.YYYY_MM_DD_HH_MM_SS);
                        })
                        .build(),
                // 如果不需要转换数据，不写parse
                FieldMap.<Integer>builder().fieldName("status").fieldTitle("状态")
                        .build()
        );
        Export.builder()
                .title("导出转账记录列表")
                // 分页的大小
                .pageSize(5000)
                // 导出总条数
                .total(200000)
                // 分页一次的间隔时间 毫秒
                .interval(200)
                // 管理员id
                .userId(userId)
                // 锁名称
                .fileType("TRANSFER_LIST_DOWNLOAD_LOCK_")
                // 需要导出的字段map
                .fieldMaps(fieldMaps)
                .build()
                // 执行导出 
                .doExport((page,size)-> this.findGameTransferList(dateType, startTime, endTime, minAmount, maxAmount, status,
                        // page size执行导出的时候，动态刷的
                        fromChannel, toChannel, username, idList, tradeId, page, size));
```

2. 其他使用案例
```
 Export.builder()
        .title("交易明细报表")
        // 表头 private final static String transferHeaders = "交易号,账户名,账户类型,所属代理,交易类型,细分类型,操作动";
        .header(transferHeaders)
        .pageSize(5000)
        .total(100000)
        .interval(200)
        .token(token)
        .fileType("TRANSFER_DETAIL_DOWNLOAD_LOCK_")
        .build()
        .doExport(
                // 查询
                (page,size)->{
                    UserBalanceResult transactionHistory=userBalanceService.findTransactionHistory(userId, startTime, endTime, userName, orderId, parentId, orderType, status, page, size, timeRangeType, orderBy, sort.name().toLowerCase(), subType, userType, null,betId); 
                    transactionHistory.getList();			
                    PageInfo<com.betball.data.integration.dto.user.UserBalanceLog> pageInfo = new PageInfo<>(transactionHistory.getList());
                    pageInfo.setTotal(transactionHistory.getTotal());
                    return pageInfo;
                },
                // 数据转换
                item->{
                    StringBuffer sb = new StringBuffer();
                    // 交易号
                    sb.append(item.getOrderId());
                    // 手动拼接逗号
                    sb.append(",");
                }
```