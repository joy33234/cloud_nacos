
drop table if exists okx_setting;
create table okx_setting (
  Setting_id         int(5)          not null auto_increment    comment '参数主键',
  Setting_name       varchar(100)    default ''                 comment '参数名称',
  Setting_key        varchar(100)    default ''                 comment '参数键名',
  Setting_value      varchar(500)    default ''                 comment '参数键值',
  Setting_type       int(5)         default 0                comment '类型',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (Setting_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into okx_setting values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',       'skin-blue',     0, 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into okx_setting values(2, '用户管理-账号初始密码',         'sys.user.initPassword',    '123456',        0, 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into okx_setting values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',      'theme-dark',    1, 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into okx_setting values(4, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false',         1, 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');


drop table if exists okx_coin;
create table okx_coin (
  coin         varchar(100)          default ''    comment '币种主键',
  lowest       decimal(18,8)    not null default 0                 comment '最低价',
  hightest      decimal(18,8)    not null default 0                 comment '最高价',
  standard      decimal(18,8)    not null default 0            comment '标准值',
  unit          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  vol_ccy24h          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  vol_usdt24h          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  count          decimal(18,8)    not null default 0               comment '数量',
  status         int(5)          not null default 0              comment '状态（Y是 N否）',
  is_rise         int(5)          not null default 0              comment '上涨（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (coin)
) engine=innodb auto_increment=100 comment = '币种';

insert into okx_coin values('test', 1, 2, 1.5, 1, 1.1 ,2, 1, 1, 1, '', sysdate(), '', null, 'remark');



drop table if exists okx_buy_record;
create table okx_buy_record (
  id         int(5)          not null auto_increment    comment '参数主键',
  coin         varchar(100)          default ''    comment '币种主键',
  order_id         varchar(100)          default ''    comment '订单号',
  okx_order_id         varchar(100)          default ''    comment 'okx订单号',
  inst_id         varchar(100)          default ''    comment '币种主键',
  price       decimal(18,8)    not null default 0                 comment '价格',
  quantity      decimal(18,8)    not null default 0                 comment '最高价',
  amount      decimal(18,8)    not null default 0            comment '标准值',
  fee          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  fee_usdt          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  status         int(5)          not null default 0              comment '状态（Y是 N否）',
  strategy_id         int(5)          not null default 0              comment '策略',
  times          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  account_id         int(5)          not null default 0              comment '帐号ID',
  account_name        varchar(64)          not null default ''              comment '帐号名称',
  market_status        int(5)           not null default 0              comment '帐号名称',
  mode_type        varchar(64)          not null default ''              comment '帐号名称',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id),
  INDEX create_time(create_time)
) engine=innodb auto_increment=100 comment = '币种';

insert into okx_buy_record values(1,'test', 'buy1', 'okxbuy1', 'soxxd', 1, 1.1 ,2, 1, 8, 1, 1,1, 1,'张三', 1,'market','admin', sysdate(), '', null, 'remark');




drop table if exists okb_coin_ticker;
create table okb_coin_ticker (
  id         int(5)          not null auto_increment    comment '参数主键',
  coin         varchar(100)          default ''    comment '币种主键',
  inst_id         varchar(100)          default ''    comment '币种主键',
  last       decimal(18,8)    not null default 0                 comment '价格',
  open24h      decimal(18,8)    not null default 0                 comment '最高价',
  low24h      decimal(18,8)    not null default 0            comment '标准值',
  high24h          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  average          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  month_average          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  ins          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  month_ins          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id)
) engine=innodb auto_increment=100 comment = '币种行情';




