以下三种配置是一样的
【_等同于:】

```json
{
  "format": "DIC",
  "NACOS": {
    "NORMAL": {
      "TITLE": {
        "zh_CN": "亲爱的 ${name} 您有新的到账提醒",
        "en": "Deal ${name} you have new message"
      },
      "BODY": {
        "en": "balance: ${balance} recive ${amount+fee}",
        "zh_CN": "余额：${balance} 收到 ${amount-fee}"
      }
    }
  }
}
```

```json
{
  "format": "DIC",
  "NACOS_NORMAL_TITLE": {
    "zh_CN": "亲爱的 ${name} 您有新的到账提醒",
    "en": "Deal ${name} you have new message"
  },
  "NACOS_NORMAL_BODY": {
    "en": "balance: ${balance} recive ${amount+fee}",
    "zh_CN": "余额：${balance} 收到 ${amount-fee}"
  }
}
```

```json
{
  "format": "DIC",
  "NACOS_NORMAL_TITLE_zh_CN": "亲爱的 ${name} 您有新的到账提醒",
  "NACOS_NORMAL_TITLE_zh_CN_en": "Deal ${name} you have new message",
  "NACOS_NORMAL_BODY_en": "balance: ${balance} recive ${amount+fee}",
  "NACOS_NORMAL_BODY_zh_CN": "余额：${balance} 收到 ${amount-fee}"
}
```