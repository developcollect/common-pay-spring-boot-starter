# common-pay-spring-boot-starter

### 简介

common-pay-spring-boot-starter是一个common-pay的spring boot自动配置器，为common-pay提供了各项必须组件的默认配置，已达到只需在spring配置文件中配置支付平台参数*(如appId, key等等)*即可使用的程度。并各项配置都支持自定义组件覆盖，使用案例见[common-pay-sample](https://github.com/developcollect/common-pay-sample)。



### 安装

##### Maven

```xml
<dependency>
    <groupId>com.developcollect</groupId>
    <artifactId>common-pay-spring-boot-starter</artifactId>
    <version>1.8.1</version>
</dependency>
```

##### Gradle

```groovy
implementation 'com.developcollect:common-pay-spring-boot-starter:1.8.1'
```

##### 非Maven项目

点击以下任一链接，下载`common-pay-spring-boot-starter-X.X.X.jar`即可：

- [Maven中央仓库1](https://repo1.maven.org/maven2/com/developcollect/common-pay-spring-boot-starter/1.8.1/)
- [Maven中央仓库2](https://repo2.maven.org/maven2/com/developcollect/common-pay-spring-boot-starter/1.8.1/)

> 注意：common-pay-spring-boot-starter仅支持JDK8+



### 目录结构描述

```tex
autoconfig
  │  CommonPayAutoConfig.java                        // 自动配置器
  │  CommonPayConfigurer.java                        // common-pay配置器(重点)
  │  CommonPayProperties.java                        // 配置文件属性映射对象
  │  CommonPaySerializeUtil.java                     // 序列化工具
  │  CommonPaySpringUtil.java                        // spring 工具
  │  CommonPayWebMvcConfig.java                      // 静态资源映射配置
  │  PayEvent.java                                   // 支付事件对象
  │  RefundEvent.java                                // 退款事件对象
  │  
  └─controller                                       
          BaseController.java
          CommonPayAliPayController.java             // 支付宝异步通知地址Controller
          CommonPayWxPayController.java              // 微信异步通知地址Controller
```



### 使用

##### 属性配置

配置文件中的属性前缀统一为**develop-collect.pay**，下面的配置项解释默认不带该前缀，具体格式可参考下面的完整配置文件样例

* **domain**：外网域名，用于拼接结果异步通知地址，资源访问地址。该属性无默认值，必须配置
* **port**：外网访问端口，默认为${server.port}，也就是spring web的端口
* **context-path**：上下文路径，默认为${server.servlet.context-path}
* **ssl**：是否启用了https，默认值：false，用于决定拼接的地址的协议是http还是https
* **query-notice-delay**：主动查询间隔时间，单位：ms，默认值为600000，因为支付结果除了异步通知也可主动查询，这个值就是设置的每主动查一次的间隔时间
* **notify-endpoint.enabled**：是否开启异步通知端点，默认值：true。因为异步推送的话就需要写接口，当前项目提供了默认的接口来接收异步通知结果，这个值就是用来决定是否开启默认的接口
* **resource-endpoint.enabled**：是否启用静态资源映射，默认值：true。因为生成二维码访问地址，页面访问地址时需要通过链接地址访问资源，默认策略是将资源存在本地*(位于项目地址下的cPay文件夹下)*，然后通过静态资源映射实现资源访问
* **wxpay.use-sandbox**：微信支付是否使用沙箱环境，默认值：false
* **wxpay.appid**：微信支付AppId
* **wxpay.key**：微信支付KEY
* **wxpay.mch-id**：微信支付商户id
* **wxpay.cert-location**：微信支付证书路径，支持文件路径，如`F:/test/a.cert`；支持类路经，但需要以classpath:开头，如`classpath:apiclient_cert.p12`；支持url地址，如`http://www.baidu.com/test.p12`、`ftp://10.3.3.32/2020816/test.p12`等等
* **wxpay.qr-code-width**：二维码宽度， 默认值：300
* **wxpay.qr-code-height**：二维码高度，默认值：300
* **alipay.use-sandbox**：支付宝支付是否使用沙箱环境
* **alipay.appid**：支付宝AppId
* **alipay.private-key**：支付宝私钥
* **alipay.public-key**：支付宝公钥
* **alipay.charset**：接口调用时的编码参数，支持UTF-8/GBK，默认值：UTF-8
* **alipay.sign-type**：密钥算法，支持RSA/RSA2，默认值：RSA2
* **alipay.qr-code-width**：二维码宽度， 默认值：300
* **alipay.qr-code-height**：二维码高度，默认值：300

配置文件样例：
```yml
develop-collect:
  pay:
    domain: 17o04f3737.iask.in
    port: 80
    context-path: /dc
    query-notice-delay: 600000
    ssl: false
    notify-endpoint:
      enabled: true
    resource-endpoint:
      enabled: true
    wxpay:
      use-sandbox: true
      appid: wxappid
      key: wxkey
      mch-id: wxmchid
      qr-code-width: 500
      qr-code-height: 500
      cert-location: classpath:apiclient_cert.p12

    alipay:
      use-sandbox: true
      appid: 2016090900472312
      private-key: xxxxx
      public-key: xxxxx  
      charset: UTF-8
      sign-type: RSA2
      qr-code-width: 200
      qr-code-height: 200
```

##### 组件配置

具体有哪些组件可参考`CommonPayAutoConfig`，需要注意的是在`CommonPayAutoConfig`中注入组件时有指定bean名称， 所以==在自定义组件时需要确保自定义的bean的名称和`CommonPayAutoConfig`里需要的bean的名称相匹配==，只有这样才能实现自动注入。具体的自定义配置可查看[common-pay-sample](https://github.com/developcollect/common-pay-sample)， 里面有例子。

##### 接口权限

自动生成的接口地址都是以/cPay开头的，在项目中要确保/cPay/**接口不需要任何权限即可访问，否则会导致页面、二维码无法访问，结果异步通知无法接收的情况。



### 提供BUG反馈或建议

提交问题反馈请说明正在使用的JDK版本、common-pay版本和相关依赖库版本。

- [Github issue](https://github.com/developcollect/common-pay-sample/issues)



### 添砖加瓦

emmm... 分支啥的都还没弄好，等弄好了我再来补~ 咕咕咕~~



### 写在最后

总算写完了文档。。。