drop table if exists okx_sell_record;
create table okx_sell_record (
  id         int(5)          not null auto_increment    comment '参数主键',
  buy_record_id         int(5)          not null    comment '参数主键',
  coin         varchar(100)          default ''    comment '币种主键',
  inst_id         varchar(100)          default ''    comment '币种主键',
  price       decimal(18,8)    not null default 0                 comment '价格',
  quantity      decimal(18,8)    not null default 0                 comment '最高价',
  amount      decimal(18,8)    not null default 0            comment '标准值',
  fee          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  status         int(5)          not null    comment '参数主键',
  order_id         varchar(100)          default ''    comment '币种主键',
  okx_order_id         varchar(100)          default ''    comment '币种主键',
  strategy_id          varchar(64)      default null    comment '设置ids',
  buy_strategy_id        int(100)         default null    comment '币种主键',
  times          decimal(18,8)    not null default 0               comment '系统内置（Y是 N否）',
  account_id         int(5)          not null default 0              comment '帐号ID',
  account_name        varchar(64)          not null default ''              comment '帐号名称',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id),
  INDEX create_time(create_time)
) engine=innodb auto_increment=100 comment = '卖出记录';


insert into okx_sell_record values(1,1,'test', 'buy1555',3.3, 3.3, 1, 1.1 ,2, 'sell1', 'okxsell1', 1,1, 1,1,'张三','admin', sysdate(), '', null, 'remark');



drop table if exists okx_account;
create table okx_account (
  id         int(5)          not null auto_increment    comment '参数主键',
  name         varchar(100)          default ''    comment '姓名',
  apikey         varchar(100)          default ''    comment '币种主键',
  secretkey         varchar(100)          default ''    comment '币种主键',
  password         varchar(100)          default ''    comment '币种主键',
  setting_ids         varchar(100)          default ''    comment '策略',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id)
) engine=innodb auto_increment=100 comment = '币种行情';

insert into okx_account values(1,'张三','t1123sfest', 'bsf3uw3y4s1r555', 'sf3fsfw3sf',null,'admin', sysdate(), '', null, 'remark');



drop table if exists okx_account_count;
create table okx_account_count (
  id                 int(5)          not null auto_increment    comment '参数主键',
  account_id         int(5)          default null    comment '帐户',
  coin              varchar(100)          default ''    comment '币种',
  count             decimal(18,8)          default null    comment '数量',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id)
) engine=innodb auto_increment=1 comment = '币种行情';

insert into okx_account_count values(1, 1, 'testcoin', 1.5562, 'admin', sysdate(), '', null, 'remark');



drop table if exists okx_strategy;
create table okx_strategy (
  id                int(5)         not null auto_increment    comment '参数主键',
  account_id        int(5)         not null     comment '参数主键',
  setting_ids       varchar(64)     not null     comment '参数主键',
  strategy_name     varchar(64)     not null     comment '名称',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id),
  UNIQUE KEY `account_strategy_account_id` (`account_id`)

) engine=innodb auto_increment=1 comment = '策略';


insert into okx_strategy values(1, 1, '1,2,3' ,'网格','admin' ,sysdate(), '', null, 'remark');




drop table if exists okx_buy_strategy;
create table okx_buy_strategy (
  id                int(5)         not null auto_increment    comment '参数主键',
  fall_days         int(5)     not null     comment '天数',
  fall_percent     decimal(8,8)       not null     comment '百分比',
  times         int(5)     not null     comment '倍数',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id)
) engine=innodb auto_increment=1 comment = '策略';


insert into okx_buy_strategy values(1,  1 , 0.1, 2, 'admin' ,sysdate(), '', null, 'remark');



drop table if exists okx_account_balance;
create table okx_account_balance (
  id         int(5)          not null auto_increment    comment '参数主键',
  account_name         varchar(100)          default ''    comment '姓名',
  account_id         int(5)     not null       comment '帐号主键',
  coin         varchar(100)          default ''    comment '币种',
  balance         decimal(18,8)          default 0    comment '币种主键',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id)
) engine=innodb auto_increment=100 comment = '币种余额';



drop table if exists okx_coin_profit;
create table okx_coin_profit (
  id         int(5)          not null auto_increment    comment '参数主键',
  account_id         int(5)     not null       comment '帐号主键',
  coin         varchar(100)          default ''    comment '币种',
  profit         decimal(18,8)          default 0    comment '利润',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (id),
    UNIQUE KEY `profit_coin_account_id` (`account_id`,`coin`)
) engine=innodb auto_increment=1 comment = '利润